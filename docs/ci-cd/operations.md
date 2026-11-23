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

### Runner guard (automatic)

`eduvark-runner-guard.timer` runs every 5 minutes and restarts the runner **only**
when the listener is genuinely wedged: a `connect error` older than 180s that was
never followed by `Listening for Jobs`. It is skipped while a job is executing and
while the deploy engine holds its flock, and it is budgeted to 3 restarts per hour.

```bash
# what would it do right now?
pct exec 10100 -- /usr/local/sbin/eduvark-runner-guard check

# replay a historical window (used to validate the detector)
pct exec 10100 -- /usr/local/sbin/eduvark-runner-guard check \
  --since "2026-11-23 03:20:00" --until "2026-11-23 04:05:00"

# what has it done?
pct exec 10100 -- cat /var/lib/eduvark/runner-guard.log
```

GitHub can report a wedged runner as `online` (`GET /actions/runners`), so never
use the API alone to judge runner health — that is exactly why the guard reads the
runner's own journal.

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
| Deploy job queued forever | runner offline at the moment CI finished | see the recipe below; GitHub does not always reassign a job to a reconnected runner |
| Runner logs `A session for this runner already exists` / `Conflict` | previous listener was killed mid-session | wait 2-3 min for the lease to expire; the guard does not restart during this window |
| Materials fail with `NoSuchKey` | object stored under a different key than `materials.object_key` | compare `docker exec eduvark-postgres psql -c "select object_key from materials"` with the bucket listing; copy the object to the DB key |
| Deploy fails "not an ancestor of origin/main" | SHA from a PR/unmerged branch | merge first or use a commit on `main` |
| Deploy fails "insufficient disk" | images/dumps | cleanup section above, then retry |
| Backup timer missed | LXC was off at 03:30 | `Persistent=true` runs it at next boot; check `systemctl list-timers` |
| `sudo: a password is required` in job | sudoers file missing | `visudo -cf /etc/sudoers.d/eduvark-runner` |

### Deploy job stuck in `queued` after a runner outage

```bash
gh run list --workflow=deploy-homelab.yml --limit 5      # find the stale run
gh run cancel <run_id>
gh workflow run deploy-homelab.yml --ref main -f sha=<sha-on-main> -f mode=deploy
```

`validate` re-checks that the SHA is an ancestor of `main` and that CI is green,
so a re-dispatch cannot bypass the gates. Recovery needs a human until the
queue-guard workflow (layer 2) exists.

## Maintenance log

| Date | Action | Result |
| --- | --- | --- |
| 2026-11-22 | LXC 10100 provisioned, Docker 29.10.0, PostgreSQL 17, backend image, admin seed, runner `eduvark-lxc-10100`, deploy engine, backup timer | healthy, public API reachable |
| 2026-11-22 | CI fixed (Android SDK installation), CD added (`deploy-homelab.yml`) | see GitHub Actions |
| 2026-11-23 | Deploy engine fixed (compose **service** name, image assertion), engine + runner unit + sudoers versioned in git | first real deploy succeeded; `state.json` records SHA, actor and run id |
| 2026-11-23 | CD switched to automatic (`workflow_run` after CI on `main`); required reviewers removed from `homelab-testing` | `actor`/`run_id` now propagate through sudo |
| 2026-11-23 | `R2_ENDPOINT` corrected (bucket path removed); 5 material objects copied from the doubled key prefix to the DB key | presigned downloads 200/200 bytes match; app opens materials |
| 2026-11-23 | `eduvark-runner-guard.timer` installed (5 min, 3 restarts/hour, lock-aware) | would have caught the 03:24Z wedge within ~2 min instead of 46 |

## Open actions

1. ~~Fill `R2_*` credentials~~ — done 2026-11-23 (endpoint corrected, objects re-keyed).
2. Configure offsite backup copy (restic/rclone → R2, or `vzdump` to another node).
3. Add uptime monitoring for `https://pam.sequein.my.id/health/ready` + backup-failure
   alerting.
4. Harden SSH in LXC `10100` (key-only, no root password login) — currently `pct exec`
   is the only access path used.
5. CD is now automatic on `main`, so the human control point is the **merge**:
   require a pull request plus review in the `main-protection` ruleset (currently
   only the 3 CI checks are required, so a direct push deploys).
6. Schedule the quarterly rollback drill from [`rollback.md`](rollback.md).
7. Layer 2 queue guard (auto cancel + re-dispatch of a deploy stuck in `queued`).
   Not built: needs a scheduled workflow. Until then the recipe above is manual.
8. Restore the 8-document layout from the master plan
   (`TROUBLESHOOTING.md` in particular) — this file still covers that role.