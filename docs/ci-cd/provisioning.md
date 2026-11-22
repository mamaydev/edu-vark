# Provisioning: LXC 10100 (EduVARK homelab)

Everything below was executed on 2026-11-22 from the Proxmox host `mamay`
(`root@100.65.29.10` over Tailscale) using `pct exec 10100`. The MacBook cannot reach
`192.168.0.100` directly: the Wi-Fi AP isolates wireless clients, so all server work goes
through the Proxmox host.

## 1. Actual container specification (verified, not assumed)

| Item | Brief / expectation | Actual | Verdict |
| --- | --- | --- | --- |
| Container | fresh LXC `10100` | `10100`, hostname `pam`, unprivileged | matches |
| OS | Ubuntu | Ubuntu 24.04.5 LTS | matches |
| vCPU | 1 core | 2 cores (`cores: 2`, `cpulimit: 2`) | above minimum |
| RAM | 2 GB | 8192 MB | above minimum |
| Swap | 512 MB | 512 MB | matches |
| Disk | 8 GB | 20 GB rootfs on `nvme-storage` (~18 GB free) | above minimum |
| Features | nesting for Docker | `nesting=1,keyctl=1` | matches |
| Network | `vmbr0` | `192.168.0.100/24`, gw `192.168.0.1`, `firewall=1` | matches |
| Autostart | — | `onboot: 1` | good |

Capacity check before installing anything: ~18 GB free disk, 8 GB RAM, 2 cores. Enough for
Docker images, PostgreSQL data and a deployment backup. No resource blocker was found, and
no container resize was needed.

```bash
pct config 10100
pct exec 10100 -- df -h /
pct exec 10100 -- free -m
```

## 2. Pre-flight checks (read-only)

```bash
# host
pveversion
pct list
ls -la /var/lib/vz/dump          # existing container backups, if any

# container
pct exec 10100 -- bash -lc 'nproc; free -m; df -h /; ip -4 addr; ip route'
pct exec 10100 -- bash -lc 'command -v docker || echo "no docker"'
pct exec 10100 -- bash -lc 'ss -lntp'
```

Result at the time of provisioning: no Docker, no PostgreSQL, no backend, no runner and no
volumes existed in `10100`. Nothing from an older installation could be reused or damaged.

**Backup decision.** No backup of a previous EduVARK database existed anywhere (no dumps in
`/var/lib/vz/dump` for `10100`, no PBS, no NFS/CIFS mount, nothing on the workstation). The
database was therefore initialised fresh; no restore was possible or required. Documented
here because "check for backups before creating the database" was part of the brief.

## 3. Base OS preparation

```bash
pct exec 10100 -- bash -lc '
  export DEBIAN_FRONTEND=noninteractive
  apt-get update
  apt-get install -y ca-certificates curl gnupg git jq openssl rsync unzip ufw
  timedatectl set-timezone Asia/Jakarta
'
```

Timezone on the container is `Asia/Jakarta`; the container clock is what the backup timer
and log timestamps use. (Deployment records use UTC.)

## 4. Docker Engine from the official repository

```bash
pct exec 10100 -- bash -lc '
  install -m 0755 -d /etc/apt/keyrings
  curl -fsSL https://download.docker.com/linux/ubuntu/gpg |
    gpg --dearmor -o /etc/apt/keyrings/docker.gpg
  chmod a+r /etc/apt/keyrings/docker.gpg
  echo "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.gpg] \
https://download.docker.com/linux/ubuntu noble stable" > /etc/apt/sources.list.d/docker.list
  apt-get update
  apt-get install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin
'
```

`/etc/docker/daemon.json` (validated with `dockerd --validate`):

```json
{
  "log-driver": "json-file",
  "log-opts": { "max-size": "10m", "max-file": "3" },
  "live-restore": true
}
```

Verification:

```bash
docker version
docker compose version
docker info --format '{{.Driver}} {{.CgroupVersion}} {{.LiveRestoreEnabled}}'
ss -lntp | grep 2375 || echo "no insecure TCP listener"
```

## 5. Deployment directory and configuration

```bash
install -d -m 0755 /opt/eduvark
git clone https://github.com/mamaydev/edu-vark.git /opt/eduvark/try-pam
chmod 0700 /opt/eduvark/try-pam
```

Two secret files are created by hand, both `root:root 0600`, never committed:

`/opt/eduvark/try-pam/.env` (used by Compose for the database and image tag):

```dotenv
POSTGRES_PASSWORD=<openssl rand -hex 32>
BACKEND_TAG=<git sha, 12 chars>
BACKEND_BIND_ADDRESS=192.168.0.100
```

`/opt/eduvark/try-pam/backend/.env.production` (application configuration):

```dotenv
NODE_ENV=production
PORT=3000
DATABASE_URL=postgresql://eduvark:<same password>@postgres:5432/eduvark
JWT_ACCESS_SECRET=<openssl rand -hex 32>
JWT_REFRESH_SECRET=<different openssl rand -hex 32>
CORS_ORIGINS=
LOG_LEVEL=info
R2_ACCOUNT_ID=
R2_ACCESS_KEY_ID=
R2_SECRET_ACCESS_KEY=
R2_BUCKET=
```

`env.ts` validates: production needs both JWT secrets (≥32 chars, different from each
other), and R2 must be either completely empty or completely filled. Empty R2 means the
API boots but file upload endpoints fail; fill the four values and restart the backend to
enable uploads.

## 6. First start and initial data

```bash
cd /opt/eduvark/try-pam
docker compose -f docker-compose.homelab.yml up -d postgres
docker compose -f docker-compose.homelab.yml ps

# migrations (creates schema_migrations, 001_init.sql, ...)
docker compose -f docker-compose.homelab.yml run --rm --no-deps backend node dist/db/migrate.js

# initial admin (values come from the local backend/.env, not from this repo)
docker compose -f docker-compose.homelab.yml run --rm --no-deps \
  -e ADMIN_LOGIN_ID -e ADMIN_PASSWORD -e ADMIN_FULL_NAME \
  backend node dist/db/seed-admin.js
```

The generated admin is flagged `must_change_password=true`.

```bash
docker compose -f docker-compose.homelab.yml up -d backend
curl -fsS http://192.168.0.100:3000/health/ready
```

## 7. Runner, deploy engine, backups

Installed afterwards; see [`runner.md`](runner.md) and [`operations.md`](operations.md).

## 8. Rebuild from scratch (disaster recovery)

1. Create LXC `10100` as in section 1 (Ubuntu 24.04, `nesting=1,keyctl=1`, 2 vCPU,
   4–8 GB RAM, ≥20 GB disk, `vmbr0`).
2. Sections 3–5 (Docker, repo, secret files — restore the secrets from your password
   manager; they are not recoverable from the repository).
3. `docker compose ... up -d postgres`
4. Restore the newest dump:
   `docker exec -i eduvark-postgres pg_restore -U eduvark -d eduvark --clean --if-exists < /path/to/dump`
   (or without `--clean` on an empty database, which is the safe default).
5. `docker compose ... up -d backend`, health-check.
6. Section 7: runner + engine + timer.
7. Re-register the runner in GitHub (Settings → Actions → Runners).

Total measured rebuild time on this hardware: ~15 minutes excluding image downloads.