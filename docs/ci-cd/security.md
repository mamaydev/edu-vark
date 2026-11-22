# Security model and residual risks

## Threat model

| Threat | Impact | Mitigation |
| --- | --- | --- |
| Malicious pull request runs on the self-hosted runner | Container compromise, secret theft | CI runs only on GitHub-hosted runners; the self-hosted runner is reserved for `deploy-homelab.yml`, which is `workflow_dispatch`-only and never checks out PR code. Fork PR workflows require approval (GitHub default). |
| Workflow file changed by an attacker | Arbitrary root commands on the LXC | `main-protection` ruleset requires the three CI checks; `CODEOWNERS` flags `.github/`; releases need environment approval by the owner; the runner can only call `eduvark-deploy`, which re-validates the SHA and that it is on `main`. |
| Compromised commit on `main` | Deployed code executes as container root, can read DB | Inherent to CD. Compensated by required CI + manual approval + deployment records + backups. Admins can bypass the ruleset (single-maintainer trade-off). |
| Runner user escalates to root | Full container control, DB and secrets | Runner is not in `docker` group and cannot read the `0600` env files. The only privileged command is the sudoers-pinned `eduvark-deploy`. A sudoers bug or a vulnerable deploy script is the remaining path. |
| Docker socket exposure | Root equivalent | No TCP listener on 2375/2376; socket is `0660 root:docker`; only root uses it. |
| Internet exposure of the API | Abuse, credential stuffing | Rate limiting on `/api/v1`, helmet, explicit CORS allow-list, JWT auth, 401 on protected routes. Traefik/cloudflared are the only ingress. |
| Database exposure | Data breach | PostgreSQL has no published port; reachable only on the internal Docker network. |
| Secret leakage via git or logs | Credential compromise | Env files are untracked `0600 root`; workflows carry no secrets; deploy engine never prints env values; GitHub token is read-only in CD. |
| Disk exhaustion (images, logs, dumps) | Outage | Docker log rotation `10m/3`, 14-dump retention, preflight disk gate (3 GB), manual image pruning documented. |

## Controls in place

- **Least-privilege runner**: `eduvark-runner` system user, no sudo shell, no docker group,
  repo-scoped registration, label `eduvark-deploy` only.
- **Single privileged entry point**: `/usr/local/sbin/eduvark-deploy` (root, `0750`),
  sudoers rule limited to `verify|dry-run|deploy|rollback|status`.
- **Deploy-time trust checks**: SHA format, `origin/main` ancestry, clean checkout, env file
  permissions, compose validation, postgres health, capacity.
- **Protected deployment environment**: `homelab-testing` requires reviewer `@mamaydev` and
  only accepts deployments from `main`.
- **Branch ruleset**: no force push, no deletion, three required CI checks.
- **Read-only `GITHUB_TOKEN`** in both workflows; default repo permissions are `read`.
- **Secrets**: root-only files; no secrets in the repository, in workflow YAML, or in logs.
- **Container hygiene**: backend runs as the non-root `node` user, read-only-ish image
  (multi-stage build, `npm prune --omit=dev`), database not published, Docker daemon local
  only.
- **Recoverability**: verified backup before every deployment, automatic image rollback,
  deployment history.

## Secret inventory

| Secret | Where | Owner/mode | Rotation |
| --- | --- | --- | --- |
| `POSTGRES_PASSWORD` | `/opt/eduvark/try-pam/.env` and inside `DATABASE_URL` in `backend/.env.production` | `root:root 0600` | `openssl rand -hex 32`, `ALTER USER eduvark PASSWORD`, update both files, `docker compose up -d` |
| `JWT_ACCESS_SECRET`, `JWT_REFRESH_SECRET` | `backend/.env.production` | `root:root 0600` | rotate both (≥32 chars, different), restart backend; all sessions invalidated |
| Admin password | PostgreSQL (`users.password_hash`, argon2id) | database | change on first login (`must_change_password=true`) |
| GitHub PAT (runner registration) | maintainer's password manager only | not on the server | short-lived; revoke after use, prefer a fine-grained token with `Administration: write` on this repo only |
| Runner credentials | `/opt/eduvark/runner/.credentials` | `eduvark-runner 0600` | re-register with `--replace` |
| Cloudflare R2 keys | `backend/.env.production` (currently empty) | `root:root 0600` | rotate in Cloudflare dashboard, update file, restart |

## Residual risks (accepted, documented)

1. **Co-location** of runner, backend and database in one LXC: a runner compromise is one
   step away from application data. Accepted because the brief requires it; mitigations
   above reduce the blast radius (no docker group, no secret reads).
2. **Deployment executes repository code as root inside the container.** Any approved
   release can run arbitrary code with container root. This is inherent to deploying
   software; the unprivileged LXC keeps container root separate from host root.
3. **Admin bypass on the `main-protection` ruleset.** Chosen so the single maintainer can
   push directly; a compromised maintainer account can bypass review. Compensated by
   environment approval, which the same account must give — so a compromised account can
   deploy. Enable 2FA on GitHub; consider a second reviewer account for real separation.
4. **Backups are on the same disk as the database.** No offsite copy yet. Recommended:
   `restic`/`rclone` to Cloudflare R2, or `pct vzdump` to a second node, plus a monthly
   restore test. Tracked in [`operations.md`](operations.md#open-actions).
5. **No monitoring/alerting.** Failures are discovered by users or by looking at logs.
   Recommended: Uptime Kuma (or healthchecks.io) on `/health/ready`, and an alert when the
   nightly backup timer fails.
6. **R2 not configured**, so file uploads return errors. Fill the four `R2_*` variables and
   restart to enable uploads.
7. **SSH inside LXC 10100** still accepts password authentication for root by default. All
   administrative access currently goes through `pct exec` on the Proxmox host, so this is
   not needed. Recommended hardening: install an SSH key, then
   `PasswordAuthentication no` + `PermitRootLogin prohibit-password` in
   `/etc/ssh/sshd_config.d/99-hardening.conf`, and `systemctl reload ssh`.
8. **No host firewall** inside the LXC (`ufw` installed but inactive; Proxmox firewall flag
   set on the NIC). The only published port is `3000` on the LAN IP. Leaving it as-is avoids
   interfering with Docker's iptables rules; add explicit `DOCKER-USER` rules if stricter
   isolation is wanted.
9. **Image accumulation**: every release leaves an `eduvark-backend:<sha>` image (~380 MB).
   After ~20 releases, prune old tags manually:
   `docker image prune -a --filter "until=720h"` (review first with `docker image ls`).
10. **Single PostgreSQL instance**, no replicas. Restore time is the downtime.

## Security checklist (run after any infra change)

```bash
# runner has no docker access
runuser -u eduvark-runner -- docker ps 2>&1 | grep -qi 'permission denied' && echo OK

# runner cannot read secrets
runuser -u eduvark-runner -- cat /opt/eduvark/try-pam/backend/.env.production 2>&1 | grep -qi 'permission denied' && echo OK

# no Docker TCP listener
ss -lntp | grep -E '2375|2376' || echo "OK: no TCP docker socket"

# postgres not published
docker port eduvark-postgres || echo "OK: no published ports"

# env files
stat -c '%a %U:%G %n' /opt/eduvark/try-pam/.env /opt/eduvark/try-pam/backend/.env.production

# public surface
curl -s -o /dev/null -w 'ready:%{http_code}\n' https://pam.sequein.my.id/health/ready
curl -s -o /dev/null -w 'topics-noauth:%{http_code}\n' https://pam.sequein.my.id/api/v1/topics
```

## Incident response (suspected runner or secret compromise)

1. `systemctl stop github-runner-eduvark.service` — stops all job execution immediately.
2. Remove the runner in GitHub Settings → Actions → Runners.
3. Revoke the PAT used for registration; rotate `POSTGRES_PASSWORD`, both JWT secrets and R2
   keys; restart the stack.
4. Inspect `/var/lib/eduvark/deploy.log`, `state.json`, `history-*.json`,
   `docker ps -a`, `docker logs eduvark-backend`, and `journalctl -u github-runner-eduvark`.
5. If the database was touched, restore the newest verified dump
   ([`rollback.md`](rollback.md)).
6. Re-register the runner only after the cause is understood; keep CD disabled
   (`workflow_dispatch` without approval) until then.