# Implementation Plan — EduVARK v0.7

**Status:** Rencana eksekusi. **Fase 1–9 sudah selesai** (lihat [Phase 1](../implementation/PHASE_1_REPORT.md), [Phase 2](../implementation/PHASE_2_REPORT.md), [Phase 3](../implementation/PHASE_3_REPORT.md), [Phase 4](../implementation/PHASE_4_REPORT.md), [Phase 5](../implementation/PHASE_5_REPORT.md), [Phase 6](../implementation/PHASE_6_REPORT.md), [Phase 7](../implementation/PHASE_7_REPORT.md), [Phase 8](../implementation/PHASE_8_REPORT.md), [Phase 9](../implementation/PHASE_9_REPORT.md)).  
**Sumber:** SRS v0.7, D-01–D-18, RTM, §6, API contract, DDL draft.  
**Prinsip:** setiap fase harus lulus gate sebelum fase berikutnya; tidak ada klaim selesai tanpa bukti eksekusi test.

## 1. Urutan fase dan dependensi

```text
Fase 0  Desain final & persetujuan
   ↓
Fase 1  Foundation (Android + backend + DB + CI)
   ↓
Fase 2  Auth, sesi, RBAC, manajemen akun
   ↓
Fase 3  Topik, materi, storage, akses mahasiswa
   ↓
Fase 4  Feedback V/A/R
   ↓
Fase 5  Studi kasus Kinesthetic + submission + deadline
   ↓
Fase 6  Penilaian dosen + status
   ↓
Fase 7  Analitik
   ↓
Fase 8  Hardening, acceptance, dokumentasi
```

**Dependensi wajib:**

- Akun + enrollment + teaching assignment sebelum topik/materi.
- Topik sebelum materi/studi kasus.
- Materi sebelum feedback.
- Studi kasus sebelum submission.
- Submission sebelum penilaian.
- Penilaian sebelum analitik Kinesthetic.
- Auth/sesi sebelum semua endpoint akademik.

## 2. Fase

### Fase 0 — Desain final & persetujuan

**Deliverable:**

- API contract, DDL draft, struktur folder, rencana implementasi (dokumen ini).
- Keputusan P0 dikunci (D-15–D-18).
- Review terbuka: OQ-03–OQ-06, OQ-08–OQ-14.

**Gate:** Anda menyetujui paket desain dan memutuskan minimal OQ-03/OQ-04/OQ-05/OQ-08/OQ-09/OQ-11 sebelum fase yang membutuhkannya. Tidak ada kode pada fase ini.

### Fase 1 — Foundation

**Android:**

- Rename proyek/package ke EduVARK/`com.eduvark.app`.
- Setup Hilt, Retrofit/OkHttp, Kotlinx Serialization, Navigation Compose, tema Material 3.
- Skeleton `UiState`, `UiError`, `SessionStore`, network module.
- Build debug/release; minSdk 24.

**Backend:**

- Express + TypeScript strict, struktur module, error middleware, request id, logger, health check.
- Koneksi PostgreSQL/Supabase, migration runner, seed admin (OQ-09).
- Docker Compose lokal (Postgres; MinIO opsional untuk R2-compatible).
- `.env.example`, tanpa kredensial hardcoded.

**CI:**

- Android build + unit test.
- Backend lint/typecheck/unit test.
- Migration check pada database sementara.

**Gate:** build Android sukses; `GET /health` 200; migration berjalan di DB bersih; lint/typecheck hijau.

### Fase 2 — Auth, sesi, RBAC, akun

**FR:** FR-001–FR-008, FR-034–FR-037.  
**AT:** AT-10; usulan AT-P01, AT-P02, AT-P09, AT-P11, AT-P13.

**Backend:**

- Login/logout/refresh/me/change-password.
- JWT access 15m, refresh 12h rotating, session table, single active session.
- VARK write-once; `428 VARK_REQUIRED` untuk endpoint akademik.
- RBAC middleware; admin user CRUD, reset, deactivate, last-admin guard.
- Enrollment leave endpoint (D-17).

**Android:**

- Login screen, change-password, VARK selection, role-based navigation.
- Session interceptor/authenticator; 401 logout lokal.
- Admin account list/create/update/reset/deactivate.

**Gate:** matriks RBAC lulus; sesi lama dicabut saat login baru; VARK lock; akun nonaktif ditolak; admin tidak bisa menyentuh data akademik.

### Fase 3 — Topik, materi, storage, akses

**FR:** FR-009–FR-017.  
**AT:** AT-01, AT-04; usulan AT-P03, AT-P04, AT-P12.

**Backend:**

- CRUD topik, reorder, publish/unpublish, soft delete.
- Upload file ke R2 + link; validasi MIME/magic bytes/ukuran.
- Metadata materi; signed URL 5 menit; filter kategori sesi.
- Pesan persis FR-016; pesan “studi kasus belum tersedia”.

**Android:**

- Lecturer topic/material screens.
- Student topic list + material viewer (PDF/audio/video/image/link).
- Empty/error/loading states.

**Gate:** akses kategori lain 403; topik tanpa materi menampilkan pesan persis; file invalid/terlalu besar ditolak; soft delete tidak menghapus data akademik.

### Fase 4 — Feedback

**FR:** FR-018–FR-022.  
**AT:** AT-05, AT-06; usulan AT-P05.

**Backend:**

- GET/PUT feedback; upsert unique triple; nilai 0–100.
- Hanya V/A/R dan materi tersedia.
- “Belum feedback” = null, bukan 0.

**Android:**

- Slider 0–100, nilai terakhir, status “belum ada feedback”.
- Simpan ulang memperbarui nilai.

**Gate:** unique triple; kategori lain tidak berubah; Kinesthetic 403; input invalid 422; feedback orang lain 403.

### Fase 5 — Kinesthetic: studi kasus, submission, deadline

**FR:** FR-023–FR-025, FR-038–FR-041, FR-045–FR-050.  
**AT:** AT-07, AT-09, AT-13, AT-14, AT-15, AT-16; usulan AT-P06.

**Backend:**

- Upsert studi kasus 1:1; deadline UTC; lock setelah submission pertama.
- Submission text 1–10.000; no draft; unique pair.
- Validasi deadline atomik dengan `now()` database; `SELECT ... FOR UPDATE`.
- Status `Belum`/`Tidak Mengumpulkan`/`Menunggu`.

**Android:**

- Case detail + deadline WIB; text editor tanpa autosave; tombol Kirim.
- Konfirmasi final; tidak ada tombol revisi.
- Status hasil.

**Gate:** dua request paralel → satu 201, satu 409; tepat deadline diterima; +1 ms ditolak; tidak ada draft; `Tidak Mengumpulkan` tanpa nilai 0.

### Fase 6 — Penilaian dosen dan finalisasi

**FR:** FR-026–FR-028, FR-042–FR-044.  
**AT:** AT-08, AT-11, AT-12, AT-17; usulan AT-P07, AT-P10.

**Backend:**

- List submission + status.
- Insert assessment unique; score 0–100; comment opsional ≤1.000.
- Audit dalam transaksi; trigger immutability.
- Student melihat hasil segera.

**Android:**

- Lecturer grading screen; konfirmasi final; read-only setelah simpan.
- Student result screen; nilai/komentar.

**Gate:** penilaian kedua 409; update/delete nilai 405/409; admin 403; teks submission tidak berubah saat penilaian gagal.

### Fase 7 — Analitik

**FR:** FR-029–FR-033.  
**AT:** usulan AT-P08.

**Backend:**

- Agregasi feedback terbaru per mahasiswa–topik–kategori.
- Mean, n, histogram; `notRespondedCount` terpisah.
- Kinesthetic mean terpisah; tidak digabung.

**Android:**

- Grafik + definisi agregasi + n.
- Filter kategori/mahasiswa.
- Empty state.

**Gate:** belum feedback ≠ 0%; Kinesthetic terpisah; data dosen kelas lain 403.

### Fase 8 — Hardening dan acceptance

- Regression AT-01–AT-17 + AT-P01–AT-P13.
- Security: rate limit, password hashing, token revocation, upload validation, error redaction.
- Performance smoke: target OQ-05 (jika disetujui).
- Dokumentasi pengguna singkat.
- Traceability final: FR → test → evidence.
- **Gate:** seluruh acceptance test lulus dengan bukti eksekusi; tidak ada GAP kritis.

### Fase 9 — Admin Android UI (post-acceptance)

**FR:** FR-034–FR-037, D-17.  
**Tujuan:** melengkapi UI admin yang sebelumnya hanya halaman selamat datang.

**Android:**

- Menu admin di Home.
- Daftar/filter/pagination pengguna.
- Tambah pengguna + validasi password sementara.
- Detail/edit role/nama, reset password, deaktivasi.
- Leave/reactivate enrollment.

**Backend:**

- Response admin menyertakan kelas, status enrollment, dan kelas yang diampu.

**Gate:** daftar/detail/leave enrollment terverifikasi di emulator; create/reset/deactivate lulus test backend dan unit test Android.

## 3. Strategi pengujian

### 3.1 Backend

- Unit test service untuk aturan bisnis.
- Integration test route dengan Supertest.
- Database integration dengan Testcontainers PostgreSQL.
- Concurrency test: dua submission, dua assessment, login ganda.
- Auth/RBAC matrix test.
- Validation test Zod + DB constraint.
- Storage test dengan bucket R2-compatible (MinIO) atau mock.

### 3.2 Android

- Domain use case test (JVM murni).
- ViewModel test dengan MockK + Turbine.
- Repository test dengan MockWebServer.
- Compose UI test untuk form/error/empty.
- Navigation test role + VARK gate.

### 3.3 Business rule tests wajib

- Pergantian VARK setelah login ulang.
- Penolakan perubahan VARK dalam sesi.
- Penolakan akses kategori lain.
- Pesan materi belum tersedia.
- Feedback terbaru; pemisahan kategori.
- Panjang 1/10.000; 0/10.001.
- Deadline tepat/+1 ms.
- Submission ganda/paralel.
- Perubahan jawaban final.
- Penilaian sekali; perubahan nilai final.
- `Tidak Mengumpulkan` bukan 0.
- RBAC tiga role.
- Admin tidak menyentuh akademik.
- Login/logout staf (D-18).
- Password sementara/reset (D-16).
- Leave class (D-17).

### 3.4 Data uji

- Database terisolasi; factory; reset antar-test.
- Tidak memakai data produksi.
- Seed admin hanya untuk test/dev.

## 4. Risiko dan mitigasi

| Risiko | Dampak | Mitigasi |
|---|---|---|
| Deadline race | Submission terlambat tersimpan | Transaksi + `now()` DB + row lock + unique |
| Submission ganda | Dua jawaban | Unique pair; 409 |
| Penilaian ganda | Dua nilai | Unique submission; 409 |
| VARK bypass | Akses kategori lain | Session server + cek backend; signed URL |
| Token revocation | Sesi lama tetap aktif | Session table + `is_active` + revoke |
| Supabase scope | Integrasi auth ambigu | D-15: hanya Postgres; auth custom |
| R2 outage | Upload gagal | 502; tidak ada metadata setengah jadi |
| Soft delete | Data tersembunyi tapi ada | Filter `deleted_at`; partial unique |
| Reorder topik | Konflik sort_order | Transaksi dua fase |
| analytics misleading | Subjektif dianggap objektif | Metrik terpisah + label |

## 5. Definition of Done per fase

- Kode + test + dokumentasi traceability.
- Tidak ada TODO kritis.
- Error/empty/loading state.
- Tidak ada hardcoded credential.
- Validasi backend, bukan hanya UI.
- Gate test dijalankan; bukti output disimpan.
- Review diff sebelum merge.

## 6. Yang belum diputuskan (tidak menghambat Fase 1)

- OQ-03 backup/purge (menghambat operasi produksi).
- OQ-04 akses audit (menghambat UI audit).
- OQ-05 target performa (menghambat acceptance nonfungsional).
- OQ-06 ekspor laporan (scope).
- OQ-08 dosen penilai (menghambat aturan penilaian final jika multi-dosen).
- OQ-09 bootstrap admin (menghambat deployment).
- OQ-10 copy UI (menghambat UI final).
- OQ-11 MIME/ukuran per tipe (menghambat validasi upload final).
- OQ-12 konvensi API (menghambat kontrak final).
- OQ-13 offline/push/email (scope).
- OQ-14 privasi analytics (menghambat UI analytics).

> Fase 1 dapat dimulai setelah persetujuan paket desain; fase yang membutuhkan OQ tertentu menunggu keputusan tersebut.
