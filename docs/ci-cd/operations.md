# Operations runbook

All commands are executed from a machine that can reach the Proxmox host
(`ssh root@100.65.29.10`, Tailscale) because the MacBook's Wi-Fi AP isolates clients and
cannot reach `192.168.0.100` directly.

```bash
pct exec 10100 -- <command>          # run inside LXC 10100
pct exec 10080 -- <command>          # ingress LXC (Traefik/cloudflared)
```

## Daily (2 minutes)

```bash
# containers healthy?
pct exec 10100 -- docker ps --format 'table {{.Names}}\t{{.Image}}\t{{.Status}}'

# public endpoint
curl -fsS https://pam.sequein.my.id/health/ready && echo

# last deployment
pct exec 10100 -- jq -r '"\(.deployed_sha) \(.result) \(.finished_at) backup=\(.backup)"' /var/lib/eduvark/state.json

# backup timer
pct exec 10100 -- systemctl list-timers eduvark-backup.timer --no-pager
pct exec 10100 -- ls -lt /opt/eduvark/backups | head -4
```

## Weekly

```bash
# disk, memory, restart count
pct exec 10100 -- df -h /
pct exec 10100 -- free -m
pct exec 10100 -- docker inspect -f '{{.Name}} restarts={{.RestartCount}}' eduvark-backend eduvark-postgres

# backend errors in the last week
pct exec 10100 -- docker logs --since 168h eduvark-backend 2>&1 | grep -iE 'error|fatal' | tail -30

# runner still online
pct exec 10100 -- systemctl is-active github-runner-eduvark.service
curl -s -H "Authorization: Bearer $GITHUB_PAT" \
  https://api.github.com/repos/mamaydev/edu-vark/actions/runners |
  jq '.runners[] | {name, status}'
```

## Component operations

### Restart the API

```bash
pct exec 10100 -- bash -lc 'cd /opt/eduvark/try-pam && docker compose -f docker-compose.homelab.yml restart backend'
```

### Recreate the API after an env change

```bash
pct exec 10100 -- bash -lc 'cd /opt/eduvark/try-pam && docker compose -f docker-compose.homelab.yml up -d --no-deps --force-recreate backend'
```

### Logs

```bash
pct exec 10100 -- docker logs -f --tail 200 eduvark-backend
pct exec 10100 -- docker logs -f --tail 100 eduvark-postgres
pct exec 10100 -- tail -n 100 /var/lib/eduvark/deploy.log
pct exec 10100 -- journalctl -u github-runner-eduvark.service -n 100 --no-pager
```

### PostgreSQL

```bash
# open a psql shell
pct exec 10100 -- docker exec -it eduvark-postgres psql -U eduvark -d eduvark

# applied migrations
pct exec 10100 -- docker exec eduvark-postgres psql -U eduvark -d eduvark \
  -c 'select * from schema_migrations order by id'

# database size
pct exec 10100 -- docker exec eduvark-postgres psql -U eduvark -d eduvark \
  -c "select pg_size_pretty(pg_database_size('eduvark'))"
```

### Runner

```bash
pct exec 10100 -- systemctl restart github-runner-eduvark.service
pct exec 10100 -- systemctl status github-runner-eduvark.service --no-pager
pct exec 10100 -- journalctl -u github-runner-eduvark.service -f
```

### Deploy engine (manual)

```bash
pct exec 10100 -- sudo /usr/local/sbin/eduvark-deploy status
pct exec 10100 -- sudo /usr/local/sbin/eduvark-deploy verify <sha>
pct exec 10100 -- sudo /usr/local/sbin/eduvark-deploy dry-run <sha>
pct exec 10100 -- sudo /usr/local/sbin/eduvark-deploy deploy  <sha>
pct exec 10100 -- sudo /usr/local/sbin/eduvark-deploy rollback [sha]
```

## Backup verification (monthly)

```bash
# 1. list backups, pick the newest
pct exec 10100 -- ls -lht /opt/eduvark/backups | head -5

# 2. prove the dump is readable
pct exec 10100 -- bash -lc 'docker exec -i eduvark-postgres pg_restore -l < /opt/eduvark/backups/<newest>.dump | head'

# 3. restore into a scratch database (does NOT touch the live one)
pct exec 10100 -- docker exec eduvark-postgres createdb -U eduvark eduvark_restore_test
pct exec 10100 -- bash -lc 'docker exec -i eduvark-postgres pg_restore -U eduvark -d eduvark_restore_test --no-owner --no-privileges < /opt/eduvark/backups/<newest>.dump'
pct exec 10100 -- docker exec eduvark-postgres psql -U eduvark -d eduvark_restore_test -c 'select count(*) from users'
pct exec 10100 -- docker exec eduvark-postgres dropdb -U eduvark eduvark_restore_test
```

`dropdb` on a scratch database is fine; never `DROP DATABASE eduvark`.

## Disk cleanup

```bash
# what is using space
pct exec 10100 -- docker system df
pct exec 10100 -- du -sh /opt/eduvark/backups /var/lib/docker

# safe cleanups (no volumes are ever pruned)
pct exec 10100 -- docker image prune -a --filter 'until=720h'   # old tags, keep recent
pct exec 10100 -- docker builder prune --filter 'until=168h'
```

Never run `docker volume prune`, `docker system prune --volumes`, or
`docker compose down -v`.

## Troubleshooting matrix

| Symptom | Likely cause | Fix |
| --- | --- | --- |
| `502`/`530` from `pam.sequein.my.id` | backend down or ingress/tunnel issue | check `docker ps`; `curl http://192.168.0.100:3000/health/ready` from `pct exec 10080`; if that works, the problem is Traefik/cloudflared (out of scope, ask owner) |
| `/health/ready` returns 503 | database unreachable | `docker ps`; `docker inspect eduvark-postgres --format '{{.State.Health.Status}}'`; check disk |
| Backend container restarting | config validation failure (`env.ts`) | `docker logs eduvark-backend` — e.g. missing JWT secret, half-filled R2 block |
| `401` on every authenticated call | JWT secrets rotated | expected: users must log in again |
| Uploads fail with 500 | R2 not configured | fill `R2_*` in `backend/.env.production`, recreate backend |
| Deploy job queued forever | runner offline | `systemctl status github-runner-eduvark`; runner labels must be `eduvark-deploy` |
| Deploy fails "not an ancestor of origin/main" | SHA from a PR/unmerged branch | merge first or use a commit on `main` |
| Deploy fails "insufficient disk" | images/dumps | cleanup section above, then retry |
| Backup timer missed | LXC was off at 03:30 | `Persistent=true` runs it at next boot; check `systemctl list-timers` |
| `sudo: a password is required` in job | sudoers file missing | `visudo -cf /etc/sudoers.d/eduvark-runner` |

## Maintenance log

| Date | Action | Result |
| --- | --- | --- |
| 2026-11-22 | LXC 10100 provisioned, Docker 29.10.0, PostgreSQL 17, backend image, admin seed, runner `eduvark-lxc-10100`, deploy engine, backup timer | healthy, public API reachable |
| 2026-11-22 | CI fixed (Android SDK installation), CD added (`deploy-homelab.yml`) | see GitHub Actions |

## Open actions

1. Fill `R2_*` credentials and restart the backend (file uploads).
2. Configure offsite backup copy (restic/rclone → R2, or `vzdump` to another node).
3. Add uptime monitoring for `https://pam.sequein.my.id/health/ready` + backup-failure
   alerting.
4. Harden SSH in LXC `10100` (key-only, no root password login) — currently `pct exec`
   is the only access path used.
5. Consider a second GitHub account as environment reviewer to remove the
   approve-your-own-deployment weakness.
6. Schedule the quarterly rollback drill from [`rollback.md`](rollback.md).
7. Decide whether automatic deployment on merge should ever be enabled (currently
   intentionally off).