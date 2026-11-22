# Deploy EduVARK di Homelab Proxmox (LXC 10100)

Panduan ini menggantikan versi lama yang mengasumsikan PostgreSQL eksternal/Supabase dan
reverse proxy Caddy/Nginx. Stack yang berjalan sekarang:

| Komponen | Lokasi |
|---|---|
| Android | build debug (emulator/HP) & release → `https://pam.sequein.my.id/api/v1/` |
| Cloudflare | DNS + Tunnel untuk `pam.sequein.my.id` |
| Ingress | LXC **10080** (`192.168.0.80`), Traefik v3, dynamic file provider di `/opt/ingress/dynamic` |
| Backend | LXC **10100** (`192.168.0.100`), Ubuntu 24.04, Docker Engine, `/opt/eduvark/try-pam` |
| Database | container `eduvark-postgres` (PostgreSQL 17), volume `try-pam_eduvark_pgdata`, network `try-pam_eduvark_internal` |
| Storage | Cloudflare R2 (`R2_*` di `backend/.env.production`) |

> Jangan menjalankan `docker compose down -v`, `docker volume prune`, atau
> `docker system prune --volumes`. Volume database tidak boleh dibuat ulang.

## 1. Prasyarat LXC 10100

- Ubuntu 24.04, unprivileged LXC dengan `nesting=1,keyctl=1`.
- Docker Engine + Docker Compose plugin.
- Repo ter-clone di `/opt/eduvark/try-pam` pada commit yang disetujui.
- Dua file konfigurasi (tidak di-commit):

```bash
cd /opt/eduvark/try-pam
cp .env.homelab.example .env
cp backend/.env.production.example backend/.env.production
chmod 600 .env backend/.env.production
```

`.env` berisi `POSTGRES_PASSWORD` (samakan dengan password pada `DATABASE_URL`),
`BACKEND_TAG`, dan `BACKEND_BIND_ADDRESS=192.168.0.100`. Database `eduvark` dan user
`eduvark` sudah dipin di compose mengikuti volume yang ada — jangan menambah/mengubah
`POSTGRES_DB`/`POSTGRES_USER` setelah volume terinisialisasi.

`backend/.env.production` minimal berisi:

```dotenv
NODE_ENV=production
PORT=3000
DATABASE_URL=postgresql://eduvark:<password>@postgres:5432/eduvark
JWT_ACCESS_SECRET=<min 32 karakter, berbeda>
JWT_REFRESH_SECRET=<min 32 karakter, berbeda>
R2_ENDPOINT=https://<account>.r2.cloudflarestorage.com
R2_ACCESS_KEY_ID=<...>
R2_SECRET_ACCESS_KEY=<...>
R2_BUCKET=<...>
R2_SIGNED_URL_TTL_SECONDS=300
MAX_UPLOAD_BYTES=104857600
LOG_LEVEL=info
```

`NODE_ENV=production` sekarang **menolak start** bila JWT secret kosong/terlalu pendek,
kedua secret sama, atau konfigurasi R2 hanya terisi sebagian.

## 2. Cek kondisi awal (read-only)

```bash
docker compose ls
docker ps --format 'table {{.Names}}\t{{.Image}}\t{{.Status}}\t{{.Ports}}'
docker volume inspect try-pam_eduvark_pgdata --format '{{.Name}} {{.Mountpoint}}'
docker network inspect try-pam_eduvark_internal --format '{{.Name}} {{range .Containers}}{{.Name}} {{end}}'
curl -fsS http://192.168.0.100:3000/health/ready
docker compose -f docker-compose.homelab.yml config --quiet
```

`docker compose -f docker-compose.homelab.yml config` **wajib** sukses dan harus menampilkan
`name: try-pam`, volume `try-pam_eduvark_pgdata`, network `try-pam_eduvark_internal`,
image `eduvark-backend:testing`.

## 3. Backup sebelum migrasi (wajib)

```bash
mkdir -p /opt/eduvark/backups
docker exec eduvark-postgres pg_dump -U eduvark -d eduvark -Fc \
  > /opt/eduvark/backups/eduvark-$(date +%F-%H%M).dump
ls -lh /opt/eduvark/backups | tail -3
```

## 4. Build image

```bash
cd /opt/eduvark/try-pam
git fetch --all
git checkout <commit-disetujui>
docker compose -f docker-compose.homelab.yml build backend
docker image ls eduvark-backend
```

## 5. Migrasi (eksplisit, satu kali per rilis)

```bash
docker compose -f docker-compose.homelab.yml run --rm backend node dist/db/migrate.js
```

`migrate.ts` memakai `pg_advisory_lock`, jadi dua proses migrasi tidak dapat berjalan
bersamaan. Migrasi tidak dijalankan otomatis saat container start.

## 6. Jalankan / update

```bash
docker compose -f docker-compose.homelab.yml up -d
docker compose -f docker-compose.homelab.yml ps
docker logs --tail=100 eduvark-backend
```

## 7. Verifikasi

```bash
curl -fsS http://192.168.0.100:3000/health/ready
curl -fsS https://pam.sequein.my.id/health/ready
curl -fsS https://pam.sequein.my.id/api/v1/health
# endpoint terproteksi harus 401 tanpa token
curl -s -o /dev/null -w '%{http_code}\n' https://pam.sequein.my.id/api/v1/topics
```

Health check hanya membuktikan proses + koneksi database. Verifikasi fungsional minimal
(manual): login admin → daftar akun; login dosen → daftar topik; upload satu materi **kecil**
ke R2 lalu buka signed URL; login mahasiswa → pilih VARK → buka materi.

## 8. Rollback

```bash
# image sebelumnya (tag lain/commit sebelumnya)
docker compose -f docker-compose.homelab.yml up -d --no-deps backend

# hanya jika migrasi baru bermasalah dan sudah disetujui:
docker exec -i eduvark-postgres pg_restore -U eduvark -d eduvark --clean --if-exists \
  < /opt/eduvark/backups/eduvark-<timestamp>.dump
```

Jangan menghapus volume. Jika container dobel, hapus container lama dengan
`docker rm -f <nama>` (bukan volume).

## 9. Migrasi dari dua file Compose lama ke satu file

1. Bandingkan resource lama dan baru:
   ```bash
   docker compose -f docker-compose.db.yml config
   docker compose -f docker-compose.backend.yml config
   docker compose -f docker-compose.homelab.yml config
   ```
2. Pastikan `name: try-pam`, volume `eduvark_pgdata`, network `eduvark_internal`,
   container `eduvark-postgres`/`eduvark-backend`, dan image `eduvark-backend:testing` identik.
3. Backup database (bagian 3).
4. `docker compose -f docker-compose.homelab.yml up -d --remove-orphans`.
5. Bandingkan volume ID/container ID sebelum dan sesudah. Jika ada container lama yang
   tertinggal, hentikan dengan `docker rm -f` **tanpa** menyentuh volume.

## 10. Catatan keamanan

- Port PostgreSQL tidak dipublikasikan; backend hanya publish di `192.168.0.100:3000`.
- Jangan menaruh secret di image, compose, atau dokumen.
- `backend/.env.production` dan `.env` sudah di-`.gitignore` dan harus `chmod 600`.
- Android release hanya memakai `https://pam.sequein.my.id/api/v1/`; override URL dinonaktifkan
  pada build release.
