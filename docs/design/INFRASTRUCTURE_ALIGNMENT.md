# Penyelarasan Infrastruktur EduVARK (Homelab)

Dokumen ini mencatat audit dan perubahan yang menyelaraskan repository dengan deployment
homelab: Android → Cloudflare → Traefik (LXC 10080) → backend LXC 10100 → PostgreSQL/R2.

**Tanggal audit:** 22 November 2026. **Domain publik:** `https://pam.sequein.my.id`.

## Arsitektur aktual

| Lapisan | Implementasi |
|---|---|
| Client | Android Compose; debug → emulator `10.0.2.2:3000`, release → `https://pam.sequein.my.id/api/v1/` |
| Edge | Cloudflare DNS + Tunnel |
| Ingress | LXC 10080, Traefik v3, dynamic file provider |
| Backend | LXC 10100, `eduvark-backend:testing`, Express 5 / Node 24, port 3000 |
| Database | PostgreSQL 17 container, volume `try-pam_eduvark_pgdata`, network `try-pam_eduvark_internal`, port tidak dipublikasi |
| Storage | Cloudflare R2 + signed URL (TTL 300 s) |

Traceability lengkap ada di [UI redesign](UI_REDESIGN.md) dan
[DEPLOY-PROXMOX.md](../../DEPLOY-PROXMOX.md).

## Perubahan yang diterapkan

| Area | File | Perubahan |
|---|---|---|
| Android base URL | `app/build.gradle.kts` | release = `https://pam.sequein.my.id/api/v1/`; flag `ALLOW_API_BASE_URL_OVERRIDE` (true hanya debug) |
| Android override | `BaseUrlInterceptor.kt` | override runtime hanya berlaku bila build mengizinkan |
| Refresh token | `TokenAuthenticator.kt` | single-flight `Mutex`; 401 paralel tidak menghabiskan refresh token yang sudah dirotasi |
| Timeout | `NetworkModule.kt` | write 300 s / read 120 s untuk transfer materi, `retryOnConnectionFailure` |
| Lint/CI | `res/values-v27/themes.xml` | `windowLightNavigationBar` dipindah ke kualifikasi API 27 |
| Proxy | `backend/src/app.ts` | `trust proxy` hanya untuk alamat privat (loopback/linklocal/uniquelocal) |
| CORS | `backend/src/app.ts`, `config/env.ts` | default menolak semua origin; origin eksplisit via `CORS_ORIGINS` |
| Rate limit | `lib/rate-limit.ts`, `app.ts` | batas kasar 1.800 request / 5 menit / IP untuk `/api/v1` |
| Log | `lib/logger.ts` | redaksi `authorization`, `cookie`, `set-cookie`, token/signature query |
| Throttle login | `login-throttle.ts`, `auth.service.ts` | 5 kegagalan / identitas dan 20 kegagalan / IP per 15 menit (bukan lagi global per IP) |
| Env | `config/env.ts` | produksi wajib dua JWT secret berbeda (≥32 karakter) dan R2 lengkap atau kosong |
| TTL | `lib/tokens.ts`, `auth.service.ts`, `middleware/auth.ts` | `ACCESS_TOKEN_TTL`, `REFRESH_TOKEN_TTL`, `SESSION_IDLE_TTL`, `SESSION_ABSOLUTE_TTL` benar-benar dipakai |
| Upload | `topics.routes.ts` | `files: 1`, `fields: 2`, `parts: 4` (batas memori tetap 100 MB sesuai D-05) |
| Migrasi | `db/migrate.ts` | `pg_advisory_lock` mencegah migrasi paralel |
| Test DB | `tests/helpers/db.ts` | menolak `TRUNCATE` bila database bukan `*_test` |
| Compose | `docker-compose.homelab.yml` | satu stack PostgreSQL + backend, nama resource dipin agar volume/network lama dipakai ulang; database/user dipin `eduvark/eduvark` (bukan dari `.env`) |
| Dokumentasi | `DEPLOY-PROXMOX.md`, `README.md` | runbook baru sesuai LXC 10100 + Traefik + Cloudflare Tunnel |
| CI | `.github/workflows/ci.yml` | cache Gradle, `assembleRelease`, build image backend, database test `eduvark_test` |

## Keputusan & catatan

- **PostgreSQL self-hosted vs SRS:** SRS v0.7 (D-02/D-15) menyebut Supabase sebagai managed
  PostgreSQL. Deployment homelab memakai PostgreSQL 17 self-hosted (engine yang sama, data
  tetap PostgreSQL). Perbedaan ini adalah keputusan infrastruktur, bukan perubahan aturan
  bisnis; dokumen SRS tidak diubah. Jika diinginkan, tambahkan ADR resmi di `docs/design/`.
- **CORS:** aplikasi Android tidak memerlukan CORS. Origin hanya diizinkan bila
  `CORS_ORIGINS` diisi (mis. untuk kebutuhan web di masa depan).
- **`trust proxy`:** daftar alamat privat dipakai agar `X-Forwarded-For` dari internet tidak
  bisa dipalsukan; `req.ip` menjadi IP klien sebenarnya di balik Traefik/Cloudflare.
- **R2:** helper upload/presign/delete sudah ada, tetapi bukti end-to-end (upload → signed URL
  → delete) belum dijalankan karena kredensial hanya ada di server. `/health/ready` hanya
  memeriksa database.
- **Retensi R2:** soft delete materi tidak menghapus objek R2. Kebijakan retensi (D-13) perlu
  lifecycle rule di bucket atau job pembersihan terpisah.
- **Migrasi tidak otomatis:** migration tetap dijalankan eksplisit (`run --rm backend node
  dist/db/migrate.js`), tidak saat container start.

## Verifikasi yang sudah dijalankan

- `backend`: typecheck, lint, unit 20/20, integration 81/81 pada database disposable
  `eduvark_test` (lalu dihapus).
- Android: `compileDebugKotlin`, `compileReleaseKotlin`, `testDebugUnitTest`, `lintDebug`.
- `docker compose -f docker-compose.homelab.yml config` (dengan env dummy sementara) →
  resource name terverifikasi.
- Publik: `/health/ready` dan `/api/v1/health` 200; endpoint terproteksi 401 tanpa token.

## Risiko tersisa

1. R2 E2E belum dibuktikan.
2. HTTP port 80 belum redirect ke HTTPS (aktifkan “Always Use HTTPS” di Cloudflare).
3. Token JWT masih tersimpan plaintext di DataStore (backup sudah dikecualikan); enkripsi
   Keystore belum diterapkan.
4. Release build belum memakai R8/minify dan belum ada signing/rilis.
5. Belum ada backup terjadwal + uji restore otomatis.
6. Rate limit & login throttle masih in-memory (hilang saat restart, satu instance).
