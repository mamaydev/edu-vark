# Phase 8 Report — Hardening & Acceptance

**Tanggal:** 20 November 2026  
**Status:** Selesai; 13/13 gate AT-P lulus dengan bukti eksekusi.  
**Lingkup:** hardening keamanan, regression, acceptance AT-P01–AT-P13, uji emulator, dan traceability akhir.

## 1. Gate Fase 8

| Gate | Target | Hasil |
|---|---|---|
| Acceptance AT-P01–P13 | Semua lulus | **13/13 PASS** |
| Regression backend | Semua test hijau | **74 test lulus** |
| Android unit | Semua test hijau | **22 test lulus** |
| Android instrumented | Berjalan di emulator | **2/2 lulus (API 37)** |
| Security audit | Tidak ada vulnerability produksi | **0 vulnerability** |
| DDL | Apply + trigger materi | **MATERIAL_UPDATED_AT_OK** |
| App launch | Tidak crash | **Lulus; login screen tampil** |

## 2. Hardening yang dikerjakan

### 2.1 Bug kritis yang ditemukan

- **Trigger `trg_materials_updated` memanggil `NEW.updated_at` yang tidak ada.**
  - Dampak: `DELETE /lecturer/materials/:id` dan update materi menghasilkan **500**; soft delete materi tidak berjalan.
  - Perbaikan: kolom `materials.updated_at timestamptz NOT NULL DEFAULT now()` ditambahkan ke migration dan DDL draft.
  - Bukti: test hapus materi lulus; DDL revalidasi menghasilkan `MATERIAL_UPDATED_AT_OK`.

### 2.2 Keamanan

- Token sesi (access/refresh) tidak lagi ikut Android backup/cloud restore/device transfer.
  - `backup_rules.xml` dan `data_extraction_rules.xml` mengecualikan `datastore/eduvark_session.preferences_pb`.
- Batas upload tidak lagi hardcode 100 MB; mengikuti `MAX_UPLOAD_BYTES` dari env.
- `npm audit --omit=dev`: **0 vulnerability** (info/low/moderate/high/critical).
- Helmet, body limit 1 MB, validasi magic-byte, Argon2id, JWT 32+ karakter, dan rate limit login tetap aktif.

### 2.3 CI

- Android CI sekarang menjalankan `assembleDebug`, unit test, `lintDebug`, dan `assembleDebugAndroidTest`.
- Backend CI sudah menjalankan typecheck, lint, migrate, test, dan build.

## 3. Acceptance

Lihat [Acceptance Matrix](../acceptance/ACCEPTANCE_MATRIX.md) untuk pemetaan per gate.

| Gate | Status |
|---|---|
| AT-P01–AT-P13 | **13/13 PASS** |

Bukti utama:

- Backend: **74 test** di 13 file (termasuk rollback audit, upload 413, staff logout, admin submission 403).
- Android: **22 unit test** + **2 instrumented test** di emulator Pixel 10 Pro XL API 37.
- APK debug diinstal, aplikasi dibuka, login screen tampil tanpa crash.
- Screenshot smoke test tersimpan di [login-screen.png](../acceptance/login-screen.png).

## 4. Batasan yang tersisa

- **R2/MinIO nyata belum diuji**; test upload memakai mock storage.
- **Target performa OQ-05 masih TBD**; belum ada load test 100 pengguna.
- **Release API base URL masih placeholder** (`https://api.eduvark.example`); signing/Play distribution belum.
- **Belum ada E2E penuh aplikasi ↔ backend nyata** di perangkat.
- Instrumented UI test masih terbatas pada login screen.
- Tidak ada ekspor PDF/Excel/CSV (OQ-06).

## 5. Kesimpulan

MVP EduVARK v0.7 selesai secara fungsional dan seluruh gate acceptance otomatis lulus. Temuan kritis pada trigger materi sudah diperbaiki dan diverifikasi. Sisa pekerjaan bersifat production readiness (R2 nyata, load test, signing/rilis, E2E perangkat–backend), bukan gap aturan bisnis.
