# Phase 2 Report — Auth, Session, RBAC & Account Management

**Tanggal:** 20 November 2026  
**Status:** Selesai secara fungsional dan terverifikasi.  
**Lingkup:** FR-001–FR-008, FR-034–FR-037; D-03, D-04, D-11, D-15–D-18.

## 1. Gate Fase 2

| Gate | Target | Hasil |
|---|---|---|
| Login/logout/refresh | JWT access + refresh + session table | **Lulus** |
| Sesi tunggal | Login baru mencabut sesi lama | **Lulus** (test) |
| VARK write-once | 409 pada pilihan kedua | **Lulus** (test) |
| VARK gate | 428 sebelum pilih VARK | **Lulus** (test middleware) |
| Must-change gate | 403 sebelum ganti password | **Lulus** (test) |
| RBAC | Student tidak bisa akses admin | **Lulus** (test) |
| Admin akun | Create/list/update/reset/deactivate | **Lulus** (test) |
| Last-admin guard | Tidak bisa menonaktifkan self/admin terakhir | **Lulus** (test) |
| Leave class | Status LEFT + re-enroll | **Lulus** (test) |
| Backend typecheck/lint/test | Semua hijau | **Lulus; 35 test** |
| Android build/test/lint | Semua hijau | **Lulus; 11 unit test** |
| Compose UI test | Compile | **Lulus compile; belum dijalankan di emulator** |

## 2. Backend

### 2.1 Endpoint

```text
POST   /api/v1/auth/login
POST   /api/v1/auth/refresh
POST   /api/v1/auth/logout
GET    /api/v1/auth/me
POST   /api/v1/auth/change-password
POST   /api/v1/auth/vark

GET    /api/v1/admin/users
POST   /api/v1/admin/users
GET    /api/v1/admin/users/:id
PATCH  /api/v1/admin/users/:id
POST   /api/v1/admin/users/:id/reset-password
POST   /api/v1/admin/users/:id/deactivate
PATCH  /api/v1/admin/enrollments/:id
```

### 2.2 Implementasi

- JWT access 15 menit; refresh opaque 12 jam; rotasi refresh.
- Session table: idle 8 jam, absolut 12 jam; satu sesi aktif per user.
- Password Argon2id; minimal 12 karakter; blokir password umum.
- Login throttle: 5 kegagalan / 15 menit (in-memory, single instance).
- VARK write-once; middleware `requireVark` (428).
- Middleware `requirePasswordChanged` (403).
- RBAC `requireRole`.
- Admin user CRUD, reset password, deactivate, last-admin guard.
- Role change membersihkan enrollment/teaching assignment lama dan menyiapkan yang baru.
- Enrollment leave/re-enroll.
- Audit: login/logout, VARK, perubahan akun, reset, deactivation, enrollment.
- Error envelope dan request id tetap konsisten.

### 2.3 Bukti

```text
npm run typecheck -> lulus
npm run lint      -> lulus
npm test          -> 7 files, 35 tests passed
  - unit: password 3, tokens 3, env 5, throttle 2
  - integration: health 3, auth 10, admin 9

Integration di PostgreSQL disposable:
  - migration 001_init.sql applied
  - auth: login, invalid, inactive, refresh rotation, logout,
    VARK lock, VARK gate, must-change gate, change-password
  - admin: create, duplicate, role change, reset, deactivate,
    last-admin, RBAC, leave/re-enroll, no-active-class
```

## 3. Android

### 3.1 Implementasi

- DTO auth + error envelope.
- Retrofit auth endpoints + refresh API terpisah.
- `TokenAuthenticator` untuk 401 → refresh → retry.
- `SessionStore` menyimpan token, user, role, active VARK, must-change.
- `AuthRepository` + use case login/logout/me/change-password/select-VARK.
- Login screen (NIM/NIP + password).
- Change password screen (wajib saat password sementara).
- VARK selection screen (4 kategori, dikunci setelah dipilih).
- Home role-aware: profil, VARK aktif, status backend, logout.
- Navigation: login → change-password/VARK/home; logout → login.

### 3.2 Bukti

```text
./gradlew :app:assembleDebug :app:testDebugUnitTest -> BUILD SUCCESSFUL
  Android unit tests: 11
    - VarkTypeTest 3
    - LoginViewModelTest 3
    - ChangePasswordViewModelTest 3
    - VarkSelectionViewModelTest 2

./gradlew :app:lintDebug -> BUILD SUCCESSFUL
./gradlew :app:assembleDebugAndroidTest -> BUILD SUCCESSFUL
  LoginScreenTest dikompilasi; belum dijalankan di emulator.
```

## 4. Database

- Migration tidak berubah dari Fase 1; auth memakai tabel `users`, `auth_sessions`, `audit_logs`.
- Seed admin sekarang juga memastikan kelas aktif `IBD-01`.
- Bukti seed:
  ```text
  classes: IBD-01 | is_active = true
  users:   admin   | ADMIN   | must_change_password = true
  ```

## 5. Catatan dan batasan

- Belum ada emulator/device run; UI dan refresh authenticator belum diuji end-to-end di perangkat.
- Login throttle masih in-memory; untuk multi-instance produksi perlu Redis/shared store.
- `GET /auth/me` dan endpoint admin mengembalikan data dari server; tidak ada cache lokal selain DataStore sesi.
- Admin role change tidak menghapus histori enrollment; enrollment lama di-set `LEFT`.
- Belum ada endpoint materi/topik; `requireVark` sudah siap dipakai Fase 3.
- CI GitHub belum dijalankan pada sesi ini; workflow sudah diperbarui (migrate sebelum test).

## 6. Langkah berikutnya

Fase 3 — topik, materi, storage, dan akses mahasiswa:

- CRUD topik + reorder/publish/unpublish/soft delete.
- Upload materi R2 + validasi MIME/ukuran.
- Akses materi berdasarkan VARK sesi; pesan persis FR-016.
- `requireVark` diterapkan pada endpoint akademik.
- Android: lecturer topic/material screens, student topic/material viewer.
