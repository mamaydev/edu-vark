# Phase 1 Report — Foundation

**Tanggal:** 20 November 2026  
**Status:** Selesai secara fungsional; **bukan** implementasi fitur bisnis.  
**Lingkup:** Android foundation, backend foundation, database migration, seed admin, CI.

## 1. Gate Fase 1

| Gate | Target | Hasil |
|---|---|---|
| Android build | `assembleDebug` sukses | **BUILD SUCCESSFUL** |
| Android unit test | `testDebugUnitTest` sukses | **BUILD SUCCESSFUL** (VarkTypeTest, 3 test) |
| Android lint | `lintDebug` sukses | **BUILD SUCCESSFUL** |
| Backend typecheck | `tsc --noEmit` | **Lulus** |
| Backend lint | ESLint | **Lulus** |
| Backend test | Vitest | **8 test lulus** (2 file) |
| Backend build | `tsc` + copy migrations | **Lulus** |
| Migration | Apply ke DB bersih | **001_init.sql applied** |
| Seed admin | Buat admin pertama | **admin / ADMIN / must_change_password=true** |
| Health check | `/health` + `/api/v1/health` | **200 OK** |
| Readiness | `/health/ready` dengan DB | **200 OK** |
| Error envelope | 404 konsisten | **ROUTE_NOT_FOUND + requestId** |

## 2. Android

### 2.1 Yang diimplementasikan

- Rename proyek/package: **EduVARK** / `com.eduvark.app`.
- Hilt (`@HiltAndroidApp`, `@AndroidEntryPoint`, `@HiltViewModel`, `@Module`).
- Retrofit + OkHttp + Kotlinx Serialization.
- Navigation Compose (`login` → `foundation`).
- DataStore `SessionStore`.
- `UiState` / `UiError` / error mapping.
- Health check end-to-end: Compose → ViewModel → UseCase → Repository → Retrofit.
- Theme Material 3, strings, manifest, debug cleartext.
- Unit test `VarkTypeTest`.

### 2.2 Versi utama

| Komponen | Versi |
|---|---|
| AGP | 9.4.1 |
| Gradle | 9.6.0 |
| Kotlin | 2.2.10 |
| KSP | 2.2.10-2.0.2 |
| Hilt | 2.60.1 |
| Compose BOM | 2026.02.01 |
| Navigation Compose | 2.10.2 |
| Retrofit | 2.11.0 |
| OkHttp | 4.12.0 |
| DataStore | 1.3.0 |

### 2.3 Bukti

```text
./gradlew :app:assembleDebug :app:testDebugUnitTest
BUILD SUCCESSFUL

./gradlew :app:lintDebug
BUILD SUCCESSFUL

./gradlew :app:assembleDebugAndroidTest
BUILD SUCCESSFUL (compile only; no emulator run)

APK: app/build/outputs/apk/debug/app-debug.apk
package: com.eduvark.app.debug
label: EduVARK
minSdk: 24
targetSdk: 37
permission: android.permission.INTERNET
```

### 2.4 Catatan teknis

- Toolchain Kotlin/JVM diset ke **17**.
- AGP 9 memakai built-in Kotlin; KSP memerlukan `android.disallowKotlinSourceSets=false` (dicatat di `gradle.properties`).
- API base URL debug: `http://10.0.2.2:3000/api/v1/`.
- Belum ada emulator/device UI test; belum ada login.

## 3. Backend

### 3.1 Yang diimplementasikan

- Express 5 + TypeScript strict, struktur modular.
- `requestId`, pino HTTP logging, helmet, cors, JSON body limit.
- Error envelope konsisten.
- `GET /health`, `GET /api/v1/health`, `GET /health/ready`.
- `GET /api/v1/` metadata API.
- PostgreSQL pool + env validation.
- Migration runner bertransaksi + `schema_migrations`.
- Seed admin dengan Argon2id.
- Vitest + Supertest.
- ESLint flat config.

### 3.2 Bukti

```text
npm run typecheck -> lulus
npm run lint      -> lulus
npm test          -> 2 files, 8 tests passed
npm ci (clean)    -> exit 0; argon2 + tsx berfungsi

npm run build     -> lulus
dist/db/migrations/001_init.sql tersedia
node dist/server.js + GET /health -> 200

Migration DB bersih:
  001_init.sql applied
  13 tabel (12 domain + schema_migrations)
Seed:
  admin | ADMIN | must_change_password = true
Readiness:
  GET /health/ready -> 200 OK
Compiled artifacts:
  node dist/db/migrate.js + dist/db/seed-admin.js -> OK
```

## 4. Database

- Migration `001_init.sql` dihasilkan dari DDL yang sudah divalidasi.
- 12 tabel domain + `schema_migrations`.
- Trigger immutability, deadline lock, VARK lock, audit append-only, topic hard-delete guard.
- Seed admin pertama berhasil; password di-hash Argon2id; `must_change_password=true`.

## 5. CI

Workflow `.github/workflows/ci.yml`:

- Android: JDK 21, Android SDK 37, `assembleDebug`, `testDebugUnitTest`.
- Backend: Node 24, PostgreSQL 17 service, `npm ci`, typecheck, lint, test, migrate, build.

> Workflow belum dijalankan di GitHub Actions pada sesi ini; verifikasi lokal sudah dilakukan.

## 6. Yang belum ada

- Login/logout, VARK, RBAC, admin endpoints.
- Topik/materi, feedback, Kinesthetic, penilaian, analitik.
- Cloudflare R2 upload.
- Emulator/device UI test.
- Deployment.

## 7. Langkah berikutnya

Fase 2:

- Login/logout/refresh/change-password.
- JWT access 15m + refresh 12h + session table.
- VARK write-once dan `428 VARK_REQUIRED`.
- RBAC middleware.
- Admin user CRUD/reset/deactivate.
- Enrollment leave endpoint.
- Android login/change-password/VARK screens.

## 8. Traceability

- [Implementation Plan](../design/IMPLEMENTATION_PLAN.md)
- [API Contract](../design/API_CONTRACT.md)
- [DDL](../design/DATABASE_SCHEMA_DRAFT.sql)
- [SRS Completion](../srs/SRS_EDUVARK_V0.7_COMPLETION.md)
- [RTM](../srs/REQUIREMENTS_TRACEABILITY_MATRIX.md)
