# Deployment procedure

## Prerequisites

- The commit is an ancestor of `main` and its CI run is green.
- You are `@mamaydev` (owner) so you can approve the `homelab-testing` environment.
- The runner `eduvark-lxc-10100` is online with label `eduvark-deploy`.
- Disk free ≥ 3 GB on LXC `10100` (the engine refuses to continue below that).

Spot-check the host:

```bash
pct exec 10100 -- systemctl is-active github-runner-eduvark.service docker
pct exec 10100 -- df -h /
pct exec 10100 -- docker ps --format 'table {{.Names}}\t{{.Image}}\t{{.Status}}'
```

## Step 1 — verify (read-only)

GitHub → Actions → **Deploy homelab** → Run workflow → SHA `<commit>` → mode `verify`.

Equivalent on the host (no GitHub involved):

```bash
sudo /usr/local/sbin/eduvark-deploy verify <sha>
```

Confirmed by the run: SHA exists locally, is an ancestor of `origin/main`, compose config
is valid, env files are `0600`, PostgreSQL is healthy, disk and memory are sufficient.

## Step 2 — dry run (backup + image build)

Run workflow again with mode `dry-run` (or `sudo /usr/local/sbin/eduvark-deploy dry-run <sha>`).

It creates a real timestamped `pg_dump`, validates it with `pg_restore -l`, and builds
`eduvark-backend:<sha12>`. The running container is not touched. This proves the image
builds on the host and that the backup path works before a release.

## Step 3 — deploy

Run workflow with mode `deploy`, then approve the `homelab-testing` environment.

The engine executes, in order:

| # | Step | Detail |
| --- | --- | --- |
| 1 | lock | `flock` on `/var/lib/eduvark/deploy.lock`; refuses concurrent releases |
| 2 | preflight | env files, compose config, postgres health, disk ≥3 GB, RAM ≥384 MB |
| 3 | fetch | `git fetch origin main`, clean checkout of the deployment tree |
| 4 | resolve | full SHA, must be an ancestor of `origin/main` |
| 5 | backup | `pg_dump -Fc` → `/opt/eduvark/backups/eduvark-<ts>-<sha12>.dump`, verified, never overwritten |
| 6 | checkout | `git checkout --detach <sha>` (`.env` files are untracked and preserved) |
| 7 | build | `docker compose build backend` tagged `eduvark-backend:<sha12>` |
| 8 | migrate | `node dist/db/migrate.js` under `pg_advisory_lock`; delta of `schema_migrations` logged |
| 9 | switch | `docker compose up -d --no-deps --wait backend` (waits for the container healthcheck) |
| 10 | health | polls `http://192.168.0.100:3000/health/ready` for up to 80 s |
| 11 | public | polls `https://pam.sequein.my.id/health/ready` (warning only) |
| 12 | smoke | `/api/v1/health` → 200, `/api/v1/topics` without token → 401 |
| 13 | record | `/var/lib/eduvark/state.json` + timestamped history copy |
| 14 | cleanup | keeps the 14 newest dumps |

If steps 9–12 fail, the engine restarts the **previous image tag** and re-checks health
before exiting non-zero. The database is never restored automatically.

### What "success" looks like

```
[eduvark-deploy] deploy start: sha=<sha> image=<sha12> previous_image=<old>
[eduvark-deploy] creating database backup: /opt/eduvark/backups/eduvark-...dump
[eduvark-deploy] backup ok: ... (NNNN bytes)
[eduvark-deploy] migrations: before=[001_init.sql] after=[001_init.sql]
[eduvark-deploy] switching backend to eduvark-backend:<sha12>
[eduvark-deploy] host health check passed (attempt 1)
[eduvark-deploy] public health check passed (attempt 1)
[eduvark-deploy] smoke tests passed
[eduvark-deploy] deploy success: sha=<sha> image=<sha12>
```

## Post-deployment verification checklist

Run these from anywhere with LAN/Tailscale access to the Proxmox host (the MacBook cannot
reach `192.168.0.100` directly):

```bash
# 1. container state and image tag
pct exec 10100 -- docker ps --format 'table {{.Names}}\t{{.Image}}\t{{.Status}}'
pct exec 10100 -- docker inspect eduvark-backend --format '{{.Config.Image}} {{.State.Health.Status}}'

# 2. state file
pct exec 10100 -- cat /var/lib/eduvark/state.json | jq .

# 3. LAN health
pct exec 10100 -- curl -fsS http://192.168.0.100:3000/health/ready

# 4. through the ingress LXC
pct exec 10080 -- curl -fsS http://192.168.0.100:3000/health/ready

# 5. public endpoint
curl -fsS https://pam.sequein.my.id/health/ready
curl -s -o /dev/null -w '%{http_code}\n' https://pam.sequein.my.id/api/v1/topics   # 401

# 6. logs contain no startup errors
pct exec 10100 -- docker logs --tail 50 eduvark-backend
```

Expected: both containers `Up ... (healthy)`, image tag equals the released SHA prefix,
public health returns `{"data":{"status":"ready",...}}`, unauthenticated topics returns
`401`, no `FATAL`/`unhandled` lines in the backend log.

Then smoke-test the Android app against `https://pam.sequein.my.id`: log in with the admin
account (it forces a password change), open one class/topic, confirm a 200 in the backend log.

## Manual deployment (fallback, no runner)

If the runner is down but a release is urgent:

```bash
cd /opt/eduvark/try-pam
sudo /usr/local/sbin/eduvark-deploy deploy <sha>
```

The engine behaves exactly as in the workflow (same backup, migration, health, rollback).
Record in the release notes that it was deployed manually and why.

## Adding new configuration

1. Add the variable to `backend/src/config/env.ts` with validation.
2. Document it in `docs/ci-cd/provisioning.md` and in `backend/.env.example` if present.
3. Put the real value in `/opt/eduvark/try-pam/backend/.env.production` (root, `0600`).
4. Restart: `docker compose -f docker-compose.homelab.yml up -d --no-deps backend`.
5. Never commit real secrets; never echo them in workflow logs.

## Rollback

See [`rollback.md`](rollback.md).