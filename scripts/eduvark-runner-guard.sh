#!/usr/bin/env bash
# EduVARK GitHub Actions runner guard (layer 1).
#
# The self-hosted runner can wedge in a retry loop: the listener process stays
# alive, logs "Runner connect error" once, and then never returns to
# "Listening for Jobs". GitHub still reports the runner as online during this,
# so an API-based health check cannot detect it. This guard reads the runner's
# own journal instead and restarts the service only when it is safe to do so.
#
# Signals (all required to restart):
#   - the last "connect error" is older than GRACE seconds (the listener had
#     time to retry), and
#   - no "Listening for Jobs" happened after that error, and
#   - no job is executing, and
#   - the deployment engine does not hold its flock.
#
# Usage:
#   eduvark-runner-guard run            # used by the systemd timer
#   eduvark-runner-guard check [--since T] [--until T]
#                                       # print the decision, never restart
#
# `check` with an explicit window is how the detector is tested against real
# journal history (including the 2026-11-23T03:24Z wedge).
set -Eeuo pipefail
umask 077

UNIT=${EDUVARK_RUNNER_UNIT:-github-runner-eduvark.service}
WINDOW_MIN=${EDUVARK_GUARD_WINDOW_MIN:-15}
GRACE_SEC=${EDUVARK_GUARD_GRACE:-180}
MAX_RESTARTS=${EDUVARK_GUARD_MAX_RESTARTS:-3}
RECONNECT_WAIT=${EDUVARK_GUARD_RECONNECT_WAIT:-240}
STATE_DIR=/var/lib/eduvark
LOG_FILE="$STATE_DIR/runner-guard.log"
QUOTA_FILE="$STATE_DIR/runner-guard-restarts"
GUARD_LOCK="$STATE_DIR/runner-guard.lock"
DEPLOY_LOCK="$STATE_DIR/deploy.lock"

log() { printf '%s [runner-guard] %s\n' "$(date -Is)" "$*" | tee -a "$LOG_FILE" >&2; }

mode=${1:-run}
shift || true
since=""
until=""
while [ $# -gt 0 ]; do
  case "$1" in
  --since) since=${2:-}; shift 2 ;;
  --until) until=${2:-}; shift 2 ;;
  *) echo "usage: $0 {run|check} [--since T] [--until T]" >&2; exit 2 ;;
  esac
done

if [ "$(id -u)" -ne 0 ]; then
  echo "must run as root" >&2
  exit 1
fi
install -d -m 0750 -o root -g root "$STATE_DIR"
touch "$LOG_FILE"
chmod 0600 "$LOG_FILE"

# One guard at a time; a previous run may still be waiting for reconnection.
exec 8>>"$GUARD_LOCK"
if ! flock -n 8; then
  log "skip: another guard run is still in progress"
  exit 0
fi

journal_args=(-u "$UNIT" --no-pager -o short-iso)
if [ -n "$since" ]; then
  journal_args+=(--since "$since")
else
  journal_args+=(--since "-${WINDOW_MIN}min")
fi
[ -n "$until" ] && journal_args+=(--until "$until")

raw=$(journalctl "${journal_args[@]}" 2>/dev/null || true)

count_of() { printf '%s\n' "$raw" | grep -c "$1" || true; }
last_ts_of() { printf '%s\n' "$raw" | grep "$1" | tail -1 | awk '{print $1}' || true; }
last_no_of() { printf '%s\n' "$raw" | grep -n "$1" | tail -1 | cut -d: -f1 || true; }
to_epoch() { if [ -n "$1" ]; then date -d "$1" +%s 2>/dev/null || true; fi; }

errors=$(count_of 'connect error')
last_run=$(last_no_of 'Running job:')
last_done=$(last_no_of 'completed with result')
err_epoch=$(to_epoch "$(last_ts_of 'connect error')")
listen_epoch=$(to_epoch "$(last_ts_of 'Listening for Jobs')")

if [ -n "$until" ]; then
  ref_epoch=$(date -d "$until" +%s)
else
  ref_epoch=$(date +%s)
fi
if [ -n "$err_epoch" ]; then err_age=$((ref_epoch - err_epoch)); else err_age=-1; fi

decision=""
reason=""

if [ -z "$since" ] && ! systemctl is-active --quiet "$UNIT"; then
  decision=restart
  reason="service is not active"
elif [ -n "$last_run" ] && { [ -z "$last_done" ] || [ "$last_run" -gt "$last_done" ]; }; then
  # Checked before the error heuristics: a running job must never be killed,
  # whatever else the log says.
  decision=skip
  reason="job in progress (Running job after the last completed job)"
elif [ "${errors:-0}" -eq 0 ]; then
  decision=skip
  reason="healthy (no connect errors in window)"
elif [ "${err_age:-0}" -lt "$GRACE_SEC" ]; then
  decision=skip
  reason="connect error only ${err_age}s old; giving the listener time to retry"
elif [ -n "$listen_epoch" ] && [ "$listen_epoch" -gt "$err_epoch" ]; then
  decision=skip
  reason="recovered (Listening for Jobs after the last connect error)"
else
  decision=restart
  reason="wedged (last connect error ${err_age}s ago, never reconnected)"
fi

# Never restart underneath a running deployment engine.
if [ "$decision" = restart ]; then
  exec 9>>"$DEPLOY_LOCK"
  if ! flock -n 9; then
    decision=skip
    reason="deployment engine holds the deploy lock"
  fi
  exec 9>&-
fi

# Restart budget, so a broken network cannot turn the guard into a restart loop.
if [ "$decision" = restart ]; then
  now=$(date +%s)
  : >"${QUOTA_FILE}.new"
  while read -r ts; do
    [ -n "$ts" ] || continue
    [ "$ts" -gt $((now - 3600)) ] && printf '%s\n' "$ts" >>"${QUOTA_FILE}.new"
  done < <(cat "$QUOTA_FILE" 2>/dev/null || true)
  mv "${QUOTA_FILE}.new" "$QUOTA_FILE"
  used=$(wc -l <"$QUOTA_FILE" | tr -d ' ')
  if [ "${used:-0}" -ge "$MAX_RESTARTS" ]; then
    decision=skip
    reason="restart budget exhausted (${used}/${MAX_RESTARTS} in the last hour) - ALERT"
  fi
fi

if [ "$mode" = check ]; then
  printf 'decision : %s\nreason   : %s\n' "$decision" "$reason"
  printf 'inputs   : errors=%s err_age=%ss error_at=%s listening_at=%s job_running=%s\n' \
    "${errors:-0}" "${err_age}" "${err_epoch:-none}" "${listen_epoch:-none}" \
    "$([ -n "$last_run" ] && { [ -z "$last_done" ] || [ "$last_run" -gt "$last_done" ]; } && echo yes || echo no)"
  exit 0
fi

if [ "$decision" = skip ]; then
  log "skip: $reason"
  exit 0
fi

log "action=restart reason=$reason"
systemctl restart "$UNIT"

# The listener usually needs 1-3 minutes to obtain a fresh session.
deadline=$(( $(date +%s) + RECONNECT_WAIT ))
while [ "$(date +%s)" -lt "$deadline" ]; do
  if journalctl -u "$UNIT" --since "-${RECONNECT_WAIT}sec" --no-pager -o cat 2>/dev/null |
    grep -q 'Listening for Jobs'; then
    printf '%s\n' "$(date +%s)" >>"$QUOTA_FILE"
    chmod 0600 "$QUOTA_FILE"
    log "recovered: Listening for Jobs after restart"
    exit 0
  fi
  sleep 10
done

printf '%s\n' "$(date +%s)" >>"$QUOTA_FILE"
chmod 0600 "$QUOTA_FILE"
log "ALERT: runner did not reconnect within ${RECONNECT_WAIT}s after restart; investigate egress to GitHub"
exit 0
