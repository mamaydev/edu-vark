# EduVARK

Aplikasi pembelajaran IBD berbasis preferensi VARK. Repositori ini berisi dokumentasi analisis/desain dan fondasi implementasi Android + backend.

## Status

**Fase 1–9 selesai; MVP berfungsi dan acceptance otomatis lulus:**

- **Fase 1 — Foundation:** Android Kotlin/Compose/Hilt/Retrofit/Navigation/DataStore; backend Express 5 + TypeScript, health check, error envelope, PostgreSQL client, migration runner, seed admin; CI.
- **Fase 2 — Auth & Account:** login/logout/refresh, JWT + session table, VARK write-once, RBAC, admin account management, reset/deactivate, leave enrollment, audit, dan UI login/ganti password/VARK/home.
- **Fase 3 — Topics & Materials:** CRUD topik, publish/unpublish, reorder, soft delete, upload R2 + tautan, filter VARK, signed URL, dan UI topik/materi untuk mahasiswa/dosen.
- **Fase 4 — Feedback:** slider V/A/R 0–100, upsert unik mahasiswa–topik–kategori, kategori terpisah, “belum feedback” ≠ 0%, dan UI feedback.
- **Fase 5 — Kinesthetic:** studi kasus 1:1, deadline WIB, submission sekali 1–10.000 karakter, validasi deadline atomik, dan status pengumpulan.
- **Fase 6 — Penilaian:** daftar submission Kinesthetic, detail jawaban, nilai final 0–100 + komentar, immutable, dan hasil langsung ke mahasiswa.
- **Fase 7 — Analitik:** mean/n/histogram feedback, filter kategori/mahasiswa, belum feedback terpisah, dan metrik Kinesthetic terpisah.
- **Fase 8 — Hardening:** acceptance 13/13, 74 backend test, 22 unit + 2 instrumented test, emulator API 37, bug trigger materi diperbaiki.
- **Fase 9 — Admin UI:** daftar/filter/pagination pengguna, tambah pengguna, detail, edit role, reset password, nonaktifkan akun, dan leave/reactivate enrollment; 26 unit test + verifikasi emulator.

**Deployment homelab aktif:** backend berjalan di LXC 10100 (`192.168.0.100:3000`), PostgreSQL 17 lokal, Cloudflare R2 untuk materi, dan ingress Traefik LXC 10080 + Cloudflare Tunnel pada **https://pam.sequein.my.id**. Endpoint `/health/ready` terverifikasi 200.

Sisa production readiness: verifikasi E2E R2 (upload/presign/delete), load test, release signing, dan uji perangkat–backend.

## Struktur

```text
app/                  Android (Kotlin + Compose)
backend/              Express + TypeScript
docs/                 analisis, desain, diagram
docs/design/          API contract, DDL, folder, implementation plan
docker-compose.yml             PostgreSQL lokal (development)
docker-compose.production.yml  backend saja + PostgreSQL eksternal
docker-compose.homelab.yml     stack homelab LXC (PostgreSQL + backend)
DEPLOY-PROXMOX.md              runbook deployment LXC 10100
```

## Android

Prasyarat: JDK 17+ dan Android SDK 37. Jika memakai CLI tanpa Java default:

```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
export PATH="$JAVA_HOME/bin:$PATH"
./gradlew :app:assembleDebug :app:testDebugUnitTest
```

Base URL per build type:

| Build | API Base URL | Override runtime |
|---|---|---|
| debug | `http://10.0.2.2:3000/api/v1/` (emulator) | ya, layar **Pengaturan server (debug)** |
| release | `https://pam.sequein.my.id/api/v1/` | tidak (dikunci saat build) |

Build Variant: **appDebug**. Pada API 36+, izinkan prompt **local network** agar emulator dapat menghubungi host; permission ini hanya ada di debug.  
Untuk **HP fisik** dengan backend lokal, buka **Pengaturan server (debug)** dan isi `http://<IP-LAN-Mac>:3000/api/v1/` (atau pakai APK release yang menunjuk domain publik).  
Jika `R2_*` kosong di `.env`, backend development memakai storage lokal `backend/storage/` agar upload bisa diuji; produksi tetap wajib R2.

## Backend

```bash
docker compose up -d
cd backend
npm install
cp .env.example .env
npm run db:migrate
npm run db:seed-admin
npm run dev
```

Health check:

```bash
curl http://localhost:3000/health
curl http://localhost:3000/api/v1/health
```

Tes:

```bash
cd backend
npm run typecheck
npm run lint
npm test            # butuh DATABASE_URL berakhiran _test
```

Integration test melakukan `TRUNCATE`; helper menolak berjalan bila nama database
tidak berakhiran `_test` (atau tanpa `ALLOW_TEST_DB_RESET=1`). Contoh aman:

```bash
createdb eduvark_test   # atau di container postgres
DATABASE_URL=postgresql://eduvark:eduvark@localhost:5432/eduvark_test npm run db:migrate
DATABASE_URL=postgresql://eduvark:eduvark@localhost:5432/eduvark_test npm test
```

## Dokumentasi

- [SRS Completion](docs/srs/SRS_EDUVARK_V0.7_COMPLETION.md)
- [Requirements Traceability Matrix](docs/srs/REQUIREMENTS_TRACEABILITY_MATRIX.md)
- [Open Questions](docs/srs/OPEN_QUESTIONS.md)
- [Desain Teknis](docs/design/README.md)
- [API Contract](docs/design/API_CONTRACT.md)
- [DDL](docs/design/DATABASE_SCHEMA_DRAFT.sql)
- [Implementation Plan](docs/design/IMPLEMENTATION_PLAN.md)
- [Phase 1 Report](docs/implementation/PHASE_1_REPORT.md)
- [Phase 2 Report](docs/implementation/PHASE_2_REPORT.md)
- [Phase 3 Report](docs/implementation/PHASE_3_REPORT.md)
- [Phase 4 Report](docs/implementation/PHASE_4_REPORT.md)
- [Phase 5 Report](docs/implementation/PHASE_5_REPORT.md)
- [Phase 6 Report](docs/implementation/PHASE_6_REPORT.md)
- [Phase 7 Report](docs/implementation/PHASE_7_REPORT.md)
- [Phase 8 Report](docs/implementation/PHASE_8_REPORT.md)
- [Phase 9 Report](docs/implementation/PHASE_9_REPORT.md)
- [Material Upload Fix](docs/implementation/MATERIAL_UPLOAD_FIX.md)
- [Physical Device Server Config](docs/implementation/PHYSICAL_DEVICE_SERVER_CONFIG.md)
- [Password Policy Change](docs/implementation/PASSWORD_POLICY_CHANGE.md)
- [Login Network Fix](docs/implementation/LOGIN_NETWORK_FIX.md)
- [Acceptance Matrix](docs/acceptance/ACCEPTANCE_MATRIX.md)

## Deployment

Panduan lengkap: [DEPLOY-PROXMOX.md](DEPLOY-PROXMOX.md). Ringkas:

```bash
# di LXC 10100, /opt/eduvark/try-pam
cp .env.homelab.example .env        # isi POSTGRES_PASSWORD
chmod 600 .env backend/.env.production
docker compose -f docker-compose.homelab.yml build backend
docker compose -f docker-compose.homelab.yml run --rm backend node dist/db/migrate.js
docker compose -f docker-compose.homelab.yml up -d
curl -fsS https://pam.sequein.my.id/health/ready
```

## Production readiness

- Verifikasi E2E R2: upload, presign, delete (belum diuji).
- Load test target OQ-05 setelah disetujui.
- Release signing (keystore) dan distribusi.
- E2E aplikasi ↔ backend publik di perangkat.
- Backup terjadwal `pg_dump` + uji restore.
