# Backup and rollback

## What is backed up

| Item | Mechanism | Location | Retention |
| --- | --- | --- | --- |
| PostgreSQL data | `pg_dump -Fc` before every deployment | `/opt/eduvark/backups/eduvark-<ts>-<sha12>.dump` | 14 newest |
| PostgreSQL data | nightly `pg_dump -Fc` at 03:30 | `/opt/eduvark/backups/eduvark-nightly-<ts>.dump` | 14 newest |
| Docker volumes | not dumped separately (they only hold PostgreSQL data) | — | — |
| Images | `eduvark-backend:<sha12>` stays on disk after a release | local Docker image store | pruned manually |
| Deployment records | `/var/lib/eduvark/state.json` + `history-*.json` | LXC `10100` | unlimited |
| Compose/config | git history + root-only env files | `/opt/eduvark/try-pam` | git |

Backup integrity: every dump is written to `.partial`, checked for size (≥1 KB) and parsed
with `pg_restore -l` before it is renamed. If either check fails, the deployment aborts and
no `.partial` file is kept.

**No offsite copy exists yet.** Dumps live on the same LXC as the database; a container or
disk loss loses both. Adding an offsite target (restic/rclone to R2, a NAS, or
`pct vzdump` to another node) is an open action in [`security.md`](security.md).

## Application rollback

### Automatic (built into the deploy engine)

Triggered when the new container fails to start, fails its healthcheck, or fails the smoke
tests. The engine:

1. logs `rolling back to previous image: eduvark-backend:<previous>`;
2. runs `docker compose up -d --no-deps --wait backend` with the previous tag;
3. polls `/health/ready` for up to 60 s;
4. restores `BACKEND_TAG` in `.env`;
5. records the outcome (`failed_switch`, `failed_health`, `failed_smoke`).

The database is **not** restored: migrations are forward-only by design. If the failed
release applied a destructive/incompatible migration, follow "Database restore" below.

### Manual

```bash
# 1. inspect what the last release was and what to go back to
sudo /usr/local/sbin/eduvark-deploy status
cat /var/lib/eduvark/state.json | jq '{deployed_sha, previous_sha, previous_image, backup}'

# 2. roll the container back to the previous image
sudo /usr/local/sbin/eduvark-deploy rollback

# 3. or target a specific known-good commit (image must still exist locally)
sudo /usr/local/sbin/eduvark-deploy rollback <sha>
```

The command refuses to continue if the target image is missing; rebuild it first with
`dry-run <sha>` (or `deploy` to a staging container, which is out of scope here).

Verify afterwards:

```bash
pct exec 10100 -- docker inspect eduvark-backend --format '{{.Config.Image}} {{.State.Health.Status}}'
curl -fsS https://pam.sequein.my.id/health/ready
```

### Re-deploying an old commit through GitHub

Dispatch **Deploy homelab** with the old SHA. The `validate` job requires green CI for that
SHA; if the commit never had a successful CI run, use the manual `rollback <sha>` path
instead. Deploying the old commit rebuilds its image and re-runs migrations, which are
idempotent (`schema_migrations`), so it is safe.

## Database restore

Only when a migration or data change must be undone. This is a destructive operation on
live data — **get explicit approval, and take a fresh backup first**.

```bash
# 0. record current state and take an extra dump
sudo /usr/local/sbin/eduvark-deploy status
pct exec 10100 -- docker exec eduvark-postgres \
  pg_dump -U eduvark -d eduvark -Fc > /opt/eduvark/backups/pre-restore-$(date +%s).dump
```

Preferred (restore into a scratch database and copy only the affected tables) is out of
scope for a homelab, so the documented procedure is a full restore:

```bash
# 1. stop the API so nothing writes during the restore
cd /opt/eduvark/try-pam
docker compose -f docker-compose.homelab.yml stop backend

# 2. restore (choose ONE)
#    a) full replacement of the database contents
pct exec 10100 -- bash -c 'docker exec -i eduvark-postgres pg_restore -U eduvark -d eduvark \
  --clean --if-exists --no-owner --no-privileges' < /opt/eduvark/backups/<dump>

#    b) empty database (fresh start, then restore)
#       NEVER run "DROP DATABASE"; use the following only on an empty instance:
#       docker exec eduvark-postgres psql -U eduvark -d postgres -c 'CREATE DATABASE eduvark_restore'

# 3. start the API and verify
docker compose -f docker-compose.homelab.yml up -d --no-deps backend
curl -fsS http://192.168.0.100:3000/health/ready
```

`--clean --if-exists` drops objects, not the database. `DROP DATABASE` is forbidden by
policy; if a database must be recreated, do it from the Proxmox console with an explicit
approval and a known backup.

After a restore, confirm `schema_migrations` matches the code:

```bash
pct exec 10100 -- docker exec eduvark-postgres \
  psql -U eduvark -d eduvark -c 'select * from schema_migrations order by id'
```

If the restored dump is older than the deployed code, run the migrator again
(`docker compose run --rm --no-deps backend node dist/db/migrate.js`) and re-run the smoke
tests.

## Rollback drill (do this quarterly)

1. `verify` + `dry-run` the currently deployed SHA (proves backup + build still work).
2. `rollback` to the previous image, check health and smoke tests.
3. `rollback` forward via `deploy <current sha>`.
4. Restore the newest dump into a **scratch** PostgreSQL container and run
   `select count(*) from users; select count(*) from schema_migrations;` to prove the dump
   is usable.

Record the drill date and result in [`operations.md`](operations.md#maintenance-log).

## Decision matrix

| Situation | Action | DB restore? |
| --- | --- | --- |
| New image starts but returns 500s | automatic/manual image rollback | no |
| New migration is backward compatible | image rollback | no |
| Migration dropped/renamed a column the old code needs | image rollback + restore the pre-deploy dump | yes |
| Data corrupted by application bug | restore newest good dump | yes, with approval |
| Container/volume lost | rebuild from `provisioning.md` + newest dump | yes (into empty instance) |