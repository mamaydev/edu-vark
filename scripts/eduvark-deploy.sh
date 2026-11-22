#!/usr/bin/env bash
# EduVARK homelab deployment engine.
#
# This script is executed as root on LXC 10100 by the GitHub Actions runner
# through a narrow sudoers rule:
#
#   eduvark-runner ALL=(root) NOPASSWD: /usr/local/sbin/eduvark-deploy ...
#
# It is the only privileged entry point the runner may use. The runner never
# gets docker group membership, so it cannot talk to the Docker socket
# directly; every docker command is executed here as root.
#
# Subcommands:
#   verify <sha>     read-only validation (ref, ancestry, compose, disk, env)
#   dry-run <sha>    verify + database backup + image build (no service change)
#   deploy <sha>     full deployment: backup, build, migrate, switch, health
#   rollback [sha]   switch back to a previous image tag (no database restore)
#   status           print deployment state and container status
#
# Safety rules:
#   - never `docker compose down -v`, `docker volume prune`, or `DROP DATABASE`
#   - database backups are never overwritten (timestamped, unique)
#   - rollback does NOT restore the database automatically
set -Eeuo pipefail
umask 077

REPO_DIR=/opt/eduvark/try-pam
COMPOSE_FILE=docker-compose.homelab.yml
PROJECT_NAME=try-pam
BACKUP_DIR=/opt/eduvark/backups
STATE_DIR=/var/lib/eduvark
STATE_FILE="$STATE_DIR/state.json"
LOCK_FILE="$STATE_DIR/deploy.lock"
ENV_FILE="$REPO_DIR/.env"
PROD_ENV="$REPO_DIR/backend/.env.production"
POSTGRES_CONTAINER=eduvark-postgres
BACKEND_CONTAINER=eduvark-backend
# `docker compose up` addresses the *service*, not the container_name.
BACKEND_SERVICE=backend
HOST_HEALTH_URL=http://192.168.0.100:3000/health/ready
PUBLIC_BASE_URL=https://pam.sequein.my.id
MIN_FREE_MB=${EDUVARK_MIN_FREE_MB:-3072}
KEEP_BACKUPS=${EDUVARK_KEEP_BACKUPS:-14}
LOG_FILE="$STATE_DIR/deploy.log"

log() { printf '%s [eduvark-deploy] %s\n' "$(date -Is)" "$*" | tee -a "$LOG_FILE" >&2; }
die() { log "ERROR: $*"; exit 1; }

require_root() {
  [ "$(id -u)" -eq 0 ] || die "must run as root (invoke through sudo)"
}

init_state_dir() {
  install -d -m 0750 -o root -g root "$STATE_DIR" "$BACKUP_DIR"
  touch "$LOG_FILE" "$STATE_FILE"
  chmod 0600 "$LOG_FILE" "$STATE_FILE"
}

compose() {
  ( cd "$REPO_DIR" && docker compose -f "$COMPOSE_FILE" --project-name "$PROJECT_NAME" "$@" )
}

env_get() {
  local key=$1
  sed -n "s/^${key}=//p" "$ENV_FILE" | tail -n1
}

set_backend_tag() {
  local tag=$1
  if grep -q '^BACKEND_TAG=' "$ENV_FILE"; then
    sed -i "s/^BACKEND_TAG=.*/BACKEND_TAG=${tag}/" "$ENV_FILE"
  else
    printf 'BACKEND_TAG=%s\n' "$tag" >>"$ENV_FILE"
  fi
  chmod 0600 "$ENV_FILE"
}

prepare_repo() {
  cd "$REPO_DIR"
  local origin_url
  origin_url=$(git config --get remote.origin.url)
  [ "$origin_url" = "https://github.com/mamaydev/edu-vark.git" ] ||
    die "unexpected origin URL: $origin_url"
  git fetch --quiet --prune origin main
}

git_clean_checkout() {
  cd "$REPO_DIR"
  if ! git diff --quiet || ! git diff --cached --quiet; then
    git checkout --quiet -- . 2>/dev/null || true
    git reset --quiet --hard HEAD
  fi
}

resolve_sha() {
  local input=${1:-}
  [ -n "$input" ] || die "commit SHA is required"
  [[ "$input" =~ ^[0-9a-f]{7,40}$ ]] || die "invalid SHA format: $input"
  prepare_repo
  cd "$REPO_DIR"
  local sha
  sha=$(git rev-parse --verify "${input}^{commit}" 2>/dev/null) ||
    die "commit not found locally after fetch: $input"
  git merge-base --is-ancestor "$sha" origin/main ||
    die "commit $sha is not an ancestor of origin/main; refusing to deploy"
  printf '%s' "$sha"
}

check_capacity() {
  local avail_mb
  avail_mb=$(df -Pm / | awk 'NR==2 {print $4}')
  log "disk available: ${avail_mb}MB (minimum ${MIN_FREE_MB}MB)"
  [ "$avail_mb" -ge "$MIN_FREE_MB" ] ||
    die "insufficient disk space: ${avail_mb}MB < ${MIN_FREE_MB}MB"
  local mem_avail
  mem_avail=$(awk '/MemAvailable/ {print int($2/1024)}' /proc/meminfo)
  log "memory available: ${mem_avail}MB"
  [ "$mem_avail" -ge 384 ] || die "insufficient memory: ${mem_avail}MB < 384MB"
}

check_env_files() {
  [ -f "$ENV_FILE" ] || die "missing $ENV_FILE"
  [ -f "$PROD_ENV" ] || die "missing $PROD_ENV"
  local mode
  mode=$(stat -c '%a' "$ENV_FILE")
  [ "$mode" = "600" ] || die "$ENV_FILE must be chmod 600 (found $mode)"
  mode=$(stat -c '%a' "$PROD_ENV")
  [ "$mode" = "600" ] || die "$PROD_ENV must be chmod 600 (found $mode)"
  grep -q '^NODE_ENV=production$' "$PROD_ENV" || die "NODE_ENV must be production"
  grep -q '^DATABASE_URL=postgresql://' "$PROD_ENV" || die "DATABASE_URL missing"
  grep -q '^JWT_ACCESS_SECRET=.\{32,\}' "$PROD_ENV" || die "JWT_ACCESS_SECRET too short"
  grep -q '^JWT_REFRESH_SECRET=.\{32,\}' "$PROD_ENV" || die "JWT_REFRESH_SECRET too short"
}

check_runtime() {
  docker info >/dev/null 2>&1 || die "docker daemon is not available"
  docker compose version >/dev/null 2>&1 || die "docker compose plugin missing"
  ( cd "$REPO_DIR" && BACKEND_TAG="$(env_get BACKEND_TAG)" \
      docker compose -f "$COMPOSE_FILE" --project-name "$PROJECT_NAME" config --quiet ) ||
    die "docker compose configuration is invalid"
  docker inspect -f '{{.State.Health.Status}}' "$POSTGRES_CONTAINER" 2>/dev/null |
    grep -qx healthy || die "postgres container is not healthy"
}

backup_database() {
  local sha=$1 ts file tmp
  ts=$(date -u +%Y%m%dT%H%M%SZ)
  file="$BACKUP_DIR/eduvark-${ts}-${sha:0:12}.dump"
  tmp="${file}.partial"
  [ ! -e "$file" ] || die "backup already exists, refusing to overwrite: $file"
  log "creating database backup: $file"
  docker exec "$POSTGRES_CONTAINER" pg_dump -U eduvark -d eduvark -Fc >"$tmp" ||
    die "pg_dump failed"
  local size
  size=$(stat -c '%s' "$tmp")
  [ "$size" -ge 1024 ] || die "backup is suspiciously small (${size} bytes)"
  docker exec -i "$POSTGRES_CONTAINER" pg_restore -l <"$tmp" >/dev/null ||
    die "backup verification failed (pg_restore -l)"
  mv "$tmp" "$file"
  chmod 0600 "$file"
  log "backup ok: $file (${size} bytes)"
  printf '%s' "$file"
}

prune_backups() {
  find "$BACKUP_DIR" -maxdepth 1 -type f -name 'eduvark-*.dump' -printf '%T@ %p\n' 2>/dev/null |
    sort -rn | awk -v keep="$KEEP_BACKUPS" 'NR>keep {sub(/^[^ ]+ /, ""); print}' |
    while read -r old; do
      [ -n "$old" ] && rm -f -- "$old" && log "pruned old backup: $old"
    done
}

migration_snapshot() {
  docker exec "$POSTGRES_CONTAINER" psql -U eduvark -d eduvark -tAc \
    "select coalesce(string_agg(id, ',' order by id), '') from schema_migrations" 2>/dev/null ||
    printf ''
}

wait_host_health() {
  local attempts=${1:-30} i
  for i in $(seq 1 "$attempts"); do
    if curl -fsS --max-time 5 "$HOST_HEALTH_URL" >/dev/null 2>&1; then
      log "host health check passed (attempt $i)"
      return 0
    fi
    sleep 2
  done
  return 1
}

check_public_health() {
  if [ "${EDUVARK_SKIP_PUBLIC:-0}" = "1" ]; then
    log "WARN: public health check skipped by EDUVARK_SKIP_PUBLIC=1"
    return 0
  fi
  local i
  for i in $(seq 1 10); do
    if curl -fsS --max-time 15 "$PUBLIC_BASE_URL/health/ready" >/dev/null 2>&1; then
      log "public health check passed (attempt $i)"
      return 0
    fi
    sleep 3
  done
  return 1
}

smoke_tests() {
  local code
  curl -fsS --max-time 10 "$PUBLIC_BASE_URL/api/v1/health" >/dev/null ||
    { log "smoke failed: /api/v1/health"; return 1; }
  code=$(curl -s -o /dev/null -w '%{http_code}' --max-time 10 "$PUBLIC_BASE_URL/api/v1/topics")
  [ "$code" = "401" ] || { log "smoke failed: /api/v1/topics expected 401, got $code"; return 1; }
  log "smoke tests passed"
}

write_state() {
  local sha=$1 prev_sha=$2 image=$3 prev_image=$4 backup=$5 mig_before=$6 mig_after=$7 result=$8 started=$9
  local finished
  finished=$(date -u +%Y-%m-%dT%H:%M:%SZ)
  jq -n \
    --arg sha "$sha" --arg prev_sha "$prev_sha" \
    --arg image "$image" --arg prev_image "$prev_image" \
    --arg backup "$backup" --arg mig_before "$mig_before" \
    --arg mig_after "$mig_after" --arg result "$result" \
    --arg started "$started" --arg finished "$finished" \
    --arg actor "${GITHUB_ACTOR:-manual}" \
    --arg run "${GITHUB_RUN_ID:-}" \
    '{deployed_sha:$sha, previous_sha:$prev_sha, image:$image,
      previous_image:$prev_image, backup:$backup,
      migrations_before:$mig_before, migrations_after:$mig_after,
      result:$result, started_at:$started, finished_at:$finished,
      actor:$actor, run_id:$run}' >"$STATE_FILE"
  chmod 0600 "$STATE_FILE"
  cp "$STATE_FILE" "$STATE_DIR/history-$(date -u +%Y%m%dT%H%M%SZ).json"
  log "deployment state recorded: $STATE_FILE"
}

switch_backend() {
  local tag=$1 running_image
  BACKEND_TAG="$tag" compose up -d --no-deps --wait --wait-timeout 120 "$BACKEND_SERVICE" ||
    return 1
  # A compose call that silently no-ops would otherwise be reported as success
  # while the previous image keeps serving traffic.
  running_image=$(docker inspect -f '{{.Config.Image}}' "$BACKEND_CONTAINER" 2>/dev/null) || return 1
  [ "$running_image" = "eduvark-backend:$tag" ] || {
    log "switch did not take effect: running '$running_image', expected 'eduvark-backend:$tag'"
    return 1
  }
}

rollback_to() {
  local prev_image=$1 reason=$2
  [ -n "$prev_image" ] || { log "no previous image recorded; manual intervention required"; return 1; }
  log "rolling back to previous image: $prev_image ($reason)"
  if switch_backend "$prev_image" && wait_host_health 30; then
    set_backend_tag "$prev_image"
    log "rollback succeeded to $prev_image"
    return 0
  fi
  log "rollback FAILED; manual intervention required"
  return 1
}

cmd_verify() {
  local sha
  sha=$(resolve_sha "${1:-}")
  check_env_files
  check_runtime
  check_capacity
  log "verify ok: sha=$sha tag=${sha:0:12} current_tag=$(env_get BACKEND_TAG)"
  printf 'VERIFIED_SHA=%s\n' "$sha"
}

cmd_dry_run() {
  local sha backup mig
  exec 9>"$LOCK_FILE"
  flock -n 9 || die "another deployment is already running"
  sha=$(resolve_sha "${1:-}")
  check_env_files
  check_runtime
  check_capacity
  mig=$(migration_snapshot)
  backup=$(backup_database "$sha")
  log "dry-run: building image without switching services"
  cd "$REPO_DIR"
  BACKEND_TAG="${sha:0:12}" compose build backend
  log "dry-run ok: sha=${sha:0:12} backup=$backup migrations=$mig"
}

cmd_deploy() {
  local sha prev_sha prev_image image backup mig_before mig_after started
  sha=$(resolve_sha "${1:-}")
  started=$(date -u +%Y-%m-%dT%H:%M:%SZ)
  image=${sha:0:12}

  exec 9>"$LOCK_FILE"
  flock -n 9 || die "another deployment is already running"

  check_env_files
  check_runtime
  check_capacity
  git_clean_checkout

  cd "$REPO_DIR"
  prev_sha=$(git rev-parse HEAD 2>/dev/null || true)
  prev_image=$(env_get BACKEND_TAG)
  log "deploy start: sha=$sha image=$image previous_sha=${prev_sha:-none} previous_image=${prev_image:-none}"

  backup=$(backup_database "$sha")
  mig_before=$(migration_snapshot)

  git -c advice.detachedHead=false checkout --quiet --detach "$sha"
  [ "$(git rev-parse HEAD)" = "$sha" ] || die "checkout did not land on $sha"

  log "building backend image eduvark-backend:$image"
  BACKEND_TAG="$image" compose build backend || {
    write_state "$sha" "$prev_sha" "$image" "$prev_image" "$backup" "$mig_before" "$mig_before" "failed_build" "$started"
    die "image build failed; running service untouched"
  }

  log "running database migrations"
  if ! BACKEND_TAG="$image" compose run --rm --no-deps backend node dist/db/migrate.js; then
    write_state "$sha" "$prev_sha" "$image" "$prev_image" "$backup" "$mig_before" "$mig_before" "failed_migration" "$started"
    die "migration failed; running service untouched, database backup at $backup"
  fi
  mig_after=$(migration_snapshot)
  log "migrations: before=[${mig_before}] after=[${mig_after}]"

  log "switching backend to eduvark-backend:$image"
  if ! switch_backend "$image"; then
    rollback_to "$prev_image" "backend failed to start/healthy" || true
    write_state "$sha" "$prev_sha" "$image" "$prev_image" "$backup" "$mig_before" "$mig_after" "failed_switch" "$started"
    die "backend switch failed"
  fi

  if ! wait_host_health 40; then
    rollback_to "$prev_image" "host health check failed" || true
    write_state "$sha" "$prev_sha" "$image" "$prev_image" "$backup" "$mig_before" "$mig_after" "failed_health" "$started"
    die "host health check failed"
  fi

  if ! check_public_health; then
    log "WARN: public endpoint health check failed; keeping new backend (ingress issue?)"
  fi

  if ! smoke_tests; then
    rollback_to "$prev_image" "smoke tests failed" || true
    write_state "$sha" "$prev_sha" "$image" "$prev_image" "$backup" "$mig_before" "$mig_after" "failed_smoke" "$started"
    die "smoke tests failed"
  fi

  set_backend_tag "$image"
  prune_backups
  write_state "$sha" "$prev_sha" "$image" "$prev_image" "$backup" "$mig_before" "$mig_after" "success" "$started"
  log "deploy success: sha=$sha image=$image"
  printf 'DEPLOYED_SHA=%s\nIMAGE=eduvark-backend:%s\nBACKUP=%s\n' "$sha" "$image" "$backup"
}

cmd_rollback() {
  local target=${1:-} prev_image prev_sha
  exec 9>"$LOCK_FILE"
  flock -n 9 || die "another deployment is already running"
  if [ -z "$target" ]; then
    prev_image=$(jq -r '.previous_image // empty' "$STATE_FILE" 2>/dev/null || true)
    prev_sha=$(jq -r '.previous_sha // empty' "$STATE_FILE" 2>/dev/null || true)
  else
    prev_sha=$(resolve_sha "$target")
    prev_image=${prev_sha:0:12}
  fi
  [ -n "$prev_image" ] || die "no target image available for rollback"
  docker image inspect "eduvark-backend:$prev_image" >/dev/null 2>&1 ||
    die "image eduvark-backend:$prev_image not present locally; rebuild it first"
  switch_backend "$prev_image" || die "failed to start image $prev_image"
  wait_host_health 40 || die "rollback health check failed"
  set_backend_tag "$prev_image"
  log "manual rollback complete: image=$prev_image sha=${prev_sha:-unknown}"
  log "NOTE: database was NOT restored; do it manually only if the migration is not backward compatible"
}

cmd_status() {
  log "state: $(cat "$STATE_FILE" 2>/dev/null || echo '{}')"
  compose ps || true
  docker image ls --filter 'reference=eduvark-backend:*' --format '{{.Repository}}:{{.Tag}} {{.ID}} {{.CreatedSince}}' || true
}

main() {
  require_root
  init_state_dir
  cd "$REPO_DIR" 2>/dev/null || die "missing deployment directory $REPO_DIR"
  local sub=${1:-}
  shift || true
  case "$sub" in
  verify) cmd_verify "${1:-}" ;;
  dry-run) cmd_dry_run "${1:-}" ;;
  deploy) cmd_deploy "${1:-}" ;;
  rollback) cmd_rollback "${1:-}" ;;
  status) cmd_status ;;
  *) die "usage: $0 {verify|dry-run|deploy|rollback|status} [sha]" ;;
  esac
}

main "$@"