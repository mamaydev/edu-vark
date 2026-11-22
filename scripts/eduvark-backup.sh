#!/usr/bin/env bash
# Nightly PostgreSQL logical backup for EduVARK homelab.
# Installed as /usr/local/sbin/eduvark-backup (root:root 0750) and triggered by
# eduvark-backup.timer. Deployment-time backups are created by
# /usr/local/sbin/eduvark-deploy and are never overwritten.
set -Eeuo pipefail
umask 077

BACKUP_DIR=/opt/eduvark/backups
LOG_FILE=/var/lib/eduvark/backup.log
POSTGRES_CONTAINER=eduvark-postgres
KEEP=${EDUVARK_KEEP_BACKUPS:-14}

log() { printf '%s [eduvark-backup] %s\n' "$(date -Is)" "$*" | tee -a "$LOG_FILE" >&2; }
die() { log "ERROR: $*"; exit 1; }

[ "$(id -u)" -eq 0 ] || die "must run as root"
install -d -m 0750 -o root -g root "$BACKUP_DIR" /var/lib/eduvark
touch "$LOG_FILE"; chmod 0600 "$LOG_FILE"

docker inspect -f '{{.State.Health.Status}}' "$POSTGRES_CONTAINER" 2>/dev/null |
  grep -qx healthy || die "postgres container is not healthy"

ts=$(date -u +%Y%m%dT%H%M%SZ)
file="$BACKUP_DIR/eduvark-nightly-${ts}.dump"
tmp="${file}.partial"
[ ! -e "$file" ] || die "backup already exists: $file"

log "starting nightly backup: $file"
docker exec "$POSTGRES_CONTAINER" pg_dump -U eduvark -d eduvark -Fc >"$tmp" ||
  { rm -f "$tmp"; die "pg_dump failed"; }
size=$(stat -c '%s' "$tmp")
[ "$size" -ge 1024 ] || { rm -f "$tmp"; die "backup too small (${size} bytes)"; }
docker exec -i "$POSTGRES_CONTAINER" pg_restore -l <"$tmp" >/dev/null ||
  { rm -f "$tmp"; die "backup verification failed"; }
mv "$tmp" "$file"; chmod 0600 "$file"
log "nightly backup ok: $file (${size} bytes)"

find "$BACKUP_DIR" -maxdepth 1 -type f -name '*.dump' -printf '%T@ %p\n' |
  sort -rn | awk -v keep="$KEEP" 'NR>keep {sub(/^[^ ]+ /, ""); print}' |
  while read -r old; do [ -n "$old" ] && rm -f -- "$old" && log "pruned: $old"; done

df -Pm / | awk 'NR==2 {printf "%s [eduvark-backup] free disk: %sMB\n", strftime("%Y-%m-%dT%H:%M:%S%z"), $4}' | tee -a "$LOG_FILE" >&2