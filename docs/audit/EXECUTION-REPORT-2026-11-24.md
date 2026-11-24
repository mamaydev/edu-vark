# Laporan Eksekusi Audit Fungsional EduVARK — 24 November 2026

Laporan ini dapat dibaca tanpa riwayat percakapan. Seluruh status **PASS** di bawah hanya
berasal dari perintah yang benar-benar dijalankan pada perubahan ini; yang belum dapat
dibuktikan ditandai **NEEDS VERIFICATION** atau **NOT RUN** (tidak ada klaim palsu).

## 1. Ringkasan

Enam paket pekerjaan sesuai SRS final (SSOT `Dokumen-SRS.docx`, v0.7, revisi 24 Nov 2026,
SHA-256 `a4bdc18928e0be3ba48cf868ecaa919bed6ab72a81cc17696754a39e0dcbe907`) telah
diimplementasikan dan diuji pada working tree:

| Paket | Isi | Status akhir |
| --- | --- | --- |
| P1 | Wajib ganti password + detail jawaban/nilai milik sendiri & enrollment ACTIVE | PASS (backend + unit Android); alur UI perangkat sebagian NEEDS VERIFICATION |
| P2 | Alamat materi tetap + otorisasi ulang per permintaan + buka materi terautentikasi | PASS (backend + unit Android); buka berkas di perangkat NEEDS VERIFICATION |
| P3 | Login NIM/NIDN/ID Admin + alokasi otomatis ID Admin + migrasi 002 | PASS (backend + device login); form admin di perangkat NEEDS VERIFICATION |
| P4 | Analitik feedback berdasarkan kategori asal nilai + agregasi/responden/Kinesthetic | PASS (backend + unit Android); render perangkat NEEDS VERIFICATION |
| P5 | Edit topik, empty state materi, paginasi/reorder, validasi nilai, batas 10.000 code point | PASS (backend + unit + 3 tes device); AT-04/AT-11 render perangkat NEEDS VERIFICATION |
| P6 | Verifikasi silang, uji DB terisolasi, build/lint/typecheck, restore lokal, rehearsal migrasi, rencana rollout/rollback | PASS kecuali butir bertanda NEEDS VERIFICATION (staging/OQ-05, alur UI perangkat yang tidak dijalankan); kontrol klien & rollback pra-002 PASS lokal; **release hold FAIL → cutover NO-GO** |

Ringkasan eksekusi terakhir (semua aktual):

- Backend: `npm test` **26/26 berkas, 219/219 tes PASS**; `npm run typecheck` exit 0;
  `npm run lint` exit 0; `npm run build` exit 0 (log final
  `/tmp/eduvark-final-logs/backend-final.log`).
- Android: `assembleDebug` + `assembleRelease` + `lintDebug` BUILD SUCCESSFUL;
  `testDebugUnitTest` **29 berkas / 132 tes / 0 gagal / 0 error / 0 skip**;
  `connectedDebugAndroidTest` (tanpa kelas E2E screenshot milik PO) **14/14 PASS** di
  emulator `Pixel_10_Pro_XL` (Android 17 / API 37) pada artefak final `versionCode=2`.
- Database terisolasi PostgreSQL 17 (`eduvark-postgres`): semua suite backend dijalankan
  pada DB `eduvark_test_release` yang dibuat ulang dengan migrasi **001+002+003**.
- **Status kesiapan rilis: belum siap rilis.** Kontrol klien lama, rollback pra-002, dan
  sanitasi bukti sudah PASS lokal; **release hold FAIL** dan staging/produksi NOT RUN,
  sehingga merge ke `main`/cutover produksi **NO-GO** (§16.10).
- Simulasi/rehearsal migrasi Admin ID dan rollback dijalankan pada salinan dump lokal
  (bukan produksi). Restore backup lokal terverifikasi end-to-end (skala data dev).

Tidak ada commit/push/PR/merge, tidak ada perubahan pada database produksi, storage
produksi, server produksi, kredensial produksi, `Dokumen-SRS.docx`, `docs/DECISIONS.md`,
RTM, `001_init.sql`, maupun `.github`.

## 2. Baseline dan lingkungan

- Commit baseline audit: `b7b6cd6d2be19c1f0aa93f2b95454049984250d5`
  (`b7b6cd6`), branch `fix/eduvark-functional-audit-2026`.
- Baseline audit: 36 FR ALIGNED / 14 PARTIALLY IMPLEMENTED
  (`docs/audit/SRS-CODEBASE-GAP-ANALYSIS.md`).
- Working tree berisi perubahan PO yang sudah ada sebelumnya (mis. `docs/DECISIONS.md`,
  RTM, diagram, aset gambar) — tidak di-revert dan bukan bagian perubahan paket ini.
- Lingkungan: macOS, Node.js 24.19.0, npm 12.0.2, PostgreSQL 17.12 (Docker
  `eduvark-postgres`), psql 18.7, Docker 29.8.1, Android SDK 34–37,
  AGP 9.4.1 / Gradle 9.6.0 / Kotlin 2.2.10, JDK 17 (Gradle-provisioned).
- Backend `0.1.0`, API prefix `/api/v1`; Android `versionName 1.0.0` / `versionCode 1`.
- DB uji terisolasi: `eduvark_test_lead` (final), `eduvark_test_p1..p5` (per paket),
  `eduvark_test_restore2` (restore), `eduvark_lead_prodsim` (rehearsal cutover),
  `eduvark_lead_admin_login` (uji login Admin ID baru). Reset DB hanya diizinkan untuk
  nama berakhiran `_test` atau dengan `ALLOW_TEST_DB_RESET=1`.

## 3. Hasil per paket

### Paket 1 — FR-001/003/028; AT-02/10/20/26/27/32/35

Perubahan utama:

- Guard `requirePasswordChanged` pada seluruh jalur akademik (topik list/detail/materials,
  dashboard, case, feedback, submission, kelas dosen, analitik, admin akademik); ganti
  password, logout, dan refresh tetap tersedia. Kebijakan password lama dipertahankan.
- Android: perubahan password/logout tidak lagi dianggap sukses sebelum respons HTTP valid
  (`ResponseFailure` + `AuthRepositoryImpl`, `AccountViewModel`, `HomeViewModel`); kegagalan
  ditampilkan sebagai error, sesi tidak dibersihkan, navigasi tidak terjadi.
- Detail jawaban/nilai: hanya pemilik dengan enrollment `ACTIVE` dan dosen berwenang;
  `LEFT` menutup akses (`ENROLLMENT_INACTIVE`), reaktivasi memulihkan; admin ditolak.
- Model sesi: satu sesi aktif per user, VARK terkunci per sesi, akses 15 menit, refresh
  idle 8 jam, absolut 12 jam; refresh tidak memperpanjang absolut; logout/rotasi mencabut.

Bukti: `backend/tests/integration/academic-access.test.ts` **9/9 PASS** (termasuk
ACTIVE→LEFT→ACTIVE, lintas kelas, dosen luar, admin, VARK, idle 9 jam, absolut, refresh
race dengan revokasi 3 iterasi, kebijakan password), regresi `auth.test.ts` 14/14;
Android unit `ResponseFailureTest`, `AccountViewModelTest`, `HomeViewModelTest` lulus.
Bukti lengkap: `docs/audit/evidence/P1.md`.

### Paket 2 — FR-007/014/015; AT-01/03/18–22/26/32

Perubahan utama:

- Modul `materials` baru: `GET /api/v1/materials/:id/content` (autentikasi + otorisasi
  ulang per permintaan: akun, sesi, role, penugasan, enrollment `ACTIVE`, ketersediaan
  materi, topik terpublikasi, VARK; `Cache-Control: no-store`, `Vary: Authorization`,
  dukungan `Range`/`If-Range`, 416, streaming privat) dan
  `GET /api/v1/materials/:id/link-target` (hanya HTTPS + otorisasi terkini).
- Topik tidak lagi mengirim `signedUrl`; klien mendapat alamat tetap `contentUrl` /
  `linkTargetUrl` berbasis identitas materi. Rute signed URL lama tetap melayani URL yang
  sudah beredar sampai kedaluwarsa, tetapi tidak menerbitkan URL baru.
- Android: buka materi lewat request terautentikasi → file sementara di cache → FileProvider
  `ACTION_VIEW`; tautan eksternal ke browser; 401 → login, 403/404 → take-down, error
  jaringan dibedakan; cache dibersihkan setiap kali membuka.

Bukti: `backend/tests/integration/material-access.test.ts` **11/11 PASS** (termasuk
logout + login dengan VARK berbeda, sesi kedaluwarsa, `LEFT`, unpublish, soft delete,
wajib ganti password, dosen berwenang, tautan eksternal, header/range/retry, 416);
Android `StudentMaterialsOpenTest` 6/6 + `StudentMaterialsViewModelTest` lulus;
`material-access.test.ts` juga mencakup AT-26 unduh setelah guard password. Bukti:
`docs/audit/evidence/P2.md`.

Catatan transisi URL lama ada di §8.

### Paket 3 — FR-001/034/036; AT-25/27/28

Perubahan utama:

- Login seluruh role memakai satu field berlabel NIM/NIDN; input ASCII digit saja dan
  berhenti menerima setelah digit ke-11 (ketik maupun paste), submit hanya 10–11 digit;
  validasi format berbasis role di backend.
- ID Admin otomatis `014014` + 5 digit mulai `00001`, unik global, aman konkurensi,
  melewati kandidat terpakai, NIM/NIDN lama dipertahankan, celah nomor diperbolehkan.
- `002_admin_login_ids.sql`: backfill deterministik Admin lama (UUID, relasi, histori,
  password, status, must-change dipertahankan), constraint `ck_users_admin_login_id`.
  Preflight collision/capacity tanpa PII: `backend/src/db/preflight-admin-ids.ts`.
- Transisi role + ID dalam satu transaksi (termasuk penugasan, enrollment, sesi); create
  akun/seed/API/UI diselaraskan; reseed idempoten; proteksi ganti-password sendiri dan
  Admin aktif terakhir dipertahankan.

Bukti: `login-id.test.ts` 8 unit, `admin-identity.test.ts` 23 tes (9/10/11/12 digit,
karakter invalid, leading zero, collision `01401400001`, konkurensi, ekshausti, reseed,
tujuan invalid, rollback, login ID baru, relasi UUID sama, simulasi migrasi/recovery),
`admin.test.ts` 10/10, `auth.test.ts` 14/14; migrasi 001 tidak diubah.
Android: `LoginIdRulesTest` 8/8, `LoginViewModelTest` 7/7, `AdminCreateUserViewModelTest`
5/5, `AdminUserDetailViewModelTest` 5/5; device: `LoginScreenTest` 4/4 PASS (filter
ketik/paste, stop di 11 digit, gate submit, kredensial tampil). Bukti:
`docs/audit/evidence/P3.md`.

### Paket 4 — FR-029–033; AT-05/06/17/24/30/34

Perubahan utama:

- Analitik memfilter feedback berdasarkan **kategori asal nilai tersimpan**, bukan VARK
  login terakhir; seluruh nilai category-map didukung; respons memuat `vark` nullable +
  `originCategories`; populasi kelas dari enrollment `ACTIVE`.
- Jumlah responden + definisi agregasi disertakan dari backend (`aggregation`,
  `definitions`); 0% dihitung sebagai respons, `null` berarti belum feedback.
- Kinesthetic `assessedCount`/`meanScore` dihitung dari seluruh join submission+assessment
  (termasuk enrollment `LEFT`, setelah logout/ganti VARK); terpisah dari subjektif V/A/R.
  `notRespondedCount`/`notSubmittedCount` dipertahankan sebagai kompatibilitas.
- Android: DTO/domain/repo nullable, VM menyediakan statistik per kategori asal, filter
  berdasarkan kategori asal, kartu responden/definisi agregasi, pembeda 0% vs belum
  merespons, kartu Kinesthetic terpisah.

Bukti: `analytics.test.ts` 11 + `analytics-origin.test.ts` 4 = **15/15 PASS** (dataset
acceptance Visual 40 → Audio 60 → Visual 75 lalu relogin Kinesthetic; dua final 80 & 100 →
`assessedCount 2`, `meanScore 90` setelah logout + ganti VARK + `LEFT`; 0 vs null; dosen
luar 403; admin 403; kategori invalid 422). Android `LecturerAnalyticsViewModelTest` 4/4
PASS. Bukti: `docs/audit/evidence/P4.md`.

### Paket 5 — FR-009/016/017/043/047; AT-04/11/15/23; regresi

Perubahan utama:

- Edit judul/deskripsi topik existing memakai ID yang sama (PATCH), nilai persist setelah
  dibuka kembali, publikasi/otoritas/arsip terjaga, perlindungan saat sudah ada submission.
- Empty state materi memakai teks Indonesia persis
  `materi belum tersedia, harap hubungi dosen terlebih dahulu` (+ padanan Inggris) dan
  dibedakan dari error muat; jalur Kinesthetic tetap menampilkan studi kasus.
- Paginasi/reorder: ambil semua halaman, reorder mengirim daftar lengkap, mahasiswa melihat
  urutan tersimpan; kelas 55/250 topik diuji.
- Nilai manual integer 0–100 + komentar opsional ≤1.000 karakter; input tidak valid tidak
  diubah diam-diam; konfirmasi/finalitas dipertahankan; penghitung jawaban 1–10.000
  **code point** setelah trim, satuan sama di Android/backend; backend memutuskan.
- Final submission sekali, status tanpa submission, kunci deadline setelah submission,
  deadline inklusif berbasis waktu mulai transaksi DB, rollback penilaian.

Bukti: `answer-length.test.ts` 12/12, `case-boundary.test.ts` 8/8,
`grade-validation.test.ts` 12/12, `topic-order-pagination.test.ts` 6/6,
`assessment-rollback.test.ts` 1/1; Android `P5TextRulesTest` 6/6,
`LecturerTopicsUpdateViewModelTest` 5/5, `TopicRepositoryPaginationTest` 3/3,
`StudentCaseStateViewModelTest` 7/7, `LecturerGradeValidationViewModelTest` 6/6; device
`P5ValidationInstrumentedTest` 3/3 PASS (teks Indonesia persis, counter emoji, nilai manual
tidak diubah diam-diam). Bukti: `docs/audit/evidence/P5.md`.

### Paket 6 — Verifikasi silang & kesiapan

Lihat §5–§11. Ringkas: seluruh build/lint/typecheck/suite dijalankan; DB suite mencatat
skip (tidak ada tes DB yang di-skip pada run final); restore lokal terverifikasi; rehearsal
migrasi Admin ID berhasil; staging tidak tersedia sehingga OQ-05 **NEEDS VERIFICATION**.

## 4. Matriks `Paket | FR/AT | Hasil | Bukti | Risiko tersisa`

> **STATUS MATRIKS: SUPERSEDED (historis).** Matriks ini berasal dari run awal
> (§1–§15) dan tidak lagi menjadi acuan status. Acuan pemetaan AT terbaru ada di
> §16.6; matriks kompatibilitas final akan diterbitkan di
> `docs/audit/evidence/ROLLOUT-RUNBOOK.md`, dan §16.4 akan
> digantikan oleh hasil run final Lead. Angka di tabel ini dipertahankan sebagai
> riwayat, bukan klaim status terkini.

Status: **PASS** = dibuktikan oleh tes/perintah yang dijalankan pada perubahan ini;
**NEEDS VERIFICATION** = belum ada bukti eksekusi yang cukup; **NOT RUN** = tidak
dijalankan karena di luar cakupan paket.

| Paket | FR/AT | Hasil | Bukti | Risiko tersisa |
| --- | --- | --- | --- | --- |
| P1 | FR-001 login/logout 3 role, sesi lama dicabut | PASS | `auth.test.ts` 14/14, `academic-access.test.ts` 9/9 | — |
| P1 | FR-003 wajib ganti password menahan akses akademik; change/logout tetap | PASS (backend + unit Android) | 8 rute mahasiswa + 5 dosen + admin → 403 `PASSWORD_CHANGE_REQUIRED`; `ResponseFailureTest`, `AccountViewModelTest` | Alur UI penuh di perangkat NEEDS VERIFICATION |
| P1 | FR-028 detail jawaban/nilai: pemilik ACTIVE + dosen berwenang | PASS | `academic-access.test.ts` ACTIVE→LEFT→ACTIVE, lintas kelas/dosen, admin | — |
| P1 | AT-02 VARK di luar 4 kategori ditolak; ganti VARK sesi aktif ditolak | PASS | 422 `VALIDATION_ERROR`; 409 `VARK_ALREADY_SET` | — |
| P1 | AT-10 Admin ditolak operasi akademik | PASS | 403 `FORBIDDEN` (detail & penilaian) | — |
| P1 | AT-20 enrollment LEFT: mahasiswa ditolak, dosen tetap, reaktivasi pulih | PASS | 403 `ENROLLMENT_INACTIVE` → 200 setelah ACTIVE | — |
| P1 | AT-26 wajib ganti password + kebijakan password + sesi lama dicabut | PASS (backend, termasuk unduh lewat endpoint baru P2) | `academic-access.test.ts`; unduh: `material-access.test.ts` | Rute signed URL legacy tidak memakai guard sesi (lihat §8) |
| P1 | AT-27 idle/absolut/refresh/logout/rotasi | PASS | idle 9 jam → `SESSION_EXPIRED`; absolut tidak bergeser; race revokasi | — |
| P1 | AT-32 akses di luar izin ditolak (kelas lain, materi sebelum VARK) | PASS | 403 `SUBMISSION_FORBIDDEN`/`CLASS_FORBIDDEN`; 428 `VARK_REQUIRED` | — |
| P1 | AT-35 body error tanpa jawaban/nilai bagi pihak tak berhak | PASS | assert tanpa `data`/`answerText` | — |
| P2 | FR-007 alamat tetap materi + otorisasi ulang per permintaan | PASS (backend) | `material-access.test.ts` 11/11 | — |
| P2 | FR-014 tautan eksternal HTTPS dengan izin terkini | PASS | `link-target` + tes #7 | — |
| P2 | FR-015 Android buka berkas lewat request terautentikasi + FileProvider | PASS (unit/compile) | `StudentMaterialsOpenTest` 6/6, `compileDebugKotlin` | Buka PDF/audio/video nyata di perangkat NEEDS VERIFICATION |
| P2 | AT-01 alamat tetap menolak konten setelah izin berakhir | PASS | tes #1 (denial nyata via alamat tetap) | — |
| P2 | AT-03 alamat lama setelah logout + login VARK berbeda | PASS | tes #2 (401/403) | — |
| P2 | AT-18 header respons konten | PASS | content-type/disposition/length/no-store/vary/accept-ranges/etag | — |
| P2 | AT-19 percobaan ulang/Range dire-otorisasi | PASS | tes #4 (Range/If-Range, 416) | — |
| P2 | AT-20 logout, sesi kedaluwarsa, `LEFT`, unpublish, soft delete, wajib ganti password | PASS | tes #5 | — |
| P2 | AT-21 dosen berwenang dapat membuka | PASS | tes #6 | — |
| P2 | AT-22 tautan eksternal HTTTS-only | PASS | tes #7 | — |
| P2 | AT-26 unduh ditahan saat wajib ganti password (endpoint baru) | PASS | tes #8 | Rute legacy tetap terbuka sampai URL lama habis (§8) |
| P2 | AT-32 otorisasi backend per permintaan | PASS | tes #9 | — |
| P3 | FR-001 login numerik 3 role, validasi format per role | PASS (backend + device login) | `login-id.test.ts` 8/8; `LoginScreenTest` 4/4 | — |
| P3 | FR-034 kelola akun & role + ID atomik, rollback | PASS (backend) | `admin.test.ts` 10/10, `admin-identity.test.ts` transisi/rollback | Form admin di perangkat NEEDS VERIFICATION |
| P3 | FR-036 ID unik, alokasi otomatis, Admin lama, preflight | PASS (backend + rehearsal) | 23 tes identitas; `002` pada salinan dump dev → `01401400001` | Kapasitas 99.999 (ekshausti diuji) |
| P3 | AT-25 login numerik Android, pola `014014XXXXX`, keunikan, peralihan Admin | PASS (backend + device); form admin perangkat NEEDS VERIFICATION | `LoginScreenTest` 4/4; `admin-identity.test.ts` | — |
| P3 | AT-27 kredensial/status/login/logout/sesi (regresi) | PASS | `auth.test.ts` 14/14 | — |
| P3 | AT-28 kelola akun/reset/enrollment/role/keunikan/transaksi | PASS | `admin.test.ts` 10/10 + rollback | — |
| P4 | FR-029 grafik feedback subjektif per topik & kategori V/A/R | PASS (backend + VM unit) | `analytics.test.ts` #1–3, origin #1–2 | Render perangkat NEEDS VERIFICATION |
| P4 | FR-030 filter kategori ASAL, tidak tergantung login terakhir | PASS | `analytics.test.ts` #5/#7/#8, origin #1–3 | — |
| P4 | FR-031 bedakan belum feedback dari 0% | PASS | `analytics.test.ts` #6/#9 | — |
| P4 | FR-032 responden + definisi agregasi + Kinesthetic dari semua nilai final | PASS | `analytics.test.ts` #4, origin #4 | — |
| P4 | FR-033 subjektif tidak digabung dengan Kinesthetic | PASS | `analytics.test.ts` #3, VM test | — |
| P4 | AT-05 Visual 40/Audio 60/Visual 75 lalu VARK lain | PASS | origin #1/#2 | — |
| P4 | AT-06 slider feedback 75% tanpa record baru | NOT RUN | milik paket feedback, di luar cakupan P4 | Perlu tes paket feedback |
| P4 | AT-17 status "Tidak Mengumpulkan" per mahasiswa | NOT RUN | FR-048 milik paket assessments | Perlu tes paket assessments |
| P4 | AT-24 dua final 80 & 100 → Kinesthetic 90, V/A/R terpisah setelah logout/VARK/LEFT | PASS | origin #4 | — |
| P4 | AT-30 validasi feedback / sesi Kinesthetic / akses pihak lain | NEEDS VERIFICATION | sebagian di paket feedback/materials; analitik: 403 luar kelas, 403 admin, 422 invalid | Butir feedback belum diuji penuh |
| P4 | AT-34 grafik rerata, responden, definisi, 0% vs belum, Kinesthetic terpisah | PASS (backend + VM) | `analytics.test.ts` #4/#6/#9, origin #4 | Render perangkat NEEDS VERIFICATION |
| P5 | FR-009 edit judul/deskripsi topik ID sama & persist | PASS (unit) / NEEDS VERIFICATION (UI device) | `LecturerTopicsUpdateViewModelTest` 5/5 | Render/edit di perangkat belum dijalankan |
| P5 | FR-016 empty state persis + padanan EN + kasus Kinesthetic | PASS (string + VM + device) | `P5ValidationInstrumentedTest` 3/3, `StudentCaseStateViewModelTest` 7/7 | — |
| P5 | FR-017 paginasi + reorder + urutan mahasiswa | PASS (backend + unit); UI device NEEDS VERIFICATION | `topic-order-pagination.test.ts` 6/6, `TopicRepositoryPaginationTest` 3/3 | — |
| P5 | FR-043 nilai integer 0–100 + komentar ≤1.000, tidak diubah diam-diam | PASS (backend + VM + device) | `grade-validation.test.ts` 12/12, device test | — |
| P5 | FR-047 jawaban 1–10.000 code point setelah trim, backend memutuskan | PASS (backend + unit + device) | `answer-length.test.ts` 12/12; device counter emoji | — |
| P5 | AT-04 edit topik terlihat setelah reopen | NEEDS VERIFICATION | butuh eksekusi UI perangkat | Sisa risiko UX |
| P5 | AT-11 >50 topik, reorder utuh, urutan sama | PASS (backend + unit); UI device NEEDS VERIFICATION | lihat FR-017 | — |
| P5 | AT-15 empty state materi beda dari error muat | PASS (device) | `P5ValidationInstrumentedTest` (teks ID persis) + VM error terpisah | — |
| P5 | AT-23 validasi nilai manual tanpa mutasi diam-diam | PASS (backend + VM + device) | `grade-validation.test.ts`, `LecturerGradeValidationViewModelTest` 6/6, device test | — |
| P5 | Regresi AT-07–09/12–14/16/29/31/33 (topik, assessment, deadline, finalitas) | PASS | `topics.test.ts` 15, `assessments.test.ts` 8, `case-boundary.test.ts` 8, `cases.test.ts` 8, `feedback.test.ts` 6, `assessment-rollback.test.ts` 1 | — |
| P6 | Backend unit/integration pada PG terisolasi | PASS | 23/23 berkas, 189/189 tes (`eduvark_test_lead`) | — |
| P6 | Typecheck/lint/build backend | PASS | exit 0 untuk `typecheck`, `lint`, `build` | — |
| P6 | Android unit test + build + lint | PASS | 96/96 tes; `assembleDebug`, `assembleRelease`, `lintDebug` sukses | — |
| P6 | Uji device/perangkat untuk alur UI yang berubah | PASS (8/8) | `connectedDebugAndroidTest` tanpa `CaptureScreensTest` | `CaptureScreensTest` (E2E screenshot milik PO) NOT RUN — lihat §5 |
| P6 | OQ-05 benchmark staging (p95 <2 s/<3 s @100) | NEEDS VERIFICATION | tidak ada staging (docs/ci-cd/rollback.md) | Wajib diukur sebelum SLO produksi |
| P6 | Restore backup PostgreSQL lokal | PASS (skala dev) | §9 | Data dev minim (assessments=0, feedback=0, materi LINK) |
| P6 | Rehearsal migrasi Admin ID pada salinan produksi-like | PASS | §8 | Bukan produksi; tetap butuh jendela cutover |

## 5. Perintah dan hasil pengujian aktual

> **STATUS: HISTORIS/SUPERSEDED.** Angka di §5 adalah run remediasi pertama.
> Hasil final ada di §16.2 (backend) dan §16.3 (Android).

### Backend (final, DB `eduvark_test_lead` dibuat ulang dengan 001+002)

```
cd backend
DATABASE_URL=postgresql://eduvark:eduvark@localhost:5432/eduvark_test_lead npm run db:migrate
ALLOW_TEST_DB_RESET=1 DATABASE_URL=postgresql://eduvark:eduvark@localhost:5432/eduvark_test_lead npm test
npm run typecheck   # exit 0
npm run lint        # exit 0 (eslint bersih)
npm run build       # exit 0
```

Hasil `npm test`: **Test Files 23 passed (23), Tests 189 passed (189)**, exit 0
(`/tmp/final-test2.log`). Rincian berkas: analytics 11, material-access 11,
admin-identity 23, topics 15, assessments 8, academic-access 9, grade-validation 12,
answer-length 12, analytics-origin 4, case-boundary 8, auth 14, cases 8, admin 10,
topic-order-pagination 6, feedback 6, assessment-rollback 1, password 3, health 3,
storage 1, env 10, tokens 3, login-id 8, login-throttle 3. Tidak ada tes DB yang di-skip
pada run ini.

Catatan proses: sempat muncul 9 kegagalan sementara di `academic-access.test.ts` akibat
pembersihan lint Lead yang menghapus fixture dosen tak terpakai padahal fixture itu dipakai
untuk skenario "dosen luar kelas"; fixture dipulihkan (tanpa variabel tak terpakai) dan
suite penuh kembali hijau. Tidak ada perubahan perilaku aplikasi dari insiden ini.

### Android (serial, Gradle wrapper dengan lock `/tmp/eduvark-gradle-locked.sh`)

```
bash /tmp/eduvark-gradle-locked.sh :app:assembleDebug :app:assembleRelease :app:lintDebug --console=plain
# BUILD SUCCESSFUL (1m 56s)

bash /tmp/eduvark-gradle-locked.sh :app:testDebugUnitTest :app:compileDebugAndroidTestKotlin --console=plain
# BUILD SUCCESSFUL; agregasi JUnit XML: 24 berkas, 96 tes, 0 failures, 0 errors, 0 skipped
```

`connectedDebugAndroidTest` dijalankan pada emulator `Pixel_10_Pro_XL` (Android 17 /
API 37, `emulator-5554`):

- Run penuh: 24 tes, **16 gagal** — 15 di antaranya `CaptureScreensTest` (harness screenshot
  E2E milik PO yang memerlukan backend live `10.0.2.2:3000` dan fixture login admin legacy
  `admin`), plus 1 tes `LoginScreenTest` yang ekspektasinya salah (mengharapkan
  10 digit + paste "23" → `...011`, padahal perilaku yang benar adalah memotong ke 11 digit
  pertama → `...012`).
- Tes `LoginScreenTest` diperbaiki ekspektasinya (bukan perilaku aplikasi); lalu run ulang
  dengan mengecualikan kelas E2E:
  `-Pandroid.testInstrumentationRunnerArguments.notClass=com.eduvark.app.CaptureScreensTest`.
- Hasil final: **8/8 PASS** — `ExampleInstrumentedTest` 1, `LoginScreenTest` 4
  (digit-only saat mengetik, pembersihan paste + stop 11 digit, gate submit 10–11 digit,
  tampilan kredensial), `P5ValidationInstrumentedTest` 3 (teks empty state Indonesia
  persis, penghitung 10.000 code point termasuk emoji, nilai manual tidak diubah diam-diam).

`CaptureScreensTest` **NOT RUN / NEEDS VERIFICATION** untuk laporan ini: harness milik PO,
bergantung pada backend live dan data seed tertentu, dan memakai username admin legacy
`admin` yang memang sudah dihentikan oleh FR-001/FR-036. Kelas ini bukan verifikasi alur
yang diubah dan tidak dijadikan bukti PASS.

### Bukti DB/migrasi/restore

- Suite backend berjalan pada PostgreSQL 17 terisolasi; migrasi 001 + 002 diterapkan dari
  nol (`appliedCount 2`, include 002) tanpa mengubah `001_init.sql`.
- Simulasi migrasi/recovery Admin ada di `admin-identity.test.ts` (DB scratch
  `..._scratch`): backfill, collision skip, UUID/relasi tetap, constraint menolak Admin
  non-pola, rollback = restore snapshot pre-002 identik.
- Rehearsal cutover & restore lokal: §8 dan §9.

## 6. Berkas, endpoint, DTO, aturan auth, dan migrasi yang berubah

### Backend

- Modul baru: `src/modules/materials/{materials.routes.ts,materials.controller.ts,
  materials.service.ts,materials.repository.ts}`; mount di `src/app.ts`.
- `src/modules/topics/topics.service.ts` (berhenti menerbitkan signed URL; `contentUrl` /
  `linkTargetUrl`), `topics.routes.ts` (guard password).
- `src/modules/assessments/{assessments.repository.ts,assessments.service.ts}`:
  `enrollment_status` + penolakan `ENROLLMENT_INACTIVE` untuk non-ACTIVE.
- `src/modules/analytics/{analytics.repository.ts,analytics.service.ts}`: filter kategori
  asal, populasi ACTIVE, `vark`/`originCategories`, `aggregation`, `definitions`,
  `respondentCount`/`notRespondedCount`, metrik Kinesthetic, `notSubmittedCount` legacy.
- `src/modules/cases/cases.schema.ts` (hapus batas UTF-16) + validasi code point 1–10.000
  di service (422 `ANSWER_LENGTH_INVALID`).
- `src/modules/auth/auth.service.ts` (validasi login per role), `src/lib/login-id.ts`
  (pola/prefix), `src/modules/admin/{admin.repository.ts,admin.schema.ts,admin.service.ts}`
  (alokasi ID, transaksi role+ID, proteksi), `src/db/seed-admin-core.ts` (baru),
  `src/db/seed-admin.ts`, `src/db/preflight-admin-ids.ts` (baru), `src/lib/storage.ts`
  (kebutuhan modul materi).
- Migrasi baru: `src/db/migrations/002_admin_login_ids.sql` (backfill + constraint);
  `001_init.sql` tidak berubah.
- DTO/response: materi `contentUrl`/`linkTargetUrl` (signedUrl/expiresAt null), analitik
  `vark` nullable + `originCategories` + `aggregation` + `definitions`, submission detail
  `enrollment_status` internal.

### Android

- P1: `core/network/ResponseFailure.kt` (baru), `data/repository/AuthRepositoryImpl.kt`,
  `presentation/account/{AccountViewModel,AccountScreen}.kt`,
  `presentation/home/{HomeViewModel,HomeScreen}.kt`, `testutil/FakeAuthRepository.kt`.
- P2: `data/repository/TopicRepositoryImpl.kt`, DTO topik/materi,
  `core/files/AndroidMaterialFileStore.kt` (baru), `domain/{model,repository,usecase}`,
  `presentation/student/materials/*`, `res/xml/file_paths.xml`, `AndroidManifest.xml`
  (FileProvider), `strings_p2.xml`.
- P3: `core/common/LoginIdRules.kt` (baru), login screen/VM, admin create/detail
  screen/VM/DTO/repo/usecase, `strings_p3.xml`, unit test + `LoginScreenTest`.
- P4: DTO/domain/repo/VM/screen analitik, `strings_p4.xml`.
- P5: `core/text/P5TextRules.kt` (baru), `presentation/student/cases/*`,
  `presentation/student/topics/StudentTopicsScreen.kt`,
  `presentation/lecturer/topics/*`, `presentation/lecturer/assessments/*`,
  `strings_p5.xml`, unit test + `P5ValidationInstrumentedTest`.
- String id/en untuk semua paket; `strings_common.xml`/`strings_home.xml`/
  `strings_lecturer_a.xml` disesuaikan.

### Aturan auth/sesi (tidak diubah dari desain, kini ditegakkan konsisten)

Satu sesi aktif per user; access 15 menit; refresh idle 8 jam; absolut 12 jam; refresh
tidak memperpanjang absolut; VARK terkunci per sesi; `requirePasswordChanged` menahan
seluruh jalur akademik; role-based authorization di backend untuk setiap permintaan.

### Daftar berkas perubahan (ringkas)

Lengkap: 129 berkas kode/dokumentasi (di luar aset/diagram milik PO), terdiri dari 5 file
evidence baru, modul backend, berkas Android (main/test/androidTest), test backend, dan
string resource. Daftar lengkap dapat diperiksa dengan `git status` (lihat §12). Tidak ada
berkas terlarang yang disentuh.

## 7. Matriks acceptance test (AT) — cakupan bukti

> **STATUS MATRIKS: SUPERSEDED (historis).** Pemetaan AT di §7 memuat kesalahan
> pemetaan AT-04/11/15/23 dan digantikan oleh §16.6. Rujukan status terbaru:
> `docs/audit/evidence/ROLLOUT-RUNBOOK.md` dan §16.6; §16.4
> laporan ini akan digantikan hasil run final Lead.

| Kategori bukti | AT |
| --- | --- |
| Terbukti otomatis (backend/DB, dijalankan) | AT-01, AT-02, AT-03, AT-05, AT-10, AT-11 (backend), AT-15, AT-18, AT-19, AT-20, AT-21, AT-22, AT-23, AT-24, AT-25, AT-26 (backend), AT-27, AT-28, AT-32, AT-34, AT-35 |
| Terbukti otomatis + device (instrumentation, dijalankan) | AT-15, AT-23 (tambahan), AT-25 (login), AT-26 (via endpoint baru), AT-02/27/32 (backend) |
| Terbukti otomatis, perlu render/perangkat untuk klaim UI penuh | AT-04, AT-11 (UI), AT-34 (UI), P1/P2 alur UI |
| NEEDS VERIFICATION / NOT RUN | AT-06, AT-17 (di luar cakupan), AT-30 (sebagian), OQ-05 staging, buka PDF/audio/video nyata di perangkat |

## 8. Simulasi Admin ID, cutover, dan rollback

### Simulasi collision & backfill (DB scratch, nyata dijalankan)

Fixture: `admin` (ACTIVE, must_change=true), `admin2` (INACTIVE), Admin berpola
`01401400003`, mahasiswa memakai `01401400001`, dosen `1990010001`.

Hasil `002_admin_login_ids.sql`:

- `admin` → `01401400002` (melewati `01401400001` yang dipakai mahasiswa), `admin2` →
  `01401400004`; nomor deterministik berdasarkan `created_at, id`.
- UUID, password, status, `must_change_password`, dan seluruh relasi (enrollment,
  penugasan, topik, case, submission, assessment, sesi, audit) tetap menunjuk UUID sama.
- Constraint menolak Admin dengan login non-pola (`ck_users_admin_login_id`).
- Rollback: drop DB + restore dump pre-002 → snapshot data identik
  (`ROLLBACK_EQUALS_PRE002_DATA=yes`); migrasi bersifat maju (tidak ada down-migration).

### Rehearsal cutover pada salinan dump dev produksi-like

Dump lokal `/tmp/eduvark-local-restore-20261124T091809Z.dump` direstore ke
`eduvark_lead_prodsim`, lalu preflight dan 002 dijalankan:

- Preflight sebelum migrasi: total Admin 1, `needsBackfill 1`, contoh ter-mask
  `014014-#8c6976` (tanpa PII), collision akun akademik berpola Admin 0, kapasitas 99.999,
  kandidat berikutnya `01401400001`, `backfillFeasible true`.
- Setelah migrasi: `admin` → `01401400001`, UUID tetap
  `9f3f61d4-adc6-4c24-a17c-9e84c56d003f`, 11 `auth_sessions` lama tetap terhubung ke user
  yang sama, jumlah user tetap 4.
- Uji login end-to-end pada DB scratch terpisah (`eduvark_lead_admin_login`): login
  `01401400009` → **200** + access token; login `admin` → **401**; login bentuk akademik
  10 digit untuk role Admin → **401**; `INSERT` Admin `admin` ditolak constraint DB.
- Catatan: password user pada dump dev berasal dari seed lama sehingga tidak dipakai untuk
  uji login; uji login memakai akun uji dengan hash Argon2id baru.

### Rencana cutover ID Admin (butuh persetujuan Owner)

1. Preflight produksi (`preflight-admin-ids.ts`) → lampirkan ringkasan collision/capacity
   tanpa PII; pastikan `backfillFeasible true` dan `numbersRemaining` cukup.
2. Backup penuh DB produksi (wajib; deploy engine sudah melakukannya sebelum migrasi).
3. Jalankan migrasi 002 dalam jendela pemeliharaan; migrasi mengunci tabel dan mengisi ID.
4. Verifikasi pasca-migrasi: 0 Admin non-pola, constraint ada, login Admin dengan ID baru,
   sesi lama tetap valid sampai kedaluwarsa/rotasi.
5. Komunikasikan ID Admin baru ke pemilik akun Admin.
6. Rollback: restore backup pra-002 (kehilangan sesi/data setelah backup), redeploy image
   lama; tidak ada down-migration.

## 9. Transisi URL materi lama (P2 / C1-02)

- Penerbitan signed URL baru **berhenti**; klien baru memakai alamat tetap
  `/api/v1/materials/:id/content` (selalu diotorisasi ulang).
- URL lama yang sudah beredar tetap dilayani rute legacy sampai kedaluwarsa alami.
- Jendela penonaktifan yang dapat dibuktikan = **waktu penerbitan terakhir + TTL maksimum
  yang terbukti**. `R2_SIGNED_URL_TTL_SECONDS` default 300 detik (`backend/.env` = 300);
  TTL lokal terbukti (URL 1 detik → 403 `SIGNED_URL_EXPIRED` pada 2,2 detik). TTL efektif
  di produksi/R2 **NEEDS VERIFICATION** → sebelum klaim cutover, ukur TTL efektif di
  produksi lalu tunggu ≥ TTL tersebut sebelum rute legacy dimatikan.
- Pemantauan: log 403/410 pada `material-files`, jumlah akses legacy menurun, tidak ada
  error 5xx pada endpoint konten baru.
- Rollback: aktifkan kembali penerbitan signed URL (feature flag/kode lama) sambil
  mempertahankan endpoint baru; URL lama yang sudah kedaluwarsa tidak dapat dihidupkan
  kembali kecuali diterbitkan ulang.

## 10. Bukti restore lokal dan batasannya

- Dump `pg_dump` DB dev: `/tmp/eduvark-local-restore-20261124T091809Z.dump`
  (47.193 byte); restore ke DB terisolasi `eduvark_test_restore2` di container PG 17.
- Waktu aktual: dump 0,135 s; create DB 0,146 s; restore 0,127 s; verifikasi 0,046 s;
  total 0,454 s.
- Integritas: checksum baris identik sebelum/sesudah untuk users, enrollments,
  submissions, topics, materials.
- Uji akses pasca-restore: login mahasiswa, `/auth/me`, `/auth/vark`, `/topics`, detail
  submission semua 200; objek contoh `restore-sample/sample.pdf` terbaca dari storage lokal
  (R2 tidak diuji — di luar wewenang).
- Batasan: data dev minim (`assessments = 0`, `feedback = 0`, materi hanya LINK) sehingga
  bukti untuk nilai/skor dan objek materi nyata terbatas; target RPO 24 jam / RTO 4 jam
  adalah target, **bukan** hasil ukur produksi; backup offsite di luar cakupan. RPO/RTO
  aktual produksi **NEEDS VERIFICATION**.
- Rekomendasi uji lanjutan (di luar scope ini): restore dump produksi terbaru di lingkungan
  terisolasi, ukur durasi, verifikasi akun + enrollment + jawaban + nilai + sampel objek
  materi; dokumentasikan recovery point dan waktu pemulihan aktual.

## 11. Rencana rollout, kompatibilitas, dan rollback (diselaraskan 24 Nov 2026)

> **Acuan operasional tunggal: `docs/audit/evidence/ROLLOUT-RUNBOOK.md`.** Ringkasan ini
> diselaraskan dengan perilaku deployment otomatis dan bukti aktual; bila berbeda, runbook
> yang dipakai.

**Urutan tunggal (tidak ada duplikat/urutan alternatif):**

1. **Aktifkan dan buktikan release hold.** Environment `homelab-testing` saat ini **tidak**
   punya required reviewers (`can_admins_bypass: true`), sehingga merge ke `main` memicu
   deploy otomatis. Sampai hold terbukti: **merge NO-GO**.
2. **Tutup total trafik publik** di edge, catat `t_close`, drain koneksi/pekerjaan tulis,
   hentikan penulis.
3. **Snapshot DB pra-002** (bukan pra-003) + checksum; simpan identitas image backend lama
   dan APK baseline.
4. **Jalankan migrasi 001–003 sebagai job pra-trafik** dengan image kandidat. `server.ts`
   tidak auto-migrate; jangan start backend sebelum migrasi sukses.
5. **Start backend kandidat di origin internal**, lalu smoke internal: health 200; login
   dengan header versi 2 → 200; tanpa header/versi 1 → 426; materi + Analytics.
   Smoke **tidak** memakai URL publik.
6. **Verifikasi APK baseline ditolak 426** pada sesi lama dan login baru; simpan
   screenshot/UI dump/log.
7. **Reopen hanya bila** smoke, penolakan APK lama, schema, dan stabilitas lulus; health
   atau rollback sukses saja tidak cukup. Persetujuan Owner dicatat.

**Kompatibilitas:** hanya pasangan **APK kandidat (`versionCode=2`, `fa20e463…`) × backend
kandidat** yang didukung. APK baseline tidak mengirim header versi dan ditolak
`426 CLIENT_VERSION_UNSUPPORTED` sebelum auth/kontrak data; health tetap 200. Ini bukan
"backend tetap menerima klien lama". APK lama yang sudah terpasang tetap ada di perangkat,
tetapi tidak bisa memakai API.

**Materi/signed URL:** backend kandidat tidak menerbitkan `signedUrl`; `contentUrl`
terautentikasi adalah kontrak baru. Tautan lama yang sudah terbit hanya hidup sampai
`t_last_issue + T_max`; TTL lokal 299,349 detik, TTL produksi/R2 **NEEDS VERIFICATION**.
Penolakan klien lama (426) dan kedaluwarsa URL (403) adalah dua kontrol terpisah.

**Rollback:** sah = image/backend lama + **restore snapshot pra-002 ke DB bersih** + verifikasi
pasangan lama/lama + **revoke/rotate sesi** dari snapshot. Rollback image-only di atas DB
002/003 **bukan** rollback penuh (login Admin legacy 401, endpoint konten 404 pada uji).
Rollback sebelum reopen tidak kehilangan data produk; rollback setelah tulisan baru
kehilangan data pasca-snapshot (simulasi: 6 baris produk + audit/sesi) dan butuh persetujuan
Owner. Auto-rollback deploy script hanya mengembalikan image, tidak merestore DB dan tidak
membuka trafik.

**Status:** staging/produksi **NOT RUN**, release hold **FAIL**, cutover produksi **NO-GO**.
Rincian bukti: §16.4/§16.10 dan runbook §2–§10.

## 12. Kepatuhan batasan dan status working tree

- Tidak ada commit, push, PR, atau merge. HEAD tetap `b7b6cd6`, branch
  `fix/eduvark-functional-audit-2026`; working tree kotor (perubahan dimaksudkan untuk
  ditinjau Owner).
- Tidak ada operasi pada database/storage/server/kredensial produksi atau staging.
- Tidak mengubah `Dokumen-SRS.docx` (SHA-256 tetap
  `a4bdc18928e0be3ba48cf868ecaa919bed6ab72a81cc17696754a39e0dcbe907`), `docs/DECISIONS.md`,
  `docs/srs/REQUIREMENTS_TRACEABILITY_MATRIX.md`, `001_init.sql`, `.github/`, maupun
  pipeline deploy. Berkas dokumen yang tampak berubah di `git status` adalah perubahan PO
  yang sudah ada sebelum pekerjaan ini.
- Seluruh DB uji/scratch bersifat lokal dan dapat dihapus
  (`eduvark_test_*`, `eduvark_lead_prodsim`, `eduvark_lead_admin_login`,
  `eduvark_lead_mig_sim2`).

## 13. Butir yang memerlukan persetujuan Owner sebelum cutover produksi

> **STATUS: HISTORIS.** Daftar approval terkini ada di
> `docs/audit/evidence/ROLLOUT-RUNBOOK.md` §7–§8 (Product/Engineering/Infra/Security
> Owner, semuanya PENDING; **release hold wajib disetujui/diaktifkan sebelum merge**).
> Butir di bawah dipertahankan sebagai riwayat.

1. Persetujuan menjalankan migrasi 002 pada produksi (backfill ID Admin) beserta jendela
   pemeliharaan dan komunikasi ID baru ke Admin.
2. Persetujuan rollout backend → Android dengan urutan dan pembekuan rilis seperti §11.
3. Persetujuan penghentian rute signed URL legacy setelah TTL produksi terukur + masa
   tunggu; sebelumnya rute tetap aktif.
4. Konfirmasi target RPO/RTO dan jadwal uji restore produksi di lingkungan terisolasi.
5. Konfirmasi kebutuhan benchmark staging/OQ-05 (p95 baca <2 s, tulis <3 s @100 konkuren;
   media diukur terpisah; SLO final ditetapkan kemudian).
6. Persetujuan untuk tidak memperbaiki `CaptureScreensTest` (harness E2E PO yang memakai
   login admin legacy) sebagai bagian perubahan ini, atau penugasan terpisah untuk
   memperbaruinya.
7. Jika buka berkas PDF/audio/video nyata di perangkat ingin dijadikan bukti rilis,
   sediakan akses file nyata di lingkungan uji (saat ini hanya teruji di tingkat unit +
   kompilasi).

## 14. Daftar NEEDS VERIFICATION / NOT RUN

> Tabel ini dipertahankan sebagai riwayat run awal (24 Nov 2026, sebelum remediasi).
> Status butir yang sudah tertutup oleh remediasi dicatat di §16.

| Butir | Alasan | Tindakan lanjutan |
| --- | --- | --- |
| Alur UI perangkat P1/P2/P4 dan edit topik P5 (AT-04) | instrumentation terbatas; alur Hilt+Jaringan tidak dijalankan | uji manual/device terarah sebelum rilis |
| Buka PDF/audio/video nyata (FR-015, AT-21/22 di perangkat) | tidak ada berkas media nyata di env uji | uji dengan materi nyata di staging/device |
| AT-06 (slider feedback 75% tanpa record baru) | di luar cakupan P4 | tes paket feedback |
| AT-17 / FR-048 status "Tidak Mengumpulkan" per mahasiswa | di luar cakupan P4 | tes paket assessments |
| AT-30 sebagian (validasi feedback, sesi Kinesthetic) | di luar cakupan P4 | tes paket feedback/materials |
| OQ-05 benchmark staging | staging tidak tersedia | ukur di staging/produksi terbatas |
| TTL efektif signed URL produksi/R2 | hanya TTL lokal terbukti | ukur di produksi |
| RPO/RTO & restore produksi | hanya restore dev lokal | uji restore dump produksi terisolasi |
| `CaptureScreensTest` E2E screenshot | harness PO butuh backend live + admin legacy | perbarui fixture/harness bila diinginkan |
| Backup offsite | di luar cakupan | keputusan Owner |

## 15. Lampiran — bukti per paket

- `docs/audit/evidence/P1.md` — FR-001/003/028, AT-02/10/20/26/27/32/35, perintah & hasil.
- `docs/audit/evidence/P2.md` — FR-007/014/015, AT-01/03/18–22/26/32, kontrak endpoint.
- `docs/audit/evidence/P3.md` — FR-001/034/036, AT-25/27/28, migrasi & preflight.
- `docs/audit/evidence/P4.md` — FR-029–033, AT-05/06/17/24/30/34, definisi agregasi.
- `docs/audit/evidence/P5.md` — FR-009/016/017/043/047, AT-04/11/15/23, regresi.
- Laporan ini — konsolidasi, P6, rollout/rollback, restore, persetujuan.

---

## 16. Addendum remediasi pasca cross-check (24 Nov 2026)

Bagian ini ditambahkan setelah status `REVISION REQUIRED` dari cross-check. Riwayat run
awal di §1–§15 tidak diubah; angka dan status di bawah adalah hasil run final setelah
semua temuan remediasi dikerjakan. Baseline kode tetap `b7b6cd6`; tidak ada commit,
push, PR, merge, deploy, atau perubahan produksi.

### 16.1 Ringkasan hasil

Kolom **Status** hanya memakai empat nilai: **PASS**, **FAIL**, **NOT RUN**,
**NEEDS VERIFICATION**. Pada cross-check kedua, klaim PASS yang tidak lagi sesuai
fakta dicabut dan ditandai **FAIL** (historis, dalam remediasi) atau
**NEEDS VERIFICATION** sampai Lead menerbitkan hasil run final. Hasil run final
Lead sudah dicatat di §16.2–§16.5; baris yang masih `NEEDS VERIFICATION`/`NOT RUN`
adalah lapisan yang memang belum dieksekusi. Lapisan bukti dipisah (§16.1.1) agar status API tidak dibaca
sebagai status layar nyata atau staging.

| Temuan | Perubahan | FR/AT | Bukti | Status | Risiko tersisa |
| --- | --- | --- | --- | --- | --- |
| Materi Kinesthetic memakai `signedUrl ?: url` dan tidak terautentikasi; 404/izin tidak tertangani rapi | Alur baru `contentUrl`/`linkTargetUrl`; unduhan terautentikasi di-stream ke berkas privat unik (bukan `.bytes()`), ada pembatalan/partial cleanup/prune; 404 `MATERIAL_NOT_AVAILABLE` → empty state Indonesia + Inggris; FILE dibuka lewat FileProvider, LINK lewat target terverifikasi | FR-007/014–016; AT-01/03/18–22/26/32 | `AndroidMaterialFileStoreTest` (instrumentasi 3/3), `HttpMaterialContentSource`, `StudentCaseViewModelTest`, `MaterialDtoCompatibilityTest`, P2 §9.5.1 | PASS | Viewer PDF/audio/video nyata di perangkat belum diuji (objek media nyata tidak tersedia) → layar nyata `NEEDS VERIFICATION` |
| Klien lama membaca `signedUrl` untuk FILE dan `vark` non-null; backend baru berhenti menerbitkan keduanya | **Kontrol versi nyata**: APK kandidat `versionCode=2` mengirim `X-EduVARK-Client-Version: 2`; backend menolak klien tanpa header/versi 1 dengan **426 sebelum auth/kontrak data**. APK baseline × backend kandidat: login/me 426, sesi aktif ditahan (real-screen). APK kandidat × backend kandidat: login, materi, Analytics PASS. Release hold **FAIL**; runbook menetapkan hold → tutup trafik → snapshot pra-002 → migrasi job → smoke internal → verifikasi 426 → reopen | FR-015/029–033; AT-05/18–21/24/34; C1-02 | `ROLLOUT-RUNBOOK.md`, `compat-e2e/client-compatibility-*.json`, `real-screen-evidence/`, `release-hold/EVIDENCE.md`, `rollback-pre-002/`, §16.4 | PASS untuk kontrol klien; rollback lokal PASS level API | Pasangan rilis hanya APK kandidat × backend kandidat; staging/produksi NOT RUN; TTL produksi/R2 NEEDS VERIFICATION; release hold belum ada sehingga merge NO-GO; rollback penuh butuh restore DB **pra-002** dan revoke sesi |
| Sesi Admin lama masih berlaku setelah ID Admin dimigrasikan | **Diperbaiki.** `002` mencatat setiap re-ID ke `admin_identity_changes` dan mencabut sesi akun itu dalam transaksi migrasi yang sama. `003` hanya menangani `01401400000` (catat penanda, re-ID, cabut sesi atomik) dan tidak lagi memakai `users.updated_at`. Bila riwayat 002 lama tanpa penanda atau checksum berbeda, runner/003 gagal sebelum service start sehingga trafik tidak dibuka | FR-001/034/036; AT-25/27/28 | `002`/`003`, P3 ADDENDUM C; tes clean-install/upgrade/legacy-gate + tes API migrasi (`admin-identity-remediation.test.ts`) | PASS | Sesi produksi nyata belum diperiksa; salinan produksi/staging tetap `NEEDS VERIFICATION`. Riwayat 002 lama tidak dapat direkonstruksi otomatis dan butuh rekonsiliasi Owner (P3 C3) |
| Migrasi runner belum aman untuk clean install dan upgrade; rentang ID Admin `01401400000` tidak konsisten | Runner menangani `schema_migrations`/checksum/advisory lock; `00000` dianggap perlu backfill di preflight, dilarang constraint, dialokasikan mulai `00001`; allocator serial dan `ADMIN_ID_EXHAUSTED`; seed idempoten. Bukti gate: checksum 002 historis berbeda dan 002 lama tanpa penanda sama-sama menghentikan runner sebelum 003 | FR-001/034/036; AT-25/27/28 | P3 ADDENDUM C; `admin-identity-remediation.test.ts` (clean install, upgrade, dua gate fail-safe) | PASS | Uji di salinan produksi tetap `NEEDS VERIFICATION` |
| Dua operasi Admin bersamaan dapat menghapus semua Admin aktif; self-action; keputusan role/ID dari state basi | `updateUser` dan `deactivateUser` mengunci himpunan Admin aktif (`ORDER BY id`) lalu membaca target dengan `FOR UPDATE OF u`; keputusan role/last-Admin/ID memakai snapshot terkunci. Transaksi role+ID+enrollment+assignment+session revocation tetap atomik. Tes regresi dua mutasi bersamaan pada akun target yang sama membuktikan hasil akhir koheren | FR-001/034/036; AT-25/27/28 | `admin.service.ts`, `admin.repository.ts`; `admin-identity-remediation.test.ts` (2 mutasi bersamaan, self-action, lintas akun ≥1 Admin) | PASS | Deadlock/throughput produksi belum diukur; beban konkuren nyata `NEEDS VERIFICATION` |
| `notRespondedCount`/`notSubmittedCount` (field kompatibilitas C1-05) dan definisi agregasi belum konsisten di API | `notRespondedCount` dikembalikan ke definisi ACTIVE enrollment + sesi aktif + filter kategori, terpisah dari statistik origin-category; `notSubmittedCount` tetap definisi lama; output `definitions` ditambahkan | FR-029–033/048; AT-05/17/24/34; C1-05 | `analytics` integration tests, P4 | PASS | Status ini hanya untuk lapisan API. Field kompatibilitas tetap ada di kontrak dan memang di luar kriteria metrik resmi dosen per C1-05; tidak boleh dibaca sebagai status UI |
| Klaim "UI Analytics tidak merender metrik di luar metrik resmi" | **Diperbaiki.** Semua render total `notRespondedCount`, `notSubmittedCount`, dan "Field kompatibilitas API" dihapus dari layar Analytics (main, empty, daftar feedback). Field API/domain/repository tetap ada sesuai kontrak. Metrik resmi (responden, definisi agregasi, 0% vs belum merespons, filter kategori asal, Kinesthetic) tetap tampil | FR-029–033; AT-24/34; C1-05 | 3 tes Compose emulator (semantik: 0 kemunculan total terlarang), 7 unit test Analytics, screenshot + dump di `docs/audit/evidence/analytics-c1-05/`, P4 ADDENDUM B | PASS | Bukti layar memakai data fixture/repository fake, bukan backend live; E2E dosen→API→layar tetap `NOT RUN` |
| 0% vs belum merespons serta angka setelah logout/ganti VARK/LEFT (skenario 80 & 100 → 2 penilaian final, rerata 90) | Backend: agregasi Kinesthetic memakai seluruh nilai final, termasuk setelah enrollment `LEFT`; V/A/R tetap terpisah | FR-029–033/048; AT-24/34; C1-05 | `analytics-origin.test.ts` #4 (`assessedCount 2`, `meanScore 90`), `analytics.test.ts` | PASS | Status ini hanya untuk lapisan API/DB; tidak membuktikan render UI |
| UI membedakan "belum merespons" dari 0% dan menampilkan angka 80/100 setelah logout, ganti VARK, `LEFT` | State/ViewModel memetakan nilai `null` sebagai "belum merespons"; layar Analytics nyata belum dijalankan di perangkat pada run final | FR-029–031; AT-34 | `LecturerAnalyticsViewModelTest` (unit), P4 | NEEDS VERIFICATION | Lapisan ViewModel PASS di unit; lapisan layar nyata `NOT RUN`; status API tidak boleh dibaca sebagai status UI |
| Deskripsi topik tidak bisa dikosongkan; reorder/delete tidak meneruskan kegagalan HTTP | PATCH membedakan `null` (kosongkan) vs omitted (tidak berubah); reorder/delete memetakan kegagalan ke `AppFailure.Api` dan memuat ulang urutan server | FR-009/013/017; AT-23 | `TopicDtos.kt`, `TopicRepositoryImpl`, `LecturerTopicsUpdateViewModelTest`, `LecturerTopicsFailureRecoveryViewModelTest`, P5 | PASS | Status ini untuk API + unit; render/edit UI perangkat `NOT RUN` (lihat AT-23 di §16.6) |
| Normalisasi jawaban/komentar Kinesthetic berbeda dari backend ECMAScript `trim` | `P5TextRules.ecmaTrim`/`normalizeAnswerText`/`normalizeCommentText` menyamakan trim ECMAScript termasuk BOM/NBSP/U+2007/U+202F/U+1680/U+205F/U+3000 dan mempertahankan U+001C–U+001F; backend tetap penentu penerimaan | FR-043/047; AT-11/15 | `P5TextRules` unit tests (10/10), P5 addendum | PASS | Status untuk unit + helper emulator; E2E submit ke backend `NEEDS VERIFICATION` karena backend tetap penentu akhir |
| Bukti regresi/restore belum final dan pemetaan RTM beberapa AT perlu dikoreksi | Koreksi cross-check kedua: pemetaan AT-04/11/15/18–21/23 diperbaiki di §16.6; matriks lama ditandai SUPERSEDED; token di artefak kompatibilitas diredaksi; lapisan bukti dipisah | Semua paket | §16.1.1, §16.6, P2/P3 addendum, run final §16.2/§16.3 | PASS untuk eksekusi ulang suite; sisa risiko per lapisan tercatat | Viewer media nyata, E2E UI penuh, staging/OQ-05, TTL produksi, restore produksi tetap `NOT RUN`/`NEEDS VERIFICATION` |

#### 16.1.1 Pemisahan lapisan bukti (koreksi cross-check kedua)

Status per lapisan hanya PASS/FAIL/NOT RUN/NEEDS VERIFICATION. Bukti run sebelumnya
dipertahankan sebagai riwayat; koreksi ini tidak menaikkan status lapisan yang belum
dieksekusi.

| Lapisan | Cakupan | Status | Bukti/catatan |
| --- | --- | --- | --- |
| API/backend | Suite backend, integrasi material/analytics/admin, guard kompatibilitas, typecheck/lint/build | PASS | **26 berkas / 219 tes PASS** pada run final Lead (§16.2), termasuk 16 tes remediasi Admin, 6 tes guard kompatibilitas, dan tes API migrasi; typecheck/lint/build PASS |
| ViewModel/unit JVM Android | 132 unit test: guard header versi, validasi P5, analytics VM, topic update/failure recovery, material DTO | PASS | §16.3 run final Lead: 29 berkas / 132 tes, 0 gagal; test Analytics C1-05 ikut dijalankan |
| Resource/helper emulator (instrumentasi) | `LoginScreenTest` 4/4, `AndroidMaterialFileStoreTest` 3/3, `P5ValidationInstrumentedTest` 3/3, `ExampleInstrumentedTest` 1/1, `LecturerAnalyticsTotalsNotRenderedTest` 3/3; total 14/14 tanpa `CaptureScreensTest` | PASS | §16.3. Riwayat run awal `CaptureScreensTest` tetap dipertahankan: 15/16 skenario gagal karena harness screenshot PO butuh backend live + fixture admin legacy; kelas dikecualikan dari run final dan bukan bukti regresi. Tes Analytics dijalankan pada emulator dengan ViewModel produksi + repository fake |
| Layar nyata — kompatibilitas rollout (emulator + backend live) | APK kandidat: login, daftar/isi materi, Analytics; APK baseline: penolakan 426 pada sesi aktif | PASS | `compat-e2e/real-screen-evidence/` (screenshot + UI dump), `client-compatibility-api.json`, `client-compatibility-server-evidence.json`, runbook §2/§3/§6 |
| Layar nyata — alur UI penuh | Edit/publikasi/urutan/arsip topik, form Admin, viewer PDF/audio/video nyata, E2E dosen→API→Analytics | NOT RUN | Matriks P2 §9.5.1 hanya controlled failure; Analytics layar diuji dengan repository fake (P4) dan render new×new di emulator + backend live (runbook §6); edit/arsip topik dan viewer media nyata belum dijalankan |
| Restore lokal | Dump/restore dev lokal (44.029 byte) dengan skor final 92 + komentar dan materi FILE; rollback utuh snapshot pra-002 + verifikasi backend lama | PASS | §16.5; `rollback-pre-002/README.md`; bukti ini restore dev lokal, bukan restore produksi; RPO/RTO, dump produksi, dan waktu henti produksi tetap `NEEDS VERIFICATION` |
| Staging/produksi | OQ-05 p95, TTL R2/produksi, restore produksi, dampak 003 pada sesi Admin nyata | NOT RUN | §16.8/OQ-05; status tidak dinaikkan oleh koreksi ini |

### 16.2 Backend final

Database baru `eduvark_test_release`, migrasi `001`+`002`+`003` dari nol, lalu suite penuh:

```bash
cd backend
ALLOW_TEST_DB_RESET=1 \
DATABASE_URL=postgresql://eduvark:eduvark@localhost:5432/eduvark_test_release \
npm test
```

Hasil: **26 berkas tes, 219 tes PASS, 0 gagal, 0 skipped** (termasuk 16 tes
`admin-identity-remediation.test.ts` dan 6 tes `client-compatibility.test.ts`;
run final Lead 24 Nov 2026, log `/tmp/eduvark-final-logs/backend-final.log`). Lanjutan:

```bash
npm run typecheck
npm run lint
npm run build
```

Hasil: `typecheck` PASS, `lint` PASS, `build` PASS (`BACKEND_CHECKS=PASS`). Temuan
cross-check yang memicu perubahan: validasi ID Admin, migrasi `002`/`003`, session
revocation, preflight, seed idempoten, analytics `notRespondedCount`, route konten
materi, dan penguncian mutasi Admin.

Catatan remediasi kedua: `002_admin_login_ids.sql` kini mencatat penanda
`admin_identity_changes` dan mencabut sesi akun yang benar-benar di-re-ID dalam
transaksi yang sama; `003` tidak lagi memakai `users.updated_at`, hanya
menormalkan `01401400000` + mencabut sesinya, dan gagal eksplisit bila riwayat
002 lama tidak punya penanda. Transaksi role/ID/enrollment/assignment/session
tetap atomik. Rincian per kriteria ada di `docs/audit/evidence/P3.md` ADDENDUM C.

### 16.3 Android final

Semua perintah lewat wrapper Gradle berseri `/tmp/eduvark-gradle-locked.sh`
(tanpa `clean`, tanpa Gradle paralel):

- `assembleDebug` → BUILD SUCCESSFUL; APK `app/build/outputs/apk/debug/app-debug.apk`,
  `versionCode=2`, `versionName=1.1.0`, SHA-256
  `fa20e4635762824c194cf832a70c368355d31b4b4919796a7a2956d5b25091ed` (artefak yang diuji
  di emulator untuk blocker 1).
- Unit test: **29 berkas, 132 tes, 0 failure, 0 error, 0 skipped** (termasuk 2 tes baru
  `ClientCompatibilityInterceptorTest`, `AndroidMaterialFileStoreTest`, `P5TextRules`,
  `StudentCaseViewModelTest`, `LecturerAnalyticsViewModelTest`,
  `LecturerTopicsViewModelTest`, dan 4 unit C1-05).
- `assembleRelease` → BUILD SUCCESSFUL.
- `lintDebug` → BUILD SUCCESSFUL (dua peringatan format string non-positional
  di `strings_lecturer_b.xml` dan `strings_p5.xml`; tidak menggagalkan build).
- Instrumentasi emulator `Pixel_10_Pro_XL` (API 37), mengecualikan
  `CaptureScreensTest` sesuai keputusan di bawah: **14/14 PASS pada artefak final**
  (`versionCode=2`; run 24 Nov 2026) —
  `AndroidMaterialFileStoreTest` 3, `LoginScreenTest` 4, `P5ValidationInstrumentedTest` 3,
  `ExampleInstrumentedTest` 1, `LecturerAnalyticsTotalsNotRenderedTest` 3.
- Bukti layar Analytics C1-05: screenshot main/feedback-list/empty + dump
  semantik permanen di `docs/audit/evidence/analytics-c1-05/`; dump menunjukkan
  total terlarang **0 kemunculan** sementara metrik resmi (responden, definisi
  agregasi, filter kategori asal, Kinesthetic) tetap muncul. Data berasal dari
  ViewModel produksi + repository fake; E2E backend live **NOT RUN**.
- `CaptureScreensTest` tetap dicatat sebagai **run awal gagal** (15/16 skenario gagal
  karena harness screenshot PO membutuhkan backend live + fixture admin legacy +
  prasyarat emulator). Kelas itu dikecualikan dari run final dan tidak diubah; bukan
  bukti regresi remediasi. Kegagalan awal ini dipertahankan sebagai riwayat, bukan
  disembunyikan.

### 16.4 Matriks kompatibilitas lintas versi (eksekusi nyata, kontrol versi aktif)

**Sumber acuan saat ini: `docs/audit/evidence/ROLLOUT-RUNBOOK.md`.** Bagian ini adalah ringkasan
hasil; runbook memuat urutan cutover, release hold, window URL lama, rollback, dan approval Owner.
Dijalankan dengan **APK baseline** `b7b6cd6` (hash `9b5756a0…`, `versionCode=1`) dan **APK kandidat**
working tree (hash `fa20e463…`, `versionCode=2`); backend kandidat (schema `001+002+003`, guard
`CLIENT_COMPATIBILITY_ENFORCE=1`) dan backend lama `b7b6cd6` pada port terpisah; emulator
`Pixel_10_Pro_XL` API 37 + `adb reverse`.

| Kombinasi | Login | FILE/materi | Analytics | Status kombinasi |
| --- | --- | --- | --- | --- |
| **Android baseline + backend kandidat** | **PASS (ditolak terdefinisi)** — login tanpa header/versi 1 → `426 CLIENT_VERSION_UNSUPPORTED`; sesi Admin lama yang sudah aktif → `/auth/me` 426, layar menampilkan pesan backend | **FAIL/tertahan** — seluruh API materi ditolak 426 sebelum kontrak data; tidak ada silent no-op | **FAIL/tertahan** — API Analytics ditolak 426; layar tidak menerima data lama | **PASS sebagai kontrol; APK lama tidak bisa memakai backend baru** |
| **Android kandidat + backend kandidat** | **PASS** — header versi 2, login mahasiswa & dosen 200 | **PASS** — topik/materi 200, `/materials/:id/content` 200, handoff FileProvider; render PDF nyata **NOT RUN** (stub 51 B) | **PASS** — `/lecturer/analytics/topics/:id` 200; layar menampilkan Visual 75%, Aural 60%, R/W —, Kinesthetic 92,0/100 | **PASS — pasangan rilis** |
| Android kandidat + backend lama | NOT RUN untuk artefak final | NOT RUN | NOT RUN | **NOT RUN** (bukan jalur rilis) |
| **Android baseline + backend lama** | **PASS (real-screen + API)** — login Admin `01401400001` 200, beranda admin tampil | NOT RUN | NOT RUN | **PASS untuk login (baseline rollback)** |

Temuan rollout penting:

1. **Kontrol versi kini nyata, bukan asumsi.** APK kandidat mengirim
   `X-EduVARK-Client-Version: 2`; baseline tidak mengirim header. Backend menolak 426
   **sebelum autentikasi/kontrak data**, sehingga APK lama tidak lagi menerima bentuk respons
   yang tidak bisa diparse. `/health` tetap 200; aturan auth/otorisasi tidak diubah.
2. **Sesi lama juga ditahan.** Pada emulator, sesi Admin lama yang aktif sebelum switch memanggil
   `/auth/me` ke backend kandidat dan menerima 426; layar menampilkan pesan penolakan. Ini
   membuktikan kontrol bekerja pada sesi yang sudah berjalan, bukan hanya login baru.
3. **Release hold belum terbukti (FAIL).** Environment `homelab-testing` hanya punya branch policy,
   `can_admins_bypass: true`, tanpa required reviewers; workflow deploy otomatis setelah CI `main`
   sukses. Karena itu **merge ke `main` dan cutover produksi NO-GO** sampai hold diaktifkan dan
   dibuktikan (runbook §7).
4. **Migrasi bukan job otomatis `server.ts`.** Deployment harus menjalankan migrasi sebelum backend
   kandidat melayani; rollback otomatis script hanya mengembalikan image, bukan DB.
5. **Window URL lama** dihitung dari penerbitan terakhir + TTL maksimum yang terbukti. Backend
   kandidat tidak lagi menerbitkan `signedUrl`; route lama menolak tanda tangan kedaluwarsa
   **403 `SIGNED_URL_EXPIRED`**. TTL lokal terukur 299,349 detik; TTL produksi/R2 tetap
   **NEEDS VERIFICATION**. Penolakan klien lama (426) dan kedaluwarsa URL adalah dua kontrol
   terpisah.
6. **Rollback sah = image lama + restore snapshot pra-002 + revoke sesi**, bukan image lama di atas
   DB 002/003. Simulasi lokal penuh PASS di level API/endpoint; real-screen APK lama × backend lama
   pasca-restore **NOT RUN**. Lihat `docs/audit/evidence/rollback-pre-002/README.md`.
7. Status terbuka: staging/produksi **NOT RUN**, render media nyata **NOT RUN**, TTL produksi/R2
   **NEEDS VERIFICATION**, restore produksi/RPO/RTO **NEEDS VERIFICATION**, OQ-05 **NOT RUN**,
   release hold **FAIL**.

### 16.5 Restore lokal final (representatif)

Fixture disiapkan pada DB terisolasi: kelas, enrollment ACTIVE, topik PUBLISHED,
materi FILE PDF dengan objek nyata di storage lokal, studi kasus, submission, dan
penilaian final **92** dengan komentar. Dump:

```bash
pg_dump ... -Fc -f /tmp/eduvark-restore-final-20261124T122302Z.dump
```

Hasil: dump **44.029 byte**; `pg_dump` 0,623 s; create DB 0,327 s; `pg_restore` 0,503 s;
verifikasi daftar objek 0,041 s; total **1,494 s**.

Verifikasi pada DB hasil restore (`eduvark_restore_verify`, storage lokal yang sama):
`contentStatus 200`, `contentHashMatches true` (SHA-256
`904636248025ad20fb9c6bd8b700179a2a42edb5df3636e926c7e09055ee3f75`),
`studentDetailStatus 200`, `studentScore 92`, `studentAnswerMatches true`,
`studentCommentMatches true`, `lecturerDetailStatus 200`, `lecturerScore 92`,
`topicVisible true`, `checksumsEqual true` (tabel domain; `auth_sessions`/`audit_logs`
dikecualikan karena bertambah saat login verifikasi).

Batas: ini restore dev lokal, bukan restore produksi. RPO/RTO, dump produksi, ukuran
data nyata, dan waktu henti produksi tetap `NEEDS VERIFICATION` (OQ-05, §16.8).

### 16.6 Pemetaan RTM yang dikoreksi (dicatat di laporan; RTM tidak diubah)

Koreksi cross-check kedua: pemetaan AT-04/11/15/23 pada run sebelumnya salah
(tertukar dengan edit topik, reorder, analytics, dan normalisasi P5). Tabel di
bawah mengikuti RTM dan ditambah koreksi **AT-17** (backend PASS, Android/emulator NOT RUN).
AT-04 diturunkan menjadi **NOT RUN** untuk layar nyata karena bukti yang ada hanya
resource/ViewModel, bukan alur `StudentCaseScreen`; jangan membaca status ViewModel sebagai
status layar. Status per lapisan hanya memakai PASS/FAIL/NOT RUN/NEEDS VERIFICATION. Kolom **Status** adalah status
lapisan yang dituntut RTM, bukan status semua lapisan; kolom lapisan mencatat
bukti apa adanya.

| AT | Cakupan RTM | Bukti aktual | API | ViewModel | Resource/helper emulator | Layar nyata | Staging | Status |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| AT-04 | Pesan yang disepakati secara persis saat topik terbit belum punya materi kategori (FR-013/014/016) | String ID `materi belum tersedia, harap hubungi dosen terlebih dahulu` + padanan EN (`values-en`); `P5ValidationInstrumentedTest.publishedTopicWithoutMaterialsShowsExactIndonesianMessage` merender teks resource, **bukan** `StudentCaseScreen`; `StudentCaseStateViewModelTest` 7/7 (empty ≠ error). Layar nyata alur materi kosong belum dijalankan | NOT RUN | PASS | PASS (resource; komposabel utuh belum diuji) | NOT RUN | NOT RUN | **NOT RUN** — hanya resource/ViewModel yang terbukti; AT-04 belum boleh disebut PASS |
| AT-11 | Tolak skor noninteger / di luar 0–100 dan komentar >1.000 karakter (FR-026/043) | `grade-validation.test.ts` 12/12; `LecturerGradeValidationViewModelTest` 6/6; `P5ValidationInstrumentedTest.manualGradeInputIsNotSilentlyAltered` (device; tanpa clamp/truncate) | PASS | PASS | PASS | NEEDS VERIFICATION | NOT RUN | PASS |
| AT-15 | Jawaban kosong, batas 1 dan 10.000 karakter, serta panjang melebihi batas (FR-024/038/047/050) | `answer-length.test.ts` 12/12; `P5TextRulesTest` 10/10; `P5ValidationInstrumentedTest.questionCounterUsesUnicodeCodePointsIncludingEmoji` (device, 10.000/10.001 emoji) | PASS | PASS | PASS | NEEDS VERIFICATION | NOT RUN | PASS |
| AT-17 | Status "Tidak Mengumpulkan" setelah deadline tanpa nilai nol atau kiriman palsu (FR-042/048) | Backend: `case-boundary.test.ts` + `cases.test.ts` (status `TIDAK_MENGUMPULKAN`, `submittedAt` null, kiriman terlambat 409); `assessments.test.ts` (2 baris, `score` null, `submissions` count 0). Android: enum + string + render `ClosedCase`/Home hanya inspeksi kode; tidak ada tes JVM/Compose/emulator pasca-deadline | PASS | NOT RUN | NOT RUN | NOT RUN | NOT RUN | **NEEDS VERIFICATION** — backend PASS; lapisan Android/layar belum dijalankan |
| AT-18 | Alamat tetap melayani permintaan sah baru mahasiswa berkategori sesuai dan dosen berwenang (FR-007/014/015) | `material-access.test.ts` #3 (content-type/disposition/length/no-store/vary/accept-ranges/etag); Android `StudentMaterialsOpenTest` 6/6 + `HttpMaterialContentSource`; matriks §9.5.1 hanya mengeksekusi controlled failure | PASS | PASS | PASS | NEEDS VERIFICATION | NOT RUN | PASS |
| AT-19 | Alamat berkas yang sudah diketahui ditolak setelah logout maupun setelah sesi kedaluwarsa (FR-001/015) | `material-access.test.ts` #4 (logout dan sesi kedaluwarsa → 401/403 tanpa isi); Android `StudentCaseOpenTest` (pemetaan 401/403/404) | PASS | PASS | NOT RUN | NEEDS VERIFICATION | NOT RUN | PASS |
| AT-20 | Enrollment LEFT menutup unduhan/akses mahasiswa; dosen berwenang tetap melihat riwayat; reaktivasi memulihkan akses (FR-015/027/028/034) | `material-access.test.ts` #5 (`LEFT` → 403 `NOT_ENROLLED`, `ACTIVE` → 200); `academic-access.test.ts` (ACTIVE→LEFT→ACTIVE, dosen tetap) | PASS | NEEDS VERIFICATION | NOT RUN | NEEDS VERIFICATION | NOT RUN | PASS |
| AT-21 | Unpublish topik dan soft delete materi masing-masing menutup unduhan baru melalui alamat yang sama (FR-015) | `material-access.test.ts` #6; streaming unduhan + cleanup parsial di `AndroidMaterialFileStoreTest` 3/3; viewer media nyata belum diuji | PASS | PASS | PASS | NEEDS VERIFICATION | NOT RUN | PASS |
| AT-23 | Pengelolaan topik: edit judul/deskripsi lewat Android, publikasi, urutan, hapus/arsip sesuai perlindungan data akademik (FR-009/013/017) | `topic-edit-and-archive.test.ts` 6/6 (`null` vs omitted, 409 `TOPIC_HAS_ACADEMIC_DATA`); `topic-order-pagination.test.ts` 6/6; `LecturerTopicsUpdateViewModelTest` 5/5; `TopicRepositoryRequestAndFailureTest` 7/7; `LecturerTopicsFailureRecoveryViewModelTest` 5/5 | PASS | PASS | NOT RUN | NOT RUN | NOT RUN | NEEDS VERIFICATION |

Catatan: AT-23 tetap `NEEDS VERIFICATION` karena RTM menuntut pengelolaan topik
"lewat Android", sedangkan layar edit/publikasi/urutan/arsip belum dijalankan di
perangkat pada run final. Pemetaan AT lain (AT-24/25/27/28/34) tidak diubah dan
tetap merujuk RTM.

### 16.7 Status akhir temuan

Remediasi ini **tidak mengubah** `Dokumen-SRS.docx`, `docs/DECISIONS.md`, RTM,
migrasi `001_init.sql`, `.github`, atau konfigurasi produksi. Working tree memang
sudah memuat perubahan Owner pada `Dokumen-SRS.docx` dan aset Android sebelum sesi
ini (mtime SRS 24 Nov 11:16); perubahan itu dipertahankan dan bukan hasil remediasi.
Tidak ada commit/push/PR/merge. Semua bukti di §16 diambil dari working tree tersebut.

| Temuan | Perbaikan | FR/AT | Tes atau artefak | Status | Risiko tersisa |
| --- | --- | --- | --- | --- | --- |
| Mutasi Admin bersamaan dapat menghapus semua Admin aktif / memakai role basi | Lock himpunan Admin aktif (`ORDER BY id`) + target `FOR UPDATE`; keputusan role/ID/last-Admin dari snapshot terkunci; transaksi role+ID+enrollment+assignment+revoke atomik | FR-001/034/036; AT-25/27/28 | `admin-identity-remediation.test.ts` (2 mutasi akun sama, self-action, lintas akun), suite 219/219 | PASS | Beban konkuren produksi NEEDS VERIFICATION |
| Sesi Admin lama tetap aktif setelah re-ID (003 memakai `updated_at`) | 002 mencatat `admin_identity_changes` + mencabut sesi akun yang berubah atomik; 003 hanya `00000`; runner menolak checksum/riwayat lama (wajib dijadikan job gate sebelum start service karena server tidak auto-migrate) | FR-001/034/036; AT-25/27/28 | Tes clean install/upgrade/legacy-gate/API migrasi; P3 ADDENDUM C; suite 219/219 | PASS | Sesi produksi NEEDS VERIFICATION; gate migrasi adalah tanggung jawab deployment (server tidak auto-migrate); riwayat 002 lama perlu rekonsiliasi Owner |
| Klien lama tidak kompatibel dengan backend baru; gate versi tidak ada | APK kandidat mengirim header versi 2; backend menolak klien tanpa header/versi 1 dengan 426 sebelum auth/data; runbook menetapkan release hold + coordinated maintenance + rollback paket | FR-015/029–033; AT-05/18–21/24/34; C1-02 | `compat-e2e/client-compatibility-*.json`, `real-screen-evidence/`, `ROLLOUT-RUNBOOK.md`, §16.4 | PASS (kontrol klien) | Staging/produksi NOT RUN; release hold FAIL sehingga merge NO-GO; TTL produksi NEEDS VERIFICATION |
| UI Analytics merender total `notRespondedCount`/`notSubmittedCount` | Render total dihapus dari main/empty/daftar feedback; field API tetap; metrik resmi dipertahankan | FR-029–033; AT-24/34; C1-05 | 3 tes Compose emulator, 4 unit C1-05, screenshot+semantik `evidence/analytics-c1-05/`, unit 132/132 | PASS | Bukti layar memakai repository fake; E2E dosen→API→layar NOT RUN |
| Pemetaan RTM/AT dan klaim historis tidak konsisten; token di artefak | §16.6 dipetakan ke RTM; matriks lama SUPERSEDED; token `accessToken`/`refreshToken` diredaksi; lapisan bukti dipisah | Semua paket | §16.1.1, §16.6, P2/P3/P4 addendum, `new-backend-numeric-admin-login-200.json` | PASS | Klaim lapisan yang belum dieksekusi tetap NEEDS VERIFICATION |
| Bukti regresi/restore belum final | Suite backend/Android/emulator dijalankan ulang; restore lokal dipertahankan sebagai bukti lokal | Semua paket | §16.2/§16.3/§16.5 | PASS | Restore produksi/RPO/RTO NEEDS VERIFICATION; viewer media nyata, E2E UI penuh, staging/OQ-05 tetap NOT RUN/NEEDS VERIFICATION |

### 16.8 NEEDS VERIFICATION / NOT RUN tersisa

| Butir | Alasan | Tindakan sebelum cutover produksi |
| --- | --- | --- |
| Viewer PDF/audio/video nyata di perangkat | Tidak ada objek media nyata di env uji | Uji dengan materi nyata di staging/device |
| TTL efektif signed URL produksi/R2 | Hanya TTL lokal 300 s yang terbukti (1 s → 403 pada 2,2 s) | Ukur di produksi; tetapkan panjang jendela transisi |
| Restore produksi, RPO/RTO | Hanya dump dev lokal | Uji dump produksi di lingkungan terisolasi |
| OQ-05 benchmark staging | Staging tidak tersedia | Ukur p95 baca/tulis dan media |
| Android lama + backend lama | Baseline sudah diuji untuk login/beranda admin (real-screen), tetapi FILE/Analytics pasca-restore **NOT RUN** | Ulangi verifikasi perangkat lengkap setelah restore di staging |
| `CaptureScreensTest` | Harness PO butuh backend live + admin legacy | Perbarui fixture/harness atau tugas terpisah |
| Rollout/kompatibilitas di staging/produksi | Hanya emulator + backend lokal terisolasi | Jalankan runbook di staging, lalu production dengan approval |
| Verifikasi akhir URL lama tidak menyajikan konten | Tidak ada akses production; window belum lewat | Setelah `t_last + T_max` terverifikasi, buktikan 403/404 |
| Render PDF/audio/video nyata APK baru | Fixture media hanya stub 51 B | Uji dengan objek media nyata di staging/device |
| APK lama × backend lama | Tidak dijalankan pada remediasi ini | Tidak perlu bila klien lama segera dihentikan; jika perlu, jalankan baseline |
| Release hold GitHub environment | Environment `homelab-testing` tanpa required reviewers dan `can_admins_bypass: true`; deploy otomatis setelah CI `main` | Aktifkan required reviewers/non-bypass atau gate hold; buktikan deploy tertahan sebelum merge (butuh approval terpisah) |
| Deployment otomatis vs runbook | Script tidak menutup trafik, smoke memakai URL publik, auto-rollback hanya image | Jalankan urutan runbook §5 manual; smoke ke origin internal kandidat; siapkan restore DB pra-002 |
| Rollback utuh pasca-restore layar nyata | Simulasi rollback pre-002 lulus di level API/endpoint; real-screen APK lama × backend lama pasca-restore NOT RUN | Ulangi verifikasi perangkat setelah restore di staging |
| Persetujuan Owner | Cutover, rollback, T_max, revoke sesi Admin, release hold/workflow changes | Product/Engineering/Infra/Security Owner (runbook §7–§8) |

### 16.9 Perintah verifikasi yang dapat direproduksi

```bash
# Backend
cd backend
ALLOW_TEST_DB_RESET=1 DATABASE_URL=postgresql://eduvark:eduvark@localhost:5432/eduvark_test_release npm test
# hasil final: 26 berkas / 219 tes PASS (log /tmp/eduvark-final-logs/backend-final.log)
ALLOW_TEST_DB_RESET=1 DATABASE_URL=postgresql://eduvark:eduvark@localhost:5432/eduvark_test_release \
  npx vitest run tests/integration/admin-identity-remediation.test.ts tests/integration/client-compatibility.test.ts
# hasil final: 16/16 + 6/6 PASS
npm run typecheck && npm run lint && npm run build

# Android (serial)
/tmp/eduvark-gradle-locked.sh :app:testDebugUnitTest --console=plain
/tmp/eduvark-gradle-locked.sh :app:assembleDebug :app:assembleRelease :app:lintDebug --console=plain
/tmp/eduvark-gradle-locked.sh :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.notClass=com.eduvark.app.CaptureScreensTest --console=plain

# Bukti kompatibilitas + runbook
sed -n '1,80p' docs/audit/evidence/ROLLOUT-RUNBOOK.md
ls docs/audit/evidence/compat-e2e/
```

### 16.10 Penutupan penghambat kesiapan rilis — status untuk review Owner

Tambahan ini menjawab empat penghambat kesiapan rilis. **Tidak ada** commit/push/PR/merge/deploy,
tidak ada akses staging/produksi, dan tidak ada perubahan workflow/repo settings. `AGENTS.md`
**tidak ditemukan** di repo (root maupun sampai kedalaman 3); ketiadaannya dicatat, bukan
digantikan dengan asumsi.

| Penghambat | Perbaikan | Bukti aktual | Status | Persetujuan yang masih diperlukan |
| --- | --- | --- | --- | --- |
| APK baseline masih bisa memakai API baru; belum ada kontrol klien yang terbukti | Header versi 2 di APK kandidat; guard backend `CLIENT_MIN_VERSION=2` menolak tanpa header/versi 1 dengan 426 sebelum auth/kontrak data; health tetap 200; aturan auth/otorisasi tidak diubah | APK kandidat hash `fa20e463…`/`versionCode=2`; APK baseline hash `9b5756a0…`; API `client-compatibility-api.json`; sesi Admin lama aktif → `/auth/me` 426 + layar pesan backend (`real-screen-evidence/`); 6 tes guard PASS | **PASS (lokal)** | Smoke ulang pada artefak final di staging; persetujuan build/distribusi APK |
| Sesi APK baseline sudah aktif sebelum switch | Skenario emulator: login baseline ke backend lama → beranda admin → switch `adb reverse` ke backend kandidat tanpa logout → `/auth/me` 426 dan layar penolakan; verifikasi API vs layar dipisah | `old-apk-active-session-before-switch.png`, `old-apk-active-session-blocked-426.png/.xml`, log server (`client-compatibility-server-evidence.json`) | **PASS (lokal)** | Ulangi setelah cutover nyata di staging |
| C1-02: URL lama masih terbit / umur URL tidak dipisahkan dari penolakan klien | Backend kandidat tidak menerbitkan `signedUrl`; route lama hanya membaca tautan yang sudah terbit dan menolak signature kedaluwarsa; window = `t_last_issue + T_max`; TTL lokal terukur 299,349 s; TTL produksi/R2 eksplisit NV | `client-compatibility-api.json` (materi `url/signedUrl/expiresAt` null; expired URL dengan header 2 → 403 `SIGNED_URL_EXPIRED`); `old-signed-url-ttl.json` (signature diredaksi) | **PASS (lokal)** untuk berhenti menerbitkan + expiry; **NEEDS VERIFICATION** TTL produksi | Ukur TTL R2/produksi dan buktikan 403/404 setelah window |
| Rollback utuh memakai snapshot yang salah (pra-003, bukan pra-002) | Snapshot pra-002 + restore ke DB bersih + image/APK lama; simulasi penuh lokal: baseline → snapshot → migrasi 002/003 → verifikasi baru → restore → verifikasi lama; data loss sebelum/sesudah tulisan dipisah; sesi pasca-restore harus direvoke/rotate | `rollback-pre-002/README.md`; snapshot sha256 `5aedcca2…`; semua pemeriksaan lokal PASS; real-screen lama×lama pasca-restore **NOT RUN**; image-only rollback terbukti bukan rollback penuh | **PASS (lokal, API/endpoint)**; real-screen pasca-restore **NOT RUN** | Uji restore produksi/RPO/RTO dan verifikasi perangkat setelah restore |
| Release hold belum ada; merge ke `main` memicu deploy otomatis | Audit environment: hanya branch policy, `can_admins_bypass: true`, tanpa required reviewers. Runbook mewajibkan hold + bukti deploy tertahan sebelum merge | `release-hold/EVIDENCE.md`, `gh-environment.json`, `gh-branch-policies.json`, `gh-workflow-runs.json` | **FAIL / NOT RUN** — hold belum terbukti | Persetujuan terpisah untuk mengubah GitHub environment/workflow: required reviewers, non-bypass, atau gate hold; bukti aktivasi |
| Runbook tidak sesuai perilaku deployment otomatis | Runbook diselaraskan: release hold → tutup/drain trafik → snapshot pra-002 → migrasi job → start kandidat → smoke internal → verifikasi 426 → reopen; auto image-rollback diakui image-only; smoke publik tidak dipakai saat trafik ditutup; simulasi lokal alur deploy/rollback | `ROLLOUT-RUNBOOK.md` §5/§7/§8/§9; `deploy-sim/EVIDENCE.md` (33/33 assertion PASS, termasuk migrasi gagal dan auto image-rollback); simulasi **bukan** Docker/jaringan produksi | **PASS (dokumen + simulasi lokal)**; deployment nyata **NOT RUN** | Persetujuan perubahan workflow/repo settings; uji runbook di homelab/staging |
| Bukti mentah masih memuat signature/token; status AT-04/AT-17 salah | Signature `old-signed-url-ttl.json` diredaksi (issue/expiry/status/assertion dipertahankan); AT-04 dikoreksi menjadi hanya resource/ViewModel (layar **NOT RUN**); AT-17 backend PASS, lapisan Android/emulator **NOT RUN** | `sanitization-report.md`; `at-corrections/AT-04.md`, `AT-17.md`; JSON evidence tervalidasi | **PASS** untuk sanitasi/koreksi; AT-04 layar **NOT RUN**; AT-17 **NEEDS VERIFICATION** | Jalankan layar AT-04 dan lapisan Android AT-17 sebelum klaim UI |
| Angka regresi backend/Android tidak sesuai snapshot kode | Suite backend final 26 berkas/219 tes PASS pada DB terisolasi; Android JVM 29 berkas/132 tes PASS; instrumentasi emulator 14/14 PASS pada artefak final; typecheck/lint/build PASS | `/tmp/eduvark-final-logs/backend-final.log`; `app/build/test-results/testDebugUnitTest/`; `app/build/outputs/androidTest-results/connected/debug/`; §16.2/§16.3 | **PASS (lokal)** | Regresi staging/produksi |
| Staging/produksi, media nyata, restore produksi, OQ-05 | Tidak ada akses staging/produksi; media fixture hanya stub 51 B | Semua status terkait tetap **NOT RUN / NEEDS VERIFICATION**; tidak diklaim PASS | **NOT RUN / NEEDS VERIFICATION** | Persetujuan akses staging/produksi + jadwal uji media, restore, benchmark |

**Verdict untuk Owner.** Pekerjaan remediasi **siap direview Owner**, tetapi **belum siap rilis**:

- Kontrol klien lama dan rollback utuh sudah punya bukti lokal yang dapat direproduksi.
- **Merge ke `main` tetap NO-GO** karena release hold tidak terbukti dan deploy otomatis akan
  berjalan setelah CI `main` sukses.
- **Cutover produksi tetap NO-GO** sampai hold/approval terpisah, staging smoke, TTL R2/produksi,
  restore produksi/RPO/RTO, dan media nyata diverifikasi.
- Bila Owner memilih menerima risiko APK lama tanpa paksa update, keputusan itu harus eksplisit;
  kontrol 426 tetap aktif, tetapi APK lama masih terpasang di perangkat.
