#!/usr/bin/env bash
# Offline simulation harness for scripts/eduvark-deploy.sh failure behaviour.
#
# Purpose (Paket: release hold / deploy failure audit):
#   * replay the real cmd_deploy control flow locally, WITHOUT root, Docker
#     daemon access, the homelab host, the GitHub workflow, or the active
#     deployment;
#   * prove the migration-failure branch keeps the old service running and does
#     not switch images;
#   * prove the smoke-failure branch auto-rolls back the image only, never
#     restores the database, and never closes/reopens traffic;
#   * show that smoke tests depend on the public URL, so they fail when the
#     public API is closed (the ordered cutover the runbook asks for cannot be
#     executed by this engine).
#
# Method: the production engine is copied verbatim, its last line (`main "$@"`)
# is removed, and only its hard-coded absolute paths are rewritten into a temp
# sandbox. All privileged binaries are replaced by recording shims on PATH.
# No file in the repository is modified; output is written under sim-output/.
#
# Usage: bash docs/audit/evidence/deploy-sim/run-sim.sh
set -uo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$HERE/../../../.." && pwd)"
SRC="$REPO_ROOT/scripts/eduvark-deploy.sh"
OUT="$HERE/sim-output"
TARGET_SHA=d3aed78abcfefa97dac35556698b01307fc1af3b
PREV_SHA=b7b6cd6d2be19c1f0aa93f2b95454049984250d5
PREV_IMAGE=b7b6cd6d2be1
TARGET_IMAGE=d3aed78abcfe

[ -f "$SRC" ] || { echo "missing $SRC" >&2; exit 2; }
rm -rf "$OUT"
mkdir -p "$OUT"

FAILS=0
note() { printf '%s\n' "$*"; }

# ---------------------------------------------------------------- mock bins --
write_mocks() {
  local SB=$1
  mkdir -p "$SB/bin" "$SB/repo/backend" "$SB/state" "$SB/backups" "$SB/calls"

  cat >"$SB/bin/docker" <<'EOS'
#!/usr/bin/env bash
printf 'TAG=%s docker %s\n' "${BACKEND_TAG:-}" "$*" >>"$SB/calls/docker.log"
case "$*" in
  "info") exit 0 ;;
  "compose version") exit 0 ;;
  *"pg_dump"*) head -c 4096 /dev/zero | tr '\0' 'x'; exit 0 ;;
  *"pg_restore -l"*) cat >/dev/null; exit 0 ;;
  *"psql"*) if [ -f "$SB/migrated" ]; then echo "001,002,003"; else echo "001,002"; fi; exit 0 ;;
  *"State.Health.Status"*) echo healthy; exit 0 ;;
  *"Config.Image"*) cat "$SB/current_image" 2>/dev/null || exit 1 ;;
  *"image inspect"*) exit 0 ;;
  *"compose"*"config --quiet"*) exit 0 ;;
  *"compose"*"build backend"*) exit 0 ;;
  *"compose"*"run --rm --no-deps backend node dist/db/migrate.js"*)
    if [ "${MIGRATE_EXIT:-1}" = "0" ]; then touch "$SB/migrated"; fi
    exit "${MIGRATE_EXIT:-1}" ;;
  *"compose"*"up -d"*) printf 'eduvark-backend:%s' "${BACKEND_TAG:-}" >"$SB/current_image"; exit 0 ;;
  *"compose"*"ps"*) exit 0 ;;
  *"compose"*) exit 0 ;;
  *) exit 0 ;;
esac
EOS

  cat >"$SB/bin/git" <<'EOS'
#!/usr/bin/env bash
printf 'git %s\n' "$*" >>"$SB/calls/git.log"
case "$*" in
  *"config --get remote.origin.url"*) echo "https://github.com/mamaydev/edu-vark.git" ;;
  *"fetch"*) ;;
  *"rev-parse --verify"*) echo "$TARGET_SHA" ;;
  *"merge-base --is-ancestor"*) exit 0 ;;
  *"checkout"*) printf '%s' "$TARGET_SHA" >"$SB/git_head" ;;
  *"rev-parse HEAD"*) if [ -f "$SB/git_head" ]; then cat "$SB/git_head"; else echo "$PREV_SHA"; fi ;;
  *"diff --cached --quiet"*|*"diff --quiet"*) exit 0 ;;
  *) ;;
esac
exit 0
EOS

  cat >"$SB/bin/curl" <<'EOS'
#!/usr/bin/env bash
printf 'curl %s\n' "$*" >>"$SB/calls/curl.log"
url=""
for a in "$@"; do case "$a" in http://*|https://*) url="$a" ;; esac; done
case "$url" in
  http://sim.local*) exit 0 ;;
  http://sim.public/health/ready)
    [ "${PUBLIC_STATE:-open}" = "closed" ] && exit 7
    exit 0 ;;
  http://sim.public/api/v1/health)
    [ "${PUBLIC_STATE:-open}" = "closed" ] && exit 7
    [ "${SMOKE_STATE:-ok}" = "fail" ] && exit 22
    exit 0 ;;
  http://sim.public/api/v1/topics)
    [ "${PUBLIC_STATE:-open}" = "closed" ] && exit 7
    printf '401'; exit 0 ;;
  *) exit 0 ;;
esac
EOS

  cat >"$SB/bin/date" <<'EOS'
#!/usr/bin/env bash
if [ "${1:-}" = "-Is" ]; then /bin/date -u '+%Y-%m-%dT%H:%M:%S+00:00'; else exec /bin/date "$@"; fi
EOS

  cat >"$SB/bin/stat" <<'EOS'
#!/usr/bin/env bash
if [ "${1:-}" = "-c" ]; then
  fmt=$2; shift 2
  case "$fmt" in
    '%s') wc -c <"$1" | tr -d ' ' ;;
    '%a') echo 600 ;;
    *) exec /usr/bin/stat -f "$fmt" "$@" ;;
  esac
else exec /usr/bin/stat "$@"; fi
EOS

  cat >"$SB/bin/sed" <<'EOS'
#!/usr/bin/env bash
if [ "${1:-}" = "-i" ]; then expr=$2; file=$3; exec /usr/bin/sed -i '' "$expr" "$file"; fi
exec /usr/bin/sed "$@"
EOS

  printf '#!/usr/bin/env bash\nexit 0\n' >"$SB/bin/flock"
  printf '#!/usr/bin/env bash\nexit 0\n' >"$SB/bin/sleep"
  chmod +x "$SB/bin/"*

  printf 'BACKEND_TAG=%s\n' "$PREV_IMAGE" >"$SB/repo/.env"
  cat >"$SB/repo/backend/.env.production" <<EOS
NODE_ENV=production
DATABASE_URL=postgresql://eduvark:sim@127.0.0.1:5432/eduvark
JWT_ACCESS_SECRET=simulation-access-secret-0123456789abcdef
JWT_REFRESH_SECRET=simulation-refresh-secret-0123456789abcdef
EOS
  chmod 600 "$SB/repo/.env" "$SB/repo/backend/.env.production"
}

# Build sandbox engine: verbatim copy minus `main "$@"`, constants rewritten.
derive_engine() {
  local SB=$1
  python3 - "$SB" "$SRC" <<'PY'
import sys
sb, src = sys.argv[1], sys.argv[2]
lines = open(src).read().rstrip('\n').split('\n')
assert lines[-1].strip() == 'main "$@"', 'unexpected script tail: %r' % lines[-1]
body = '\n'.join(lines[:-1]) + '\n'
body = body.replace('REPO_DIR=/opt/eduvark/try-pam', "REPO_DIR='%s/repo'" % sb)
body = body.replace('BACKUP_DIR=/opt/eduvark/backups', "BACKUP_DIR='%s/backups'" % sb)
body = body.replace('STATE_DIR=/var/lib/eduvark', "STATE_DIR='%s/state'" % sb)
body = body.replace('HOST_HEALTH_URL=http://192.168.0.100:3000/health/ready',
                    'HOST_HEALTH_URL=http://sim.local:3000/health/ready')
body = body.replace('PUBLIC_BASE_URL=https://pam.sequein.my.id',
                    'PUBLIC_BASE_URL=http://sim.public')
open(sb + '/engine.sh', 'w').write(body)
PY
}

# ---------------------------------------------------------------- assertions --
record() {
  local scenario=$1 result=$2 message=$3
  printf '%-5s %s\n' "$result" "$message" >>"$OUT/$scenario/assertions.txt"
  if [ "$result" != "PASS" ]; then FAILS=$((FAILS + 1)); fi
}

assert_eq() {
  local scenario=$1 actual=$2 expected=$3 message=$4
  if [ "$actual" = "$expected" ]; then record "$scenario" PASS "$message (actual=$actual)";
  else record "$scenario" FAIL "$message (actual=$actual expected=$expected)"; fi
}

assert_contains() {
  local scenario=$1 file=$2 needle=$3 message=$4
  if grep -qF -- "$needle" "$file" 2>/dev/null; then record "$scenario" PASS "$message";
  else record "$scenario" FAIL "$message (missing '$needle' in ${file##*/})"; fi
}

assert_not_contains() {
  local scenario=$1 file=$2 needle=$3 message=$4
  if grep -qF -- "$needle" "$file" 2>/dev/null; then record "$scenario" FAIL "$message (found '$needle')";
  else record "$scenario" PASS "$message"; fi
}

# ------------------------------------------------------------------ scenario --
run_scenario() {
  local name=$1 migrate_exit=$2 public_state=$3 smoke_state=$4
  local SB="$OUT/$name/sandbox"
  mkdir -p "$OUT/$name"
  write_mocks "$SB"
  derive_engine "$SB"
  sha256sum "$SB/engine.sh" 2>/dev/null | awk '{print $1}' >"$OUT/$name/engine.sha256" ||
    shasum -a 256 "$SB/engine.sh" | awk '{print $1}' >"$OUT/$name/engine.sha256"

  (
    cd "$SB" || exit 90
    export PATH="$SB/bin:$PATH"
    export SB TARGET_SHA PREV_SHA PREV_IMAGE
    export MIGRATE_EXIT="$migrate_exit"
    export PUBLIC_STATE="$public_state"
    export SMOKE_STATE="$smoke_state"
    set +e
    . "$SB/engine.sh"
    set +e
    require_root() { :; }
    check_capacity() { :; }
    if [ "$name" = "success" ]; then
      # macOS `find -printf` is unavailable; backup pruning is irrelevant here.
      prune_backups() { :; }
    fi
    ( cmd_deploy "$TARGET_SHA" >"$SB/scenario.out" 2>&1 )
    echo $? >"$SB/exit_code"
  )

  local code
  code=$(cat "$SB/exit_code" 2>/dev/null)
  case "$name" in
    migration-fail)
      assert_eq "$name" "$code" 1 "cmd_deploy exits 1 on migration failure"
      assert_eq "$name" "$(jq -r '.result' "$SB/state/state.json" 2>/dev/null)" failed_migration "state.result"
      assert_not_contains "$name" "$SB/calls/docker.log" "compose -f docker-compose.homelab.yml --project-name try-pam up" "no image switch after failed migration"
      assert_eq "$name" "$(sed -n 's/^BACKEND_TAG=//p' "$SB/repo/.env")" "$PREV_IMAGE" "BACKEND_TAG unchanged"
      assert_contains "$name" "$SB/state/deploy.log" "migration failed; running service untouched" "old service left running"
      assert_contains "$name" "$SB/calls/docker.log" "pg_dump" "backup happened before migration"
      local dump_line migrate_line
      dump_line=$(grep -n "pg_dump" "$SB/calls/docker.log" | head -1 | cut -d: -f1)
      migrate_line=$(grep -n "migrate.js" "$SB/calls/docker.log" | head -1 | cut -d: -f1)
      if [ -n "$dump_line" ] && [ -n "$migrate_line" ] && [ "$dump_line" -lt "$migrate_line" ]; then
        record "$name" PASS "order: backup (line $dump_line) before migration (line $migrate_line)"
      else
        record "$name" FAIL "order: backup before migration"
      fi
      ;;
    smoke-fail|public-closed)
      assert_eq "$name" "$code" 1 "cmd_deploy exits 1 when smoke fails"
      assert_eq "$name" "$(jq -r '.result' "$SB/state/state.json" 2>/dev/null)" failed_smoke "state.result"
      assert_eq "$name" "$(sed -n 's/^BACKEND_TAG=//p' "$SB/repo/.env")" "$PREV_IMAGE" "auto-rollback restored BACKEND_TAG"
      assert_contains "$name" "$SB/state/deploy.log" "rolling back to previous image" "rollback attempted"
      assert_contains "$name" "$SB/state/deploy.log" "rollback succeeded" "rollback image healthy"
      local ups first_up last_up
      ups=$(grep -c "up -d" "$SB/calls/docker.log")
      first_up=$(grep "up -d" "$SB/calls/docker.log" | head -1)
      last_up=$(grep "up -d" "$SB/calls/docker.log" | tail -1)
      assert_eq "$name" "$ups" 2 "two image switches (new then rollback)"
      case "$first_up" in *"TAG=$TARGET_IMAGE"*) record "$name" PASS "first switch uses new image $TARGET_IMAGE";; *) record "$name" FAIL "first switch uses new image ($first_up)";; esac
      case "$last_up" in *"TAG=$PREV_IMAGE"*) record "$name" PASS "rollback switch uses previous image $PREV_IMAGE";; *) record "$name" FAIL "rollback switch uses previous image ($last_up)";; esac
      local db_restores
      db_restores=$(grep "pg_restore" "$SB/calls/docker.log" | grep -vc -- "-l")
      assert_eq "$name" "$db_restores" 0 "rollback performed no database restore"
      assert_not_contains "$name" "$SB/calls/docker.log" "down -v" "no volume teardown"
      if [ "$name" = "public-closed" ]; then
        assert_contains "$name" "$SB/state/deploy.log" "WARN: public endpoint health check failed" "public health failure only warns"
        local attempts
        attempts=$(grep -c "sim.public/health/ready" "$SB/calls/curl.log")
        assert_eq "$name" "$attempts" 10 "public health probed 10x while edge closed"
      else
        assert_contains "$name" "$SB/state/deploy.log" "smoke failed" "smoke failure recorded"
      fi
      ;;
    success)
      assert_eq "$name" "$code" 0 "sanity: deploy succeeds when smoke passes"
      assert_eq "$name" "$(jq -r '.result' "$SB/state/state.json" 2>/dev/null)" success "state.result"
      assert_eq "$name" "$(sed -n 's/^BACKEND_TAG=//p' "$SB/repo/.env")" "$TARGET_IMAGE" "BACKEND_TAG updated on success"
      ;;
  esac
}

run_scenario migration-fail 1 open ok
run_scenario smoke-fail 0 open fail
run_scenario public-closed 0 closed fail
run_scenario success 0 open ok

note ""
note "Simulation complete. Assertions per scenario:"
for d in "$OUT"/*/assertions.txt; do
  s=$(basename "$(dirname "$d")")
  pass=$(grep -c '^PASS' "$d" 2>/dev/null || true)
  fail=$(grep -c '^FAIL' "$d" 2>/dev/null || true)
  note "  $s: PASS=${pass:-0} FAIL=${fail:-0}"
done
note ""
if [ "$FAILS" -eq 0 ]; then note "RESULT: ALL SIMULATION ASSERTIONS PASS"; exit 0; fi
note "RESULT: $FAILS ASSERTION(S) FAILED"; exit 1
