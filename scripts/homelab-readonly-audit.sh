#!/usr/bin/env bash
# Read-only audit untuk LXC 10100 (EduVARK). Tidak mengubah apa pun.
# Jalankan di dalam LXC sebagai root:
#   pct push 10100 scripts/homelab-readonly-audit.sh /tmp/audit.sh
#   pct exec 10100 -- bash /tmp/audit.sh
# Tidak ada nilai secret yang dicetak (hanya nama variabel).
set -uo pipefail

echo "== host =="
hostname
id -un
. /etc/os-release 2>/dev/null && echo "${PRETTY_NAME:-unknown}"

echo
echo "== docker =="
docker --version
docker compose version

echo
echo "== compose projects =="
docker compose ls

echo
echo "== containers =="
docker ps -a --format 'table {{.Names}}\t{{.Image}}\t{{.Status}}\t{{.Ports}}'

echo
echo "== images =="
docker image ls --format 'table {{.Repository}}:{{.Tag}}\t{{.ID}}\t{{.CreatedSince}}\t{{.Size}}' | head -10

echo
echo "== volumes =="
docker volume ls --format '{{.Name}}' | grep -i eduvark || true
docker volume inspect try-pam_eduvark_pgdata --format '{{.Name}} {{.Mountpoint}}' 2>/dev/null || true

echo
echo "== networks =="
docker network ls --format '{{.Name}}' | grep -i eduvark || true
docker network inspect try-pam_eduvark_internal \
  --format '{{.Name}} {{range .Containers}}{{.Name}} {{end}}' 2>/dev/null || true

echo
echo "== repo =="
cd /opt/eduvark/try-pam || exit 1
pwd
git log --oneline -1
git status --short | head -10
ls -1

echo
echo "== compose files =="
ls -1 docker-compose*.yml 2>/dev/null || true

echo
echo "== env files (keys only, no values) =="
for f in .env backend/.env.production; do
  if [ -f "$f" ]; then
    echo "[$f] perms=$(stat -c '%a %U:%G' "$f")"
    grep -oE '^[A-Z0-9_]+=' "$f" | tr -d '=' | tr '\n' ' '
    echo
  else
    echo "[$f] MISSING"
  fi
done

echo
echo "== postgres =="
docker exec eduvark-postgres psql -U eduvark -d eduvark -tAc \
  "select count(*) from information_schema.tables where table_schema='public'" \
  2>/dev/null | sed 's/^/tables: /'
docker exec eduvark-postgres psql -U eduvark -d eduvark -tAc \
  "select id from schema_migrations order by id" 2>/dev/null | sed 's/^/migration: /'
docker exec eduvark-postgres psql -U eduvark -d eduvark -tAc \
  "select pg_size_pretty(pg_database_size('eduvark'))" 2>/dev/null | sed 's/^/db size: /'

echo
echo "== health =="
curl -fsS http://127.0.0.1:3000/health/ready || echo "health gagal"
echo

echo "== disk =="
df -h / /var/lib/docker 2>/dev/null | tail -3

echo
echo "== restart/health container =="
docker inspect eduvark-backend eduvark-postgres \
  --format '{{.Name}} restarts={{.RestartCount}} started={{.State.StartedAt}} health={{if .State.Health}}{{.State.Health.Status}}{{else}}none{{end}}' \
  2>/dev/null || true
