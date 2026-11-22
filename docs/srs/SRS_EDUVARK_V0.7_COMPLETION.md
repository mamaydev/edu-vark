# SRS EduVARK v0.7 — Completion

**Dokumen:** Penyempurnaan `SRS_EduVARK_v0.7`  
**Status:** Draft completion berbasis SRS + keputusan wawancara 20 November 2026  
**Sifat:** Analisis, dokumentasi, dan pemodelan. **Bukan implementasi.**  
**SRS asli:** `../../SRS_EduVARK_v0.7.docx` — tidak diubah.

---

## 0. Cara membaca dokumen ini

Dokumen ini melengkapi tiga bagian yang kosong pada SRS asli:

- **§6 Aturan Validasi dan Status** — aturan operasional yang dapat diuji.
- **§7 Ketidakpastian Tersisa** — register CONFIRMED/TBD/CONFLICT/RECOMMENDATION.
- **§8 Matriks Cakupan** — ringkasan RTM, cakupan use case/entitas, dan test gap.

Full RTM 50 FR berada di `REQUIREMENTS_TRACEABILITY_MATRIX.md`.  
Pertanyaan yang masih terbuka berada di `OPEN_QUESTIONS.md`.

**Prinsip:** dokumen ini tidak menaikkan status TBD menjadi CONFIRMED kecuali ada keputusan wawancara yang eksplisit (D-01–D-18). Setiap keputusan turunan yang belum disetujui tetap ditandai TBD/RECOMMENDATION.

### 0.1 Konvensi response

Response sukses:

- `200 OK` — permintaan baca/ubah berhasil.
- `201 Created` — resource baru dibuat (submission, assessment, akun, topik, materi).
- `204 No Content` — logout, soft delete, deaktivasi, reset password.

Response gagal memakai envelope yang konsisten:

- `error.code` — kode mesin, mis. `VARK_REQUIRED`, `DEADLINE_PASSED`.
- `error.message` — pesan aman untuk pengguna.
- `error.details` — daftar kesalahan field, bila ada.
- `error.requestId` — untuk korelasi log.

Waktu:

- Disimpan sebagai **UTC** (`timestamptz`).
- Ditampilkan sebagai **WIB (UTC+7)** dengan label zona waktu.
- Deadline dibandingkan memakai **waktu database**, bukan waktu klien.

### 0.2 Istilah status

| Istilah | Arti |
|---|---|
| `BELUM_MENGUMPULKAN` | Deadline belum lewat dan belum ada submission. |
| `TIDAK_MENGUMPULKAN` | Deadline sudah lewat dan tidak ada submission; **bukan nilai 0**. |
| `MENUNGGU_PENILAIAN` | Submission sudah ada, assessment belum ada. |
| `SUDAH_DINILAI` | Assessment sudah tersimpan; nilai dan komentar final. |

---

## 1. Ringkasan keputusan yang memengaruhi validasi

| ID | Keputusan | Efek pada §6 |
|---|---|---|
| D-01 | Android native, backend REST, rename EduVARK | Validasi tidak berubah; konteks klien menjadi Android |
| D-02 | PostgreSQL via Supabase | `CHECK`, `UNIQUE`, trigger, `timestamptz`, transaksi |
| D-03 | JWT access + refresh + tabel sesi | Validasi sesi memeriksa token **dan** baris sesi |
| D-04 | Satu sesi aktif; idle 8 jam, absolut 12 jam | Login baru mencabut sesi lama; timeout menghasilkan 401 |
| D-05 | Cloudflare R2, URL 5 menit, 100 MB/file | Validasi MIME/ukuran dan otorisasi sebelum signed URL |
| D-06 | `classes`, `enrollments`, `teaching_assignments` | Setiap akses materi/kasus memeriksa enrollment |
| D-07 | Soft delete topik/materi | Query wajib memfilter `deleted_at`; data akademik tetap |
| D-08 | Satu studi kasus per topik | Submission unik per mahasiswa–studi kasus |
| D-09 | Deadline terkunci setelah submission pertama; WIB | Validasi perubahan deadline dan status turunan |
| D-10 | Nilai integer 0–100; komentar opsional ≤1.000 | Validasi penilaian |
| D-11 | NIM/NIP, password sementara, admin guardrails | Validasi login, ganti password, reset, deaktivasi |
| D-12 | Mean + n + histogram; belum feedback terpisah | Validasi agregasi analitik |
| D-13 | Audit + retensi 1 tahun; keluar kelas | Validasi audit dan status enrollment |
| D-14 | Enum `AURAL`, label “Audio” | Validasi nilai VARK |
| D-15 | Supabase hanya managed PostgreSQL; auth custom; storage R2 | OQ-01 selesai; tidak ada duplikasi auth |
| D-16 | Password min. 12; access 15 menit; refresh 12 jam; lockout 5/15 menit | OQ-02 selesai; selaras D-04. **Implementasi memakai D-19: min. 6 tanpa aturan lain.** |
| D-17 | Admin mengubah enrollment menjadi `LEFT` via endpoint; histori tetap | OQ-07 selesai; BR-15 punya mekanisme |
| D-18 | UC-M01/M07 diperluas untuk semua role; staf tanpa VARK | OQ-15 selesai; FR-001 tetap butuh AT-P11 |

---

# §6 Aturan Validasi dan Status

## 6.1 Validasi autentikasi dan otorisasi

| ID | Deskripsi | Prasyarat | Validasi | Respons sukses | Respons gagal | Referensi |
|---|---|---|---|---|---|---|
| VAL-AUTH-01 | Login hanya dengan kredensial valid dan akun aktif | Akun terdaftar | Cocokkan `login_id` + password (Argon2id); status `ACTIVE` | `200` + access token, refresh token, profil, `mustChangePassword` | `401 INVALID_CREDENTIALS`; `403 ACCOUNT_INACTIVE` | FR-001, FR-002 |
| VAL-AUTH-02 | Setiap request terautentikasi membawa access token valid | Header `Authorization: Bearer` | Verifikasi signature, `exp`, `iss`, `aud`; cek sesi tidak `revoked` | Lanjut ke otorisasi | `401 TOKEN_INVALID/EXPIRED`; `401 SESSION_REVOKED` | FR-001, FR-003 |
| VAL-AUTH-03 | Refresh token hanya dapat dipakai sekali dan dirotasi | Refresh token valid | Cocokkan hash, cek `exp`, cek sesi; terbitkan token baru; token lama dicabut | `200` token baru | `401 REFRESH_INVALID` | FR-001, D-03 |
| VAL-AUTH-04 | Logout mencabut sesi aktif | Sesi aktif | Set `revoked_at`; hapus refresh token | `204` | `401` jika sesi sudah tidak valid | FR-001, UC-M07 |
| VAL-AUTH-05 | Sesi berakhir karena idle/absolut | Sesi aktif | Idle > 8 jam atau umur > 12 jam → `revoked` | — | `401 SESSION_EXPIRED`; klien logout lokal | D-04 |
| VAL-AUTH-06 | Hanya satu sesi aktif per pengguna | Login baru | Cabut sesi lama sebelum membuat sesi baru | `200` sesi baru | — | D-04 |
| VAL-AUTH-07 | Password sementara wajib diganti sebelum akses akademik | `must_change_password = true` | Hanya `/auth/me`, `/auth/change-password`, `/auth/logout` diizinkan | `200` setelah ganti | `403 PASSWORD_CHANGE_REQUIRED` | D-11 |
| VAL-AUTH-08 | Reset password oleh admin mencabut semua sesi target | Admin terautentikasi | Set password baru sementara; revoke semua sesi | `204` | `403` jika bukan admin; `404` target tidak ada | FR-034, D-11 |
| VAL-AUTH-09 | Login dibatasi rate limit | Request login | Batas per IP + `login_id` (mis. 5 gagal/15 menit) | `200` | `429 TOO_MANY_ATTEMPTS` | FR-001, NFR security |
| VAL-AUTH-10 | Akun nonaktif tidak dapat login dan sesi aktifnya dicabut | Status `INACTIVE` | Tolak login; revoke semua sesi | — | `403 ACCOUNT_INACTIVE` | FR-002, FR-034, D-11 |
| VAL-AUTH-11 | Password tidak pernah disimpan/dilog dalam bentuk plaintext | — | Hash Argon2id; redaksi field sensitif di log | — | — | NFR security |
| VAL-AUTH-12 | Role diambil dari server, bukan dari klien | Token valid | Bandingkan role baris `users` dengan allowlist endpoint | Lanjut | `403 FORBIDDEN` | FR-003, FR-004 |

## 6.2 Validasi role pengguna

| ID | Deskripsi | Prasyarat | Validasi | Respons sukses | Respons gagal | Referensi |
|---|---|---|---|---|---|---|
| VAL-ROLE-01 | Role hanya `STUDENT`, `LECTURER`, `ADMIN` | Admin membuat/mengubah akun | Enum + tolak nilai lain | `201/200` | `422 INVALID_ROLE` | FR-035 |
| VAL-ROLE-02 | Endpoint memiliki allowlist role | Request terautentikasi | Middleware RBAC per route | Lanjut | `403 FORBIDDEN` | FR-003 |
| VAL-ROLE-03 | Dosen hanya mengakses kelas yang diampu | Dosen terautentikasi | Cek `teaching_assignments` untuk `class_id` target | Lanjut | `403 CLASS_FORBIDDEN` | FR-003, D-06 |
| VAL-ROLE-04 | Admin tidak boleh mengubah materi, feedback, nilai, atau submission | Admin terautentikasi | Tidak ada route mutasi akademik untuk admin; tolak jika dipanggil | — | `403 ADMIN_ACADEMIC_FORBIDDEN` | FR-037, AT-10 |
| VAL-ROLE-05 | Admin tidak boleh menonaktifkan diri sendiri atau admin aktif terakhir | Admin menonaktifkan akun | Cek target ≠ self; cek jumlah admin aktif > 1 | `204` | `409 LAST_ADMIN_PROTECTED` | D-11 |
| VAL-ROLE-06 | Menu tersembunyi bukan kontrol izin | — | Setiap endpoint tetap memeriksa role dan resource | — | `403` | FR-004 |
| VAL-ROLE-07 | Perubahan role mencabut sesi aktif target | Admin mengubah role | Revoke semua sesi target setelah perubahan | `200` | — | FR-034, D-11 |

## 6.3 Validasi pemilihan kategori VARK

| ID | Deskripsi | Prasyarat | Validasi | Respons sukses | Respons gagal | Referensi |
|---|---|---|---|---|---|---|
| VAL-VARK-01 | Mahasiswa wajib memilih VARK setelah login sebelum mengakses endpoint akademik | Sesi mahasiswa tanpa `vark_type` | Endpoint akademik menolak jika `vark_type` null | — | `428 VARK_REQUIRED` | FR-007 |
| VAL-VARK-02 | Nilai VARK hanya empat kategori | Permintaan pilih VARK | Enum `VISUAL`, `AURAL`, `READ_WRITE`, `KINESTHETIC` | `200` | `422 INVALID_VARK` | FR-005, FR-008, D-14 |
| VAL-VARK-03 | Tepat satu VARK per sesi, ditetapkan sekali | Sesi tanpa `vark_type` | `UPDATE ... WHERE vark_type IS NULL`; jika sudah terisi → tolak | `200` sesi ter-update | `409 VARK_ALREADY_SET` | FR-006, D-04 |
| VAL-VARK-04 | Perubahan VARK dalam sesi aktif ditolak | Sesi sudah punya `vark_type` | Tolak request ganti, termasuk API langsung | — | `409 VARK_LOCKED` | FR-008, AT-02 |
| VAL-VARK-05 | VARK baru hanya setelah logout dan login ulang | Sesi baru | Login membuat sesi baru; VARK lama tidak diwariskan | `200` | — | FR-008, AT-03 |
| VAL-VARK-06 | Hanya mahasiswa yang boleh memilih VARK | Role `STUDENT` | Tolak dosen/admin | — | `403 FORBIDDEN` | FR-005 |
| VAL-VARK-07 | VARK sesi adalah sumber kebenaran, bukan parameter klien | Sesi aktif | Abaikan/withhold parameter `vark` dari klien; gunakan `session.vark_type` | — | `403` jika klien memaksa kategori lain | FR-014, FR-015 |
| VAL-VARK-08 | VARK pada feedback adalah kategori asal, bukan VARK sesi saat ini | Feedback tersimpan | Simpan `vark_type` saat feedback dibuat/diubah | `200` | `422` jika bukan V/A/R | FR-019, FR-021 |
| VAL-VARK-09 | Nilai VARK tidak dapat diubah di database setelah ditetapkan | Sesi tersimpan | Trigger/guard menolak `UPDATE auth_sessions SET vark_type` jika sudah non-null | — | `409` di API; error DB ditangkap | FR-006, FR-008 |

## 6.4 Validasi akses materi sesuai kategori aktif

| ID | Deskripsi | Prasyarat | Validasi | Respons sukses | Respons gagal | Referensi |
|---|---|---|---|---|---|---|
| VAL-MAT-01 | Topik harus terbit dan mahasiswa terdaftar di kelas | Mahasiswa dengan VARK aktif | Cek `topics.status = PUBLISHED`, `deleted_at IS NULL`, dan `enrollments` | `200` detail topik | `404 TOPIC_NOT_FOUND`; `403 NOT_ENROLLED` | FR-014, FR-015, D-06 |
| VAL-MAT-02 | Hanya materi dengan kategori = VARK sesi yang dikembalikan | Sesi VARK aktif | Filter `materials.vark_type = session.vark_type` dan `deleted_at IS NULL` | `200` daftar materi | `403 MATERIAL_CATEGORY_FORBIDDEN` jika meminta kategori lain | FR-014, FR-015 |
| VAL-MAT-03 | Akses langsung file/URL/API kategori lain ditolak | Request membawa `material_id`/path | Cek kepemilikan topik + kategori material + sesi | — | `403 MATERIAL_CATEGORY_FORBIDDEN` | FR-015, AT-01 |
| VAL-MAT-04 | URL file hanya diberikan setelah otorisasi dan berumur pendek | Otorisasi lolos | Buat presigned URL R2 TTL 5 menit; bucket privat | `200` `{ url, expiresAt }` | `404 MATERIAL_NOT_FOUND` | FR-015, D-05 |
| VAL-MAT-05 | Materi soft-deleted tidak boleh muncul atau diakses | Materi `deleted_at` terisi | Filter `deleted_at IS NULL`; tolak akses langsung | — | `404 MATERIAL_NOT_FOUND` | FR-010, D-07 |
| VAL-MAT-06 | Urutan topik mengikuti `sort_order` dosen | Daftar topik | Urutkan `sort_order ASC, created_at ASC` | `200` list terurut | — | FR-017 |
| VAL-MAT-07 | Materi Kinesthetic boleh ada sebagai bahan pendukung, tetapi yang dinilai adalah studi kasus | Sesi Kinesthetic | Tampilkan materi Kinesthetic jika ada; studi kasus tetap objek penilaian | `200` | — | FR-010, FR-023, D-08 |
| VAL-MAT-08 | Dosen hanya mengelola materi pada topik kelas yang diampu; admin tidak boleh | Dosen/admin terautentikasi | Cek `teaching_assignments`; admin ditolak | `200/201` | `403 CLASS_FORBIDDEN` / `403 ADMIN_ACADEMIC_FORBIDDEN` | FR-010, FR-037 |
| VAL-MAT-09 | Upload materi divalidasi tipe dan ukuran | Dosen mengunggah | Allowlist MIME + magic bytes; maks. 100 MB/file; nama file disanitasi; object key acak | `201` metadata materi | `422 INVALID_FILE_TYPE`; `413 FILE_TOO_LARGE` | FR-012, D-05 |
| VAL-MAT-10 | Metadata materi dicatat | Upload/link | Simpan kategori, judul, tipe file/tautan, ukuran, waktu unggah, pengunggah | `201` | `422` jika field wajib kosong | FR-011 |

## 6.5 Validasi ketersediaan materi

| ID | Deskripsi | Prasyarat | Validasi | Respons sukses | Respons gagal | Referensi |
|---|---|---|---|---|---|---|
| VAL-AVAIL-01 | “Materi tersedia” berarti minimal satu materi non-deleted untuk topik + kategori | Topik terbit | Hitung `EXISTS(materials WHERE topic_id = ? AND vark_type = ? AND deleted_at IS NULL)` | `200` `available: true` | `200` `available: false` | FR-016, D-05 |
| VAL-AVAIL-02 | Jika materi kategori sesi tidak tersedia, tampilkan pesan persis | Topik terbit, kategori kosong | Kembalikan kode `MATERIAL_NOT_AVAILABLE` + pesan persis | — | `404` dengan pesan: **“materi belum tersedia, harap hubungi dosen terlebih dahulu”** | FR-016, BR-08, AT-04 |
| VAL-AVAIL-03 | Jika studi kasus Kinesthetic belum ada, tampilkan pesan khusus | Topik terbit, sesi Kinesthetic | Cek `case_studies` aktif untuk topik | — | `404` dengan pesan: **“studi kasus belum tersedia”** | FR-023, D-08 |
| VAL-AVAIL-04 | Topik terbit tetap terlihat meski kategori mahasiswa kosong | Topik terbit | Daftar topik menampilkan status ketersediaan per kategori | `200` | — | FR-013, FR-016, BR-07 |
| VAL-AVAIL-05 | Topik belum terbit tidak boleh diakses mahasiswa | Topik `DRAFT`/terarsip | Tolak detail dan materi | — | `404 TOPIC_NOT_FOUND` | UC-M02, UC-M03 |
| VAL-AVAIL-06 | Feedback hanya boleh jika materi kategori tersedia | Sesi V/A/R | Cek ketersediaan materi sebelum membuat/mengubah feedback | `200` | `404 MATERIAL_NOT_AVAILABLE` | FR-018 |
| VAL-AVAIL-07 | Daftar topik menampilkan status ketersediaan kategori sesi | Sesi VARK aktif | Sertakan `materialAvailable` boolean per topik | `200` | — | FR-016, UC-M02 |
| VAL-AVAIL-08 | Materi ganda dalam satu kategori semuanya dapat ditampilkan | Topik + kategori punya >1 materi | Urutkan materi; kembalikan semua yang non-deleted | `200` list | — | FR-010, D-05 |

## 6.6 Validasi feedback pemahaman

| ID | Deskripsi | Prasyarat | Validasi | Respons sukses | Respons gagal | Referensi |
|---|---|---|---|---|---|---|
| VAL-FB-01 | Feedback hanya untuk sesi Visual, Aural, Read/Write | Sesi VARK aktif | Tolak jika `KINESTHETIC` | — | `403 FEEDBACK_NOT_APPLICABLE` | FR-018, FR-022 |
| VAL-FB-02 | Nilai feedback berupa integer 0–100 | Permintaan simpan | Zod integer + `CHECK (value BETWEEN 0 AND 100)` | `200/201` | `422 INVALID_FEEDBACK_VALUE` | FR-018, FR-022 |
| VAL-FB-03 | Satu feedback terbaru per mahasiswa–topik–kategori | Feedback disimpan | `UNIQUE(student_id, topic_id, vark_type)` | `200/201` | `409` hanya jika terjadi race yang tidak tertangani | FR-019, BR-04 |
| VAL-FB-04 | Simpan ulang memperbarui nilai, bukan menambah riwayat | Feedback sudah ada | Upsert; perbarui `value` + `updated_at` | `200` | — | FR-020, AT-05 |
| VAL-FB-05 | Feedback kategori lain tidak terhapus saat mahasiswa berganti VARK | Ada feedback kategori lain | Upsert hanya menyentuh triple yang sama | `200` | — | FR-021, BR-09 |
| VAL-FB-06 | Saat membuka kembali, tampilkan nilai terakhir | Feedback ada | Baca baris triple; kembalikan `value` + `updatedAt` | `200` | `200` `value: null` jika belum ada | FR-021, AT-06 |
| VAL-FB-07 | “Belum feedback” tidak boleh diperlakukan sebagai 0% | Tidak ada baris feedback | API mengembalikan `null`/flag, bukan 0; analitik menghitung terpisah | — | — | FR-031 |
| VAL-FB-08 | Mahasiswa tidak boleh mengubah feedback milik orang lain | Feedback milik orang lain | Cek `feedback.student_id = session.user_id` | — | `403 FEEDBACK_FORBIDDEN` | FR-022 |
| VAL-FB-09 | Mahasiswa tidak boleh mengirim feedback untuk kategori di luar sesi | Sesi V/A/R | Tolak `vark_type` parameter yang berbeda dari sesi | — | `403 VARK_MISMATCH` | FR-014, FR-022 |
| VAL-FB-10 | Feedback yang sudah ada tetap tersimpan jika materi kemudian di-soft-delete | Materi dihapus | Jangan hapus feedback; blokir update baru sampai materi tersedia kembali | — | `404 MATERIAL_NOT_AVAILABLE` untuk update | FR-019, D-07 |
| VAL-FB-11 | Feedback hanya dapat diubah pada topik yang masih terbit | Topik terarsip | Tolak update; data tetap tersimpan untuk analitik historis | — | `404 TOPIC_NOT_FOUND` | D-07, D-12 |
| VAL-FB-12 | Endpoint feedback tidak tersedia untuk sesi Kinesthetic | Sesi Kinesthetic | Route mengembalikan 403; UI tidak menampilkan slider | — | `403 FEEDBACK_NOT_APPLICABLE` | FR-018, FR-022 |

## 6.7 Validasi jawaban Kinesthetic

| ID | Deskripsi | Prasyarat | Validasi | Respons sukses | Respons gagal | Referensi |
|---|---|---|---|---|---|---|
| VAL-CASE-01 | Submission hanya untuk sesi Kinesthetic | Sesi VARK aktif | Tolak jika bukan `KINESTHETIC` | — | `403 VARK_MISMATCH` | FR-024, FR-050 |
| VAL-CASE-02 | Studi kasus harus ada dan topik terbit | Topik terbit | Cek `case_studies` aktif + `topics.status = PUBLISHED` | `200` detail kasus | `404 CASE_NOT_FOUND` / pesan “studi kasus belum tersedia” | FR-023, D-08 |
| VAL-CASE-03 | Jawaban berupa teks 1–10.000 karakter | Permintaan Kirim | Hitung panjang setelah trim; tolak kosong/whitespace-only; hitung code point Unicode | `201` submission | `422 ANSWER_LENGTH_INVALID` | FR-047, BR-17, AT-15 |
| VAL-CASE-04 | Tidak ada draft atau penyimpanan otomatis di server | Mengetik jawaban | Tidak ada endpoint draft; server tidak menyimpan teks sebelum Kirim | — | `405 DRAFT_NOT_SUPPORTED` jika dipanggil | FR-045, BR-16, AT-13 |
| VAL-CASE-05 | Mahasiswa harus terdaftar dan memiliki akses ke kasus | Sesi Kinesthetic | Cek enrollment + topik + case | — | `403 NOT_ENROLLED` / `404` | FR-028, D-06 |
| VAL-CASE-06 | Satu submission per mahasiswa–studi kasus | Belum submit | `UNIQUE(student_id, case_study_id)` | `201` | `409 ALREADY_SUBMITTED` | FR-025, FR-038, AT-07, AT-14 |
| VAL-CASE-07 | Submission tidak dapat diubah/dihapus setelah dikirim | Submission ada | Tidak ada route update/delete; trigger menolak perubahan teks | — | `405 SUBMISSION_IMMUTABLE` / `409` | FR-039, BR-10, BR-13 |
| VAL-CASE-08 | Waktu kirim dicatat dari server | Submission dibuat | `submitted_at = now()` database | `201` + timestamp | — | FR-025, D-09 |
| VAL-CASE-09 | Jawaban hanya teks, tanpa upload file | Permintaan Kirim | Tolak multipart/file; hanya `text/plain` field | `201` | `422 FILE_NOT_ALLOWED` | FR-024 |
| VAL-CASE-10 | Studi kasus bersifat 1:1 dengan topik; teori hanya untuk sesi Kinesthetic | Topik ada | Cek `UNIQUE(topic_id)` pada `case_studies` | `200` | — | FR-023, D-08 |
| VAL-CASE-11 | Jika studi kasus belum ada, mahasiswa Kinesthetic mendapat pesan khusus | Topik terbit, tanpa case | Cek keberadaan case | — | `404` pesan “studi kasus belum tersedia” | D-08 |

## 6.8 Validasi deadline

| ID | Deskripsi | Prasyarat | Validasi | Respons sukses | Respons gagal | Referensi |
|---|---|---|---|---|---|---|
| VAL-DL-01 | Deadline wajib diisi saat membuat studi kasus | Dosen membuat kasus | Tanggal/jam valid; tidak boleh kosong | `201` | `422 DEADLINE_REQUIRED` | FR-040, BR-11 |
| VAL-DL-02 | Deadline disimpan UTC dan ditampilkan WIB | Deadline tersimpan | Simpan `timestamptz`; response menyertakan ISO 8601 + label WIB | `200` | — | FR-040, D-09 |
| VAL-DL-03 | Submission diterima jika `server_now <= deadline_at` | Submission diproses | Bandingkan `now()` database dengan `deadline_at` | `201` | — | FR-049, BR-19 |
| VAL-DL-04 | Submission ditolak jika `server_now > deadline_at` | Submission diproses | Bandingkan dalam transaksi | — | `409 DEADLINE_PASSED` | FR-040, FR-049, BR-14 |
| VAL-DL-05 | Tepat pada deadline tetap diterima (inklusif) | `server_now = deadline_at` | Gunakan `<=`, bukan `<` | `201` | — | FR-049, AT-16 |
| VAL-DL-06 | Deadline tidak dapat diubah setelah submission pertama | Sudah ada submission | Cek `EXISTS(submissions)` sebelum update | — | `409 DEADLINE_LOCKED` | D-09 |
| VAL-DL-07 | Pengecekan deadline dan insert submission atomik | Permintaan Kirim | Transaksi + lock/unique constraint; `now()` database | `201` | `409` tanpa baris tersimpan | FR-046, FR-050 |
| VAL-DL-08 | Penolakan deadline tidak boleh membuat submission atau nilai 0 | Submission ditolak | Rollback transaksi | — | Tidak ada baris baru | FR-048, BR-18 |
| VAL-DL-09 | Form yang sudah terbuka tetap ditolak setelah deadline | Form terbuka | Validasi ulang di backend saat Kirim | — | `409 DEADLINE_PASSED` | FR-046, AT-09 |
| VAL-DL-10 | Perubahan deadline sebelum submission pertama dicatat audit | Dosen mengubah deadline | Cek belum ada submission; tulis audit `DEADLINE_UPDATED` | `200` | `409 DEADLINE_LOCKED` | D-09, D-13 |
| VAL-DL-11 | Status mahasiswa dihitung ulang setelah deadline berubah | Deadline berubah | Status turunan dihitung saat baca, bukan disimpan | `200` status terbaru | — | FR-042, FR-048 |

## 6.9 Validasi pengumpulan jawaban satu kali

| ID | Deskripsi | Prasyarat | Validasi | Respons sukses | Respons gagal | Referensi |
|---|---|---|---|---|---|---|
| VAL-SUB-01 | Hanya satu submission per mahasiswa–studi kasus | Belum submit | `UNIQUE(student_id, case_study_id)` | `201` | `409 ALREADY_SUBMITTED` | FR-025, FR-038 |
| VAL-SUB-02 | Dua request Kirim bersamaan hanya menghasilkan satu submission | Dua request paralel | Satu transaksi menang; yang lain melanggar unique | `201` pada satu request | `409` pada request lain | AT-14, FR-050 |
| VAL-SUB-03 | Submission tidak dapat dihapus | Submission ada | Tidak ada route delete; trigger menolak | — | `405 SUBMISSION_IMMUTABLE` | FR-039 |
| VAL-SUB-04 | Isi jawaban tidak berubah saat penilaian berhasil atau gagal | Assessment diproses | Assessment hanya membaca submission; tidak ada update teks | `200/201` | `500` tanpa mengubah submission | FR-041 |
| VAL-SUB-05 | Semua validasi dijalankan sebelum insert | Permintaan Kirim | VARK, enrollment, panjang, unique, deadline | `201` | `4xx` sesuai aturan | FR-050 |
| VAL-SUB-06 | Submission hanya menyimpan teks final + waktu | Insert | Tidak menyimpan draft/riwayat | `201` | — | FR-025, FR-045 |
| VAL-SUB-07 | Submission yang sudah dinilai tetap tidak dapat diubah | Assessment ada | Trigger immutability pada `answer_text` | — | `409` | FR-039, BR-13 |

## 6.10 Validasi penilaian dosen

| ID | Deskripsi | Prasyarat | Validasi | Respons sukses | Respons gagal | Referensi |
|---|---|---|---|---|---|---|
| VAL-GRADE-01 | Hanya dosen yang berwenang atas kelas boleh menilai | Dosen terautentikasi | Cek `teaching_assignments` + kelas dari submission | `200/201` | `403 CLASS_FORBIDDEN` | FR-026, FR-028 |
| VAL-GRADE-02 | Submission harus ada | Dosen membuka penilaian | Cek `submissions.id` | `200` | `404 SUBMISSION_NOT_FOUND` | FR-026 |
| VAL-GRADE-03 | Submission belum pernah dinilai | Assessment belum ada | Cek `NOT EXISTS(assessments WHERE submission_id = ?)` | Lanjut | `409 ALREADY_ASSESSED` | FR-044, BR-13 |
| VAL-GRADE-04 | Nilai berupa integer 0–100 | Dosen mengirim nilai | Zod integer + `CHECK (score BETWEEN 0 AND 100)` | `201` | `422 INVALID_SCORE` | FR-043, AT-11 |
| VAL-GRADE-05 | Komentar opsional, maksimal 1.000 karakter | Dosen mengirim komentar | Jika ada, panjang 1–1.000; jika kosong, simpan `null` | `201` | `422 INVALID_COMMENT` | D-10 |
| VAL-GRADE-06 | Satu assessment per submission | Assessment belum ada | `UNIQUE(submission_id)` | `201` | `409 ALREADY_ASSESSED` | FR-044 |
| VAL-GRADE-07 | Dua dosen menilai bersamaan: hanya satu berhasil | Dua request paralel | Unique constraint + transaksi | Satu `201` | Satu `409` | FR-044 |
| VAL-GRADE-08 | Nilai dan komentar langsung terlihat mahasiswa | Assessment tersimpan | Response mahasiswa membaca assessment yang sama | `200` | — | FR-027, AT-08 |
| VAL-GRADE-09 | Admin tidak boleh menilai | Role admin | Tolak route penilaian | — | `403 ADMIN_ACADEMIC_FORBIDDEN` | FR-037, AT-10 |
| VAL-GRADE-10 | Penyimpanan nilai dicatat di audit | Assessment dibuat | Tulis audit `ASSESSMENT_SAVED` dalam transaksi yang sama | `201` | Rollback jika audit gagal | D-13 |

## 6.11 Ketentuan finalisasi nilai

| ID | Deskripsi | Prasyarat | Validasi | Respons sukses | Respons gagal | Referensi |
|---|---|---|---|---|---|---|
| VAL-FIN-01 | Assessment bersifat insert-only | Assessment akan dibuat | Tidak ada route update/delete; trigger menolak `UPDATE`/`DELETE` | `201` | `405 FINAL_IMMUTABLE` / `409` | FR-044, BR-13 |
| VAL-FIN-02 | Teks submission tidak dapat diubah setelah dikirim | Submission ada | Trigger menolak perubahan `answer_text` | — | `405 SUBMISSION_IMMUTABLE` | FR-039, FR-041 |
| VAL-FIN-03 | Nilai dan komentar tidak dapat diubah siapa pun, termasuk admin | Assessment ada | Trigger + RBAC | — | `403/405` | FR-044, BR-13, AT-12 |
| VAL-FIN-04 | Finalisasi berlaku sebelum dan sesudah dinilai | Submission ada | Tidak ada endpoint revisi untuk mahasiswa | — | `405` | FR-039, BR-10 |
| VAL-FIN-05 | Kegagalan penilaian tidak mengubah submission | Assessment gagal | Transaksi rollback; submission tidak tersentuh | — | `500` dengan pesan aman | FR-041 |

## 6.12 Status pengumpulan mahasiswa

| ID | Deskripsi | Prasyarat | Validasi | Respons sukses | Respons gagal | Referensi |
|---|---|---|---|---|---|---|
| VAL-STAT-01 | Sebelum deadline dan belum submit → `BELUM_MENGUMPULKAN` | Deadline belum lewat | `now() <= deadline_at` dan tidak ada submission | `200` status | — | FR-042, UC-M06 |
| VAL-STAT-02 | Setelah deadline dan belum submit → `TIDAK_MENGUMPULKAN` | Deadline lewat | `now() > deadline_at` dan tidak ada submission | `200` status + tanpa nilai | — | FR-048, BR-18, AT-17 |
| VAL-STAT-03 | Sudah submit, belum dinilai → `MENUNGGU_PENILAIAN` | Submission ada | `NOT EXISTS(assessment)` | `200` status | — | FR-042 |
| VAL-STAT-04 | Sudah dinilai → `SUDAH_DINILAI` + nilai/komentar | Assessment ada | Baca assessment | `200` status + nilai + komentar | — | FR-042, FR-027 |
| VAL-STAT-05 | Status dihitung server, bukan disimpan/di-set klien | — | Derived dari deadline + submission + assessment | — | Klien tidak dapat mengirim status | FR-042 |
| VAL-STAT-06 | `TIDAK_MENGUMPULKAN` tidak boleh membuat submission palsu atau nilai 0 | Deadline lewat | Tidak ada insert otomatis | — | — | FR-048, BR-18 |
| VAL-STAT-07 | Mahasiswa hanya melihat status miliknya; dosen melihat kelas | Role | Cek ownership/class | `200` | `403` | FR-028 |
| VAL-STAT-08 | Mahasiswa V/A/R tidak memiliki status submission Kinesthetic | Sesi V/A/R | Endpoint status kasus hanya relevan untuk Kinesthetic | — | `403`/tidak ditampilkan | FR-018, FR-042 |
| VAL-STAT-09 | Perubahan deadline sebelum submission pertama mengubah status secara alami | Deadline berubah | Hitung ulang saat baca | `200` | — | D-09 |
| VAL-STAT-10 | Label status ditampilkan dalam Bahasa Indonesia | — | `Belum Mengumpulkan`, `Tidak Mengumpulkan`, `Menunggu Penilaian`, `Sudah Dinilai` | — | — | FR-042 |

## 6.13 Error handling dan kondisi khusus

| ID | Kondisi | Perilaku/Validasi | Respons | Referensi |
|---|---|---|---|---|
| ERR-01 | Semua error API | Envelope konsisten `{ error: { code, message, details, requestId } }` | 4xx/5xx | §0.1 |
| ERR-02 | Pemetaan HTTP | `400` malformed; `401` unauthenticated; `403` forbidden; `404` not found; `405` method/operation not allowed; `409` conflict/state; `413` too large; `422` validation/business; `428` precondition; `429` rate limit; `500` internal; `502` storage/upstream failure | Sesuai kasus | — |
| ERR-03 | Validasi field | `details` berisi `field`, `code`, `message` per field | `422` | — |
| ERR-04 | Materi tidak tersedia | Gunakan pesan persis FR-016; jangan parafrase | `404 MATERIAL_NOT_AVAILABLE` | FR-016 |
| ERR-05 | Error server | Jangan bocorkan stack trace, SQL, token, atau data pribadi | `500 INTERNAL_ERROR` + requestId | NFR security |
| ERR-06 | Korelasi log | Setiap response punya `requestId`; log menyimpan requestId | — | NFR observability |
| ERR-07 | 401 vs 403 | 401 = belum/ tidak valid autentikasi; 403 = terautentikasi tetapi tidak berwenang | — | FR-003 |
| ERR-08 | Konflik state | `ALREADY_SUBMITTED`, `ALREADY_ASSESSED`, `VARK_LOCKED`, `DEADLINE_PASSED`, `DEADLINE_LOCKED` | `409` | FR-008, FR-025, FR-044 |
| ERR-09 | VARK belum dipilih | Endpoint akademik mengembalikan `428 VARK_REQUIRED`; UI membuka layar pilih VARK | `428` | FR-007 |
| ERR-10 | Rate limit | `429` + `Retry-After` | `429` | VAL-AUTH-09 |
| ERR-11 | Upload terlalu besar | Tolak sebelum menyimpan ke R2 | `413` | FR-012 |
| ERR-12 | Akun dinonaktifkan saat sesi aktif | Request berikutnya `401 SESSION_REVOKED`; klien logout | `401` | D-11 |
| ERR-13 | Topik di-unpublish saat mahasiswa membuka | Request materi berikutnya `404`; UI menampilkan topik tidak tersedia | `404` | D-07 |
| ERR-14 | Materi dihapus saat halaman terbuka | Signed URL lama tetap valid sampai TTL; request baru `404` | `404` | D-05, D-07 |
| ERR-15 | Dua request mengubah data yang sama | Optimistic concurrency/unique constraint; yang kalah `409` | `409` | FR-050 |
| ERR-16 | Clock skew klien | Deadline selalu memakai waktu server; waktu klien diabaikan | — | FR-046 |
| ERR-17 | Admin terakhir | Tolak deaktivasi | `409 LAST_ADMIN_PROTECTED` | D-11 |
| ERR-18 | Mahasiswa keluar kelas | Kehilangan akses baru; histori nilai/feedback tetap | `403 NOT_ENROLLED` | BR-15, D-13 |
| ERR-19 | Duplikasi login | Tolak create/update | `409 LOGIN_ID_TAKEN` | FR-036 |
| ERR-20 | Kegagalan storage | Upload gagal; tidak ada metadata setengah jadi | `502 STORAGE_UNAVAILABLE` | D-05 |
| ERR-21 | Kegagalan audit saat menyimpan nilai | Transaksi rollback; nilai tidak tersimpan | `500` | D-13 |
| ERR-22 | Timeout jaringan klien | Tampilkan retry; jangan menganggap submission gagal/berhasil tanpa konfirmasi server | — | UX |
| ERR-23 | Klien offline saat Kirim | Tidak ada draft server; pengguna harus mengirim ulang saat online | — | FR-045 |

## 6.14 Ringkasan status dan kode

| Status | Dibentuk dari | Nilai 0? | Submission? |
|---|---|---|---|
| `BELUM_MENGUMPULKAN` | `now() <= deadline_at` dan tidak ada submission | Tidak | Tidak |
| `TIDAK_MENGUMPULKAN` | `now() > deadline_at` dan tidak ada submission | **Tidak** | **Tidak** |
| `MENUNGGU_PENILAIAN` | Submission ada, assessment belum ada | Tidak | Ya |
| `SUDAH_DINILAI` | Assessment ada | Bisa 0 (nilai sah) | Ya |

> Catatan: nilai 0 yang **sah** hanya berasal dari assessment dosen, bukan dari status `TIDAK_MENGUMPULKAN`.

# §7 Ketidakpastian Tersisa

## 7.1 CONFIRMED

### 7.1.1 Keputusan wawancara

| ID | Keputusan | Status | Catatan |
|---|---|---|---|
| D-01 | Android native; REST backend; rename EduVARK/`com.eduvark.app`; monorepo | CONFIRMED | SRS §2.4/§4.2 perlu addendum saat implementasi |
| D-02 | PostgreSQL via Supabase | CONFIRMED | Detail cakupan Supabase ada di OQ-01 |
| D-03 | JWT access + refresh + tabel sesi | CONFIRMED | Bukan JWT murni |
| D-04 | Satu sesi aktif; idle 8 jam, absolut 12 jam | CONFIRMED | Login baru mencabut sesi lama |
| D-05 | Cloudflare R2; URL 5 menit; 100 MB/file; banyak materi/kategori | CONFIRMED | Detail per-tipe ada di OQ-11 |
| D-06 | `classes`, `enrollments`, `teaching_assignments`; auto-enroll/assign | CONFIRMED | Tanpa UI enrollment baru |
| D-07 | Soft delete topik/materi | CONFIRMED | Hard delete ditolak jika ada data akademik |
| D-08 | Satu studi kasus per topik; materi Kinesthetic opsional | CONFIRMED | Pesan “studi kasus belum tersedia” |
| D-09 | Deadline terkunci setelah submission pertama; UTC/WIB | CONFIRMED | Status turunan |
| D-10 | Nilai integer 0–100; komentar opsional ≤1.000 | CONFIRMED | |
| D-11 | NIM/NIP; password sementara; admin guardrails | CONFIRMED | Detail kebijakan password ada di OQ-02 |
| D-12 | Mean + n + histogram; belum feedback terpisah | CONFIRMED | |
| D-13 | Audit; retensi 1 tahun; keluar kelas | CONFIRMED | Detail backup/purge ada di OQ-03; akses audit di OQ-04 |
| D-14 | Enum `AURAL`, label “Audio” | CONFIRMED | |

### 7.1.2 TBD asli SRS yang sudah terselesaikan

| TBD asli | Status | Penyelesaian |
|---|---|---|
| TBD-01 — audit perubahan VARK antar-login | **CONFIRMED** | D-13: audit login/logout, pemilihan VARK, nilai, perubahan akun |
| TBD-05 — retensi/penghapusan akun, backup, ukuran media, teknologi | **PARTIAL** | Teknologi: D-02/D-05; retensi: D-13; ukuran: D-05; **backup/purge belum** (OQ-03) |
| TBD-10 — zona waktu tampilan deadline | **CONFIRMED** | D-09: simpan UTC, tampilkan WIB |
| BR-15 — mahasiswa keluar kelas | **CONFIRMED (kebijakan)** | D-13: kehilangan akses, histori tetap; **mekanisme** ada di OQ-07 |

### 7.1.3 Ketentuan SRS yang tetap berlaku

- FR-016: pesan persis **“materi belum tersedia, harap hubungi dosen terlebih dahulu”**.
- FR-049/BR-19: `server_now <= deadline_at` diterima; `server_now > deadline_at` ditolak.
- FR-048/BR-18: `Tidak Mengumpulkan` bukan nilai 0 dan tidak membuat submission.
- FR-019/BR-04: feedback unik per mahasiswa–topik–kategori.
- FR-025: submission unik per mahasiswa–studi kasus.
- FR-044/BR-13: nilai dan komentar final tidak dapat diubah.

## 7.2 TBD dan keputusan P0

> **Status:** OQ-01, OQ-02, OQ-07, dan OQ-15 telah **CONFIRMED** melalui D-15–D-18. Detail di bawah dipertahankan sebagai jejak keputusan. Belum final: OQ-03–OQ-06, OQ-08–OQ-14.

### OQ-01 — Cakupan Supabase: database saja atau juga Auth/Storage? — **CONFIRMED (D-15)**

- **Masalah:** Q2 memilih “Supabase”; Q3a memilih JWT + refresh + tabel sesi; Q4 memilih Cloudflare R2. Supabase juga menyediakan Auth, Storage, dan Realtime. Belum jelas apakah “Supabase” berarti hanya managed PostgreSQL atau seluruh platform.
- **Dampak:** menentukan implementasi autentikasi, migrasi pengguna, RLS, dan storage. Jika Supabase Auth dipakai, alur password sementara/refresh rotation/VARK lock harus diintegrasikan; jika tidak, Supabase hanya berperan sebagai Postgres.
- **Opsi:**
  - **A. Supabase hanya sebagai managed PostgreSQL.** Auth custom (D-03), storage Cloudflare R2 (D-05), tidak memakai Supabase Auth/Storage.
    - Kelebihan: selaras dengan D-03/D-05; kontrol penuh atas sesi dan VARK; tidak ada duplikasi identitas.
    - Kekurangan: harus membangun auth sendiri; tidak memanfaatkan fitur Supabase.
  - **B. Supabase Auth + PostgreSQL; sesi VARK tetap di tabel aplikasi; storage R2.**
    - Kelebihan: auth terkelola, reset password/email built-in.
    - Kekurangan: integrasi JWT Supabase dengan session table; potensi dua sumber identitas; aturan password sementara harus disesuaikan.
  - **C. Supabase Auth + Storage; R2 tidak dipakai.**
    - Kelebihan: satu platform.
    - Kekurangan: bertentangan dengan D-05; signed URL/akses materi berbeda; vendor lock-in.
- **Keputusan:** **A (D-15)** — Supabase hanya managed PostgreSQL; auth custom (D-03); storage R2 (D-05); Supabase Auth/Storage tidak dipakai.
- **Status:** CONFIRMED.

### OQ-02 — Kebijakan password dan token — **CONFIRMED (D-16)**

- **Masalah:** D-11 menetapkan password sementara dan wajib ganti, tetapi belum menetapkan panjang minimal, kompleksitas, masa berlaku token, rotasi refresh, dan lockout.
- **Dampak:** keamanan, UX login, dan test cases.
- **Opsi:**
  - **A. Panjang minimal 12 karakter, tanpa aturan komposisi wajib, blokir password umum; Argon2id; access token 15 menit; refresh token 12 jam (rotasi); lockout 5 gagal/15 menit.**
    - Kelebihan: mengikuti praktik modern (length over composition); selaras dengan sesi absolut 12 jam.
    - Kekurangan: pengguna mungkin memilih frasa lemah; butuh daftar password umum.
  - **B. Minimal 8 karakter dengan huruf besar/kecil/angka/simbol; access 30 menit; refresh 30 hari.**
    - Kelebihan: familiar.
    - Kekurangan: aturan komposisi sering menghasilkan password lemah; refresh 30 hari melebihi sesi absolut 12 jam.
  - **C. Passphrase minimal 16 karakter; access 10 menit; refresh 12 jam; lockout 10 gagal/30 menit.**
    - Kelebihan: sangat kuat.
    - Kekurangan: friksi tinggi untuk mahasiswa.
- **Keputusan:** **A (D-16)** — password min. 12, tanpa komposisi wajib, blokir password umum, Argon2id; access 15 menit; refresh 12 jam rotating; lockout 5 gagal/15 menit; idle 8 jam/absolut 12 jam.
- **Status:** CONFIRMED.

### OQ-03 — Backup, disaster recovery, dan purge setelah retensi

- **Masalah:** D-13 menetapkan retensi satu semester + 1 tahun, tetapi belum menetapkan frekuensi backup, RPO/RTO, versioning R2, dan apakah data dihapus permanen setelah retensi.
- **Dampak:** kepatuhan, biaya, dan kemampuan memulihkan data akademik.
- **Opsi:**
  - **A. Supabase daily backup + PITR 7 hari; R2 versioning; purge otomatis setelah 1 tahun dengan persetujuan; RPO 24 jam, RTO 4 jam.**
    - Kelebihan: cukup untuk satu kelas; biaya terkendali.
    - Kekurangan: PITR 7 hari membatasi recovery point.
  - **B. Backup mingguan tanpa PITR; purge manual; RPO 7 hari, RTO 24 jam.**
    - Kelebihan: murah.
    - Kekurangan: risiko kehilangan data seminggu.
  - **C. Simpan selamanya; tanpa purge.**
    - Kelebihan: tidak ada risiko kehilangan.
    - Kekurangan: biaya dan kewajiban retensi tidak jelas.
- **Rekomendasi:** **A**, dengan kebijakan purge yang memerlukan persetujuan akademik.
- **Pertanyaan konfirmasi:** Apakah RPO 24 jam/RTO 4 jam dan purge setelah 1 tahun dapat diterima?

### OQ-04 — Siapa yang boleh melihat audit log?

- **Masalah:** D-13 menetapkan event audit, tetapi belum menetapkan siapa yang dapat membacanya dan berapa lama audit disimpan.
- **Dampak:** privasi, investigasi sengketa, dan UI admin.
- **Opsi:**
  - **A. Admin dapat melihat semua audit (read-only); dosen hanya melihat aksi sendiri; mahasiswa melihat riwayat login sendiri.**
    - Kelebihan: mendukung investigasi dan transparansi terbatas.
    - Kekurangan: UI/API tambahan.
  - **B. Hanya admin yang dapat melihat audit.**
    - Kelebihan: sederhana.
    - Kekurangan: dosen tidak dapat menelusuri perubahan nilainya sendiri.
  - **C. Tidak ada UI; akses langsung ke database oleh operator.**
    - Kelebihan: tidak ada pekerjaan UI.
    - Kekurangan: tidak operasional; risiko akses tidak terkontrol.
- **Rekomendasi:** **A**, dengan audit append-only.
- **Pertanyaan konfirmasi:** Setujui A, atau cukup admin saja?

### OQ-05 — Target performa dan kapasitas

- **Masalah:** §5.1 SRS menyatakan target kuantitatif belum ditetapkan.
- **Dampak:** acceptance criteria nonfungsional, sizing, dan uji beban.
- **Opsi:**
  - **A. p95 < 2 detik untuk baca, < 3 detik untuk tulis; 100 pengguna bersamaan; media melalui R2.**
    - Kelebihan: realistis untuk satu kelas; mudah diuji.
    - Kekurangan: belum menangani lonjakan besar.
  - **B. p95 < 1 detik; 500 pengguna bersamaan; butuh caching/optimasi.**
    - Kelebihan: lebih ambisius.
    - Kekurangan: biaya dan kompleksitas.
  - **C. Best effort, tanpa target.**
    - Kelebihan: tidak ada beban uji.
    - Kekurangan: tidak dapat dinyatakan lulus acceptance.
- **Rekomendasi:** **A** untuk MVP satu kelas.
- **Pertanyaan konfirmasi:** Setujui target A?

### OQ-06 — Apakah “laporan” mencakup ekspor?

- **Masalah:** §1.1 menyebut “laporan”, tetapi FR-029–FR-033 hanya mengatur grafik. Tidak ada requirement ekspor PDF/Excel/CSV.
- **Dampak:** scope dosen, UI, dan pengujian.
- **Opsi:**
  - **A. Grafik saja; ekspor di luar scope MVP.**
    - Kelebihan: sesuai FR yang ada; fokus.
    - Kekurangan: dosen tidak dapat mengunduh data.
  - **B. Tambah ekspor CSV feedback dan nilai.**
    - Kelebihan: berguna untuk arsip.
    - Kekurangan: fitur baru di luar SRS; perlu persetujuan.
  - **C. Tambah ekspor PDF berformat.**
    - Kelebihan: presentasi rapi.
    - Kekurangan: pekerjaan paling besar.
- **Rekomendasi:** **A** untuk MVP; B dapat ditambahkan sebagai change request.
- **Pertanyaan konfirmasi:** Apakah ekspor memang tidak diperlukan?

### OQ-07 — Mekanisme mahasiswa keluar kelas — **CONFIRMED (D-17)**

- **Masalah:** D-13 menetapkan kebijakan kehilangan akses dan histori tetap, tetapi belum menetapkan siapa yang mengubah status, kapan berlaku, dan apakah bisa mendaftar ulang.
- **Dampak:** enrollment, akses, analytics, dan audit.
- **Opsi:**
  - **A. Admin mengubah status enrollment menjadi `LEFT` dengan tanggal efektif melalui endpoint admin (tanpa UI khusus); akses dicabut; histori tetap; re-enroll mengaktifkan kembali.**
    - Kelebihan: jelas dan dapat diaudit; tidak banyak UI.
    - Kekurangan: endpoint admin baru di luar FR-034.
  - **B. Hapus baris enrollment; histori dipertahankan melalui snapshot/arsip.**
    - Kelebihan: bersih.
    - Kekurangan: kehilangan relasi historis; sulit diaudit.
  - **C. Tidak mendukung keluar kelas di MVP; di luar scope.**
    - Kelebihan: tidak ada pekerjaan.
    - Kekurangan: BR-15 tetap TBD dan tidak terpenuhi.
- **Keputusan:** **A (D-17)** — admin mengubah enrollment menjadi `LEFT` dengan tanggal efektif via endpoint admin (tanpa UI khusus); akses dicabut; histori tetap; re-enroll mengaktifkan kembali.
- **Status:** CONFIRMED.

### OQ-08 — Dosen mana yang boleh menilai?

- **Masalah:** D-06 meng-auto-assign dosen ke satu kelas. Jika ada beberapa dosen, apakah semuanya boleh menilai submission yang sama, dan bagaimana jika menilai bersamaan?
- **Dampak:** otorisasi, audit, dan race condition penilaian.
- **Opsi:**
  - **A. Semua dosen yang di-assign ke kelas boleh menilai satu kali; unique assessment memastikan hanya satu yang menang; audit mencatat penilai.**
    - Kelebihan: sederhana; sesuai satu kelas.
    - Kekurangan: dosen lain bisa menilai topik yang bukan buatannya.
  - **B. Hanya dosen pembuat topik/studi kasus yang boleh menilai.**
    - Kelebihan: tanggung jawab jelas.
    - Kekurangan: perlu relasi creator; menyulitkan jika dosen berhalangan.
  - **C. Admin menunjuk satu penilai per studi kasus.**
    - Kelebihan: eksplisit.
    - Kekurangan: fitur baru dan UI.
- **Rekomendasi:** **A** untuk MVP.
- **Pertanyaan konfirmasi:** Setujui A?

### 7.2.9 Ringkasan OQ-09–OQ-15

Detail opsi dan pertanyaan ada di [`OPEN_QUESTIONS.md`](OPEN_QUESTIONS.md).

| OQ | Topik | Status | Opsi (2–3) | Rekomendasi | Dampak |
|---|---|---|---|---|---|
| OQ-09 | Bootstrap admin pertama | TBD/RECOMMENDATION | A. Seed env/CLI; B. Manual SQL; C. Endpoint setup publik | **A** — seed via env/CLI; password sementara; tanpa default di repo | Deployment & keamanan |
| OQ-10 | Bahasa/copy UI | RECOMMENDATION | A. Indonesia + string terpusat; B. ID+EN; C. Bebas per layar | **A** — Bahasa Indonesia; string terpusat; review product owner | Konsistensi UI & test |
| OQ-11 | MIME/ukuran per tipe & antivirus | RECOMMENDATION | A. Batas per tipe + allowlist/magic bytes; B. 100 MB semua; C. 50 MB semua | **A** — PDF 25 MB, gambar 10 MB, audio 50 MB, video 100 MB | Validasi upload & storage |
| OQ-12 | Konvensi API | RECOMMENDATION | A. REST `/api/v1` + OpenAPI; B. Tanpa versi; C. GraphQL | **A** — envelope, pagination, OpenAPI | Kontrak Android–backend |
| OQ-13 | Offline, push, email | RECOMMENDATION | A. Di luar scope; B. Cache read-only; C. Offline penuh | **A** — di luar scope MVP | Scope & dependensi |
| OQ-14 | Privasi analytics | RECOMMENDATION | A. Nama untuk dosen kelas; B. Agregat anonim; C. Configurable | **A** — nama hanya untuk dosen kelas; admin tidak | RBAC & privasi |
| OQ-15 | UC login/logout staf | **CONFIRMED (D-18)** | A. Tambah UC-X01/X02; B. Perluas UC-M01/M07; C. Shared precondition + AT | **B** — perluas UC-M01/M07 untuk semua role; staf tanpa VARK | Traceability FR-001 |

## 7.3 CONFLICT

| ID | Konflik | Status | Resolusi |
|---|---|---|---|
| C-01 | §2.4/§4.2 menyebut browser, mandat proyek Android native | **RESOLVED** | D-01: Android native; SRS addendum |
| C-02 | FR-005/FR-018 memakai “Aural”, bagian lain “Audio” | **RESOLVED** | D-14: enum `AURAL`, label “Audio” |
| C-03 | FR-034 menyebut admin membuat akun mahasiswa/dosen; FR-035 memasukkan role admin | **RESOLVED** | D-11: admin boleh membuat admin, dengan guardrail |
| C-04 | §6–§8 kosong padahal dirujuk §1.3/§1.5 | **RESOLVED** | Dokumen ini mengisi §6–§8 |
| C-05 | §1.5/Appendix B merujuk Use Case Specification, Diagram Dependensi, Use Case/Activity/ERD, tetapi tidak ada di docx | **RESOLVED (rekonstruksi)** | Dokumen ini membuat rekonstruksi; dokumen asli tetap diminta untuk validasi |
| C-06 | §1.1 menyebut “laporan”, tetapi hanya grafik yang dirinci | **OPEN** | OQ-06 |
| C-07 | §4.3 menyebut entitas `session` dan `VARK preference`, tetapi tidak ada FR/ERD eksplisit | **RESOLVED** | ERD: `auth_sessions.vark_type`; feedback menyimpan kategori asal |
| C-08 | Tidak ada FR untuk enrollment/penugasan dosen, padahal FR-014 membutuhkan enrollment | **RESOLVED** | D-06: tabel + auto-enroll/assign |
| C-09 | FR-009 “hapus topik” vs FR-044/BR-13 data final tidak dapat diubah | **RESOLVED** | D-07: soft delete; hard delete ditolak jika ada data akademik |
| C-10 | §2.4 menyebut “penyimpanan media” belum ditentukan; Q4 memilih R2 | **RESOLVED** | D-05 |

## 7.4 RECOMMENDATION

| ID | Rekomendasi | Alasan | Butuh persetujuan? |
|---|---|---|---|
| R-01 | Gunakan Supabase hanya sebagai managed PostgreSQL | Selaras D-03/D-05; menghindari duplikasi auth | **CONFIRMED — D-15** |
| R-02 | API `/api/v1`, envelope konsisten, pagination, OpenAPI | Kontrak Android–backend stabil | Ya — OQ-12 |
| R-03 | Audit append-only; tulis audit dalam transaksi yang sama dengan perubahan nilai | FR-044/BR-13; sengketa | Ya — OQ-04 |
| R-04 | Seed admin pertama via environment/CLI, tanpa password default di repo | Keamanan deployment | Ya — OQ-09 |
| R-05 | Validasi MIME dengan magic bytes, object key acak, bucket privat | Keamanan upload | — |
| R-06 | Index: `feedback(student_id, topic_id, vark_type)`, `submissions(student_id, case_study_id)`, `assessments(submission_id)`, `auth_sessions(user_id)`, `materials(topic_id, vark_type)` | Kinerja query + constraint | — |
| R-07 | Jangan memakai RLS sebagai satu-satunya kontrol; otorisasi tetap di backend | Backend memegang aturan bisnis | — |
| R-08 | Tambah test AT-P01–AT-P13 untuk GAP pengujian | Menutup FR tanpa AT | Ya — §8.4 |
| R-09 | Offline mode, push notification, dan email transaksional di luar scope MVP | Tidak ada di SRS | Ya — OQ-13 |
| R-10 | Nama mahasiswa di analytics hanya untuk dosen kelas; admin tidak | Privasi | Ya — OQ-14 |
| R-11 | UI copy terpusat di resource string; Bahasa Indonesia | Konsistensi & pengujian | Ya — OQ-10 |
| R-12 | Gunakan `timestamptz`, `now()` database, dan trigger immutability | Integritas deadline/nilai | — |

### 7.4.1 OQ-12 — Konvensi API (rekomendasi)

- **Masalah:** SRS tidak menetapkan versi, pagination, format error, atau kontrak.
- **Opsi:** A. `/api/v1` + pagination + envelope + OpenAPI; B. tanpa versi + array sederhana; C. GraphQL.
- **Rekomendasi:** A. **Pertanyaan:** Setujui A?

### 7.4.2 OQ-13 — Offline, push, email (rekomendasi)

- **Masalah:** Tidak ada requirement.
- **Opsi:** A. Di luar scope MVP; B. cache lokal read-only; C. offline penuh.
- **Rekomendasi:** A. **Pertanyaan:** Setujui A?

### 7.4.3 OQ-14 — Privasi analytics (rekomendasi)

- **Masalah:** FR-030 memungkinkan dosen melihat feedback per mahasiswa; belum jelas apakah nama ditampilkan.
- **Opsi:** A. Nama terlihat oleh dosen kelas, admin tidak; B. agregat anonim; C. dapat dikonfigurasi.
- **Rekomendasi:** A. **Pertanyaan:** Setujui A?

## 7.5 Register TBD asli vs keputusan

| TBD | Status akhir | Detail |
|---|---|---|
| TBD-01 | **CONFIRMED** | Audit D-13; akses audit OQ-04 |
| TBD-05 | **PARTIAL** | Teknologi/retensi/ukuran terjawab; backup/purge OQ-03 |
| TBD-10 | **CONFIRMED** | WIB D-09 |
| BR-15 | **CONFIRMED (kebijakan)** | D-13; mekanisme OQ-07 |

> **Aturan:** TBD yang masih terbuka **tidak** boleh diperlakukan sebagai keputusan final dalam implementasi. Jika implementasi mulai sebelum OQ-01–OQ-08 terjawab, pekerjaan terkait harus dihentikan.

# §8 Matriks Cakupan

## 8.1 Requirements Traceability Matrix — ringkasan

RTM lengkap 50 FR berada di `REQUIREMENTS_TRACEABILITY_MATRIX.md`. Ringkasan status:

| Status | Jumlah | FR |
|---|---:|---|
| **COVERED** — FR, BR, UC, activity, entity, dan AT tersedia | 28 | FR-005, 006, 008, 013, 014, 015, 016, 018, 019, 020, 021, 024, 025, 026, 027, 037, 038, 039, 040, 042, 043, 044, 045, 046, 047, 048, 049, 050 |
| **PARTIAL** — ada UC/entity, tetapi AT atau activity belum lengkap | 8 | FR-001, 003, 007, 010, 022, 023, 034, 041 |
| **GAP** — belum ada pasangan AT/UC/activity yang memadai | 14 | FR-002, 004, 009, 011, 012, 017, 028, 029, 030, 031, 032, 033, 035, 036 |
| **Total** | **50** | |

> Tidak ada FR yang dihapus. FR yang belum memiliki pasangan tetap dicantumkan dan ditandai GAP.

## 8.2 Cakupan use case

| UC | Actor | FR terkait | Activity | Entitas utama | AT | Status |
|---|---|---|---|---|---|---|
| UC-M01 | Mahasiswa, Dosen, Admin | FR-001–008 | AD-M01 | USER, AUTH_SESSION | AT-01, AT-02, AT-03 | PARTIAL — login staf belum diuji (AT-P11) |
| UC-M02 | Mahasiswa | FR-014, FR-017 | AD-M02 | TOPIC, MATERIAL | AT-04 (parsial) | PARTIAL — urutan topik belum diuji |
| UC-M03 | Mahasiswa | FR-014–017 | AD-M02 | TOPIC, MATERIAL, AUTH_SESSION | AT-01, AT-04 | COVERED |
| UC-M04 | Mahasiswa | FR-018–022 | AD-M03 | FEEDBACK | AT-05, AT-06 | PARTIAL — validasi negatif (FR-022) belum diuji (AT-P05) |
| UC-M05 | Mahasiswa | FR-023–025, FR-038–041, FR-045–050 | AD-M04 | CASE_STUDY, SUBMISSION | AT-07, 09, 13, 14, 15, 16 | COVERED |
| UC-M06 | Mahasiswa | FR-027, FR-028, FR-042, FR-048 | AD-M05 | SUBMISSION, ASSESSMENT, CASE_STUDY | AT-08, AT-17 | PARTIAL — akses pemilik vs mahasiswa lain belum diuji (FR-028) |
| UC-M07 | Mahasiswa, Dosen, Admin | FR-001, FR-006–008 | AD-M01 | AUTH_SESSION | AT-03 (parsial) | PARTIAL — logout staf belum diuji (AT-P11) |
| UC-D01 | Dosen | FR-009, FR-013, FR-017 | AD-D01 | TOPIC | — | GAP — belum ada AT topik |
| UC-D02 | Dosen | FR-010–016 | AD-D01 | MATERIAL, TOPIC | AT-01, AT-04 | PARTIAL — metadata/validasi upload belum diuji |
| UC-D03 | Dosen | FR-023, FR-040 | AD-D02 | CASE_STUDY | AT-09, AT-16 (parsial) | PARTIAL — pembuatan kasus belum diuji |
| UC-D04 | Dosen | FR-028, FR-042, FR-048 | AD-D05 | SUBMISSION, CASE_STUDY | AT-17 (parsial) | PARTIAL — daftar mahasiswa belum diuji |
| UC-D05 | Dosen | FR-026–028, FR-043, FR-044 | AD-D03 | ASSESSMENT, SUBMISSION | AT-08, AT-11, AT-12 | PARTIAL — akses dosen lain/admin belum diuji (FR-028) |
| UC-D06 | Dosen | FR-029–033 | AD-D04 | FEEDBACK, ASSESSMENT | — | GAP — belum ada AT analitik |
| UC-A01 | Admin | FR-034–036 | AD-A01 | USER, ENROLLMENT, TEACHING_ASSIGNMENT | — | GAP — belum ada AT akun |
| UC-A02 | Admin | FR-034–037 | AD-A02 | USER | AT-10 (parsial) | PARTIAL — update/role belum diuji |
| UC-A03 | Admin | FR-034 | AD-A03 | USER, AUTH_SESSION | — | GAP — deaktivasi belum diuji |

**Catatan:** D-18 memperluas **UC-M01/M07** untuk semua role: mahasiswa memilih VARK, dosen/admin langsung dashboard. Tidak ada UC baru. FR-001 tetap **PARTIAL** karena pengujian login staf (AT-P11) belum ada, bukan karena gap UC.

## 8.3 Cakupan entitas

| Entitas | FR utama | UC | Catatan |
|---|---|---|---|
| `users` | FR-001–008, FR-026–028, FR-034–037, FR-044 | UC-M01, UC-D05, UC-A01–A03 | Role/status/login unik |
| `classes` | FR-001, FR-009, FR-014, FR-034 | UC-D01, UC-A01 | Satu kelas MVP; tetap dimodelkan |
| `enrollments` | FR-014, FR-028, FR-034 | UC-M02, UC-M03, UC-M05, UC-A01 | Menentukan akses mahasiswa |
| `teaching_assignments` | FR-003, FR-009–013, FR-026–028 | UC-D01–D06 | Menentukan kelas yang diampu |
| `auth_sessions` | FR-001–008, FR-014, FR-022, FR-050 | UC-M01, UC-M07 | Menyimpan VARK aktif + status sesi |
| `topics` | FR-009–017, FR-023, FR-029–033 | UC-D01, UC-M02, UC-D06 | Soft delete + urutan |
| `materials` | FR-010–016, FR-018 | UC-D02, UC-M03, UC-M04 | Banyak materi per topik–kategori |
| `feedback` | FR-018–022, FR-029–033 | UC-M04, UC-D06 | Unik triple; kategori asal |
| `case_studies` | FR-023–025, FR-040, FR-042–050 | UC-D03, UC-M05, UC-M06 | 1:1 dengan topik |
| `submissions` | FR-025, FR-038–042, FR-044, FR-048–050 | UC-M05, UC-M06, UC-D04, UC-D05 | Unik pasangan; immutable |
| `assessments` | FR-026–028, FR-033, FR-042–044 | UC-D05, UC-M06, UC-D06 | Unik submission; final |
| `audit_logs` | D-13 (mendukung FR-044) | UC-A01–A03, UC-D05 | Append-only |

## 8.4 Test gap register (usulan AT tambahan)

AT berikut **belum ada** di SRS asli. ID `AT-Pxx` adalah usulan, bukan perubahan AT-01–AT-17.

| ID usulan | FR | Skenario | Ekspektasi |
|---|---|---|---|
| AT-P01 | FR-001, FR-002 | Login kredensial salah, akun nonaktif, lalu login benar | 401/403 yang tepat; akun aktif berhasil; tidak ada sesi untuk akun nonaktif |
| AT-P02 | FR-003, FR-004 | Mahasiswa memanggil endpoint dosen/admin; dosen memanggil kelas lain | 403 di backend; navigasi tidak menjadi satu-satunya kontrol |
| AT-P03 | FR-009, FR-011, FR-013, FR-017 | Dosen membuat 3 topik, mengurutkan, menerbitkan topik tanpa materi lengkap, mengarsipkan | Urutan tersimpan; topik terbit; arsip tidak terlihat mahasiswa; data akademik tetap |
| AT-P04 | FR-011, FR-012 | Upload file MIME palsu, file >100 MB, file valid | 422/413 untuk yang invalid; metadata lengkap untuk yang valid |
| AT-P05 | FR-022 | Feedback -1/101, sesi Kinesthetic, feedback mahasiswa lain | Semua ditolak; tidak ada baris baru/berubah |
| AT-P06 | FR-023, FR-040 | Dosen membuat kasus dengan deadline WIB, lalu mengubahnya sebelum submission | Tersimpan UTC; tampil WIB; audit perubahan; setelah submission pertama → 409 |
| AT-P07 | FR-028 | Mahasiswa lain/dosen lain/admin membuka submission | Hanya pemilik dan dosen kelas yang diizinkan; admin 403 |
| AT-P08 | FR-029–FR-033 | Dua mahasiswa feedback 40/60; satu belum feedback | Mean, n, histogram benar; belum feedback bukan 0%; Kinesthetic terpisah |
| AT-P09 | FR-034–FR-036 | Admin membuat akun, duplikasi NIM, role invalid, update role, nonaktifkan | 201/409/422; role berubah; sesi target dicabut; last-admin guard |
| AT-P10 | FR-041 | Penilaian gagal di tengah transaksi (mis. audit gagal) | Tidak ada assessment; teks submission tidak berubah |
| AT-P11 | FR-001, D-04 | Login dosen/admin, logout, login di perangkat kedua | Sesi lama dicabut; logout staf berfungsi; token lama 401 |
| AT-P12 | FR-015, D-07 | Materi dihapus/unpublish saat halaman terbuka | Signed URL lama berlaku sampai TTL; request baru 404; data feedback tetap |
| AT-P13 | D-11 | Login pertama dengan password sementara; reset oleh admin | Wajib ganti password; akses akademik diblokir sampai ganti; sesi lama dicabut |

## 8.5 Matriks cakupan scope

| Area | In scope MVP | Out of scope / pending |
|---|---|---|
| Autentikasi | Login/logout, JWT + refresh, sesi tunggal, ganti password pertama, reset admin | SSO, login sosial, MFA |
| VARK | Empat kategori, satu per sesi, lock, enum `AURAL` | Riwayat lengkap VARK antar-login (audit ada; histori terpisah tidak) |
| Kelas | Satu kelas, enrollment otomatis, teaching assignment otomatis | Multi-kelas, multi-semester, UI enrollment |
| Topik/materi | CRUD topik, soft delete, upload R2, banyak materi/kategori, publish tanpa lengkap | Versioning materi, workflow approval, CDN eksternal |
| Feedback | Slider V/A/R, upsert, kategori terpisah, analitik mean/n/histogram | Feedback per materi (hanya per topik–kategori), komentar feedback |
| Kinesthetic | Teks 1–10.000, satu kasus/topik, satu submission, deadline WIB, no draft | Upload file, multi-kasus, perpanjangan per mahasiswa |
| Penilaian | Integer 0–100, komentar opsional, final, langsung terlihat | Nilai desimal, rubrik, revisi nilai, banding |
| Admin | CRUD akun, role, deaktivasi, reset password, audit | Mengelola materi/nilai/feedback, enrollment UI |
| Analitik | Grafik per topik/kategori, filter mahasiswa, Kinesthetic terpisah | Ekspor PDF/Excel (OQ-06), prediksi, benchmarking |
| Nonfungsional | Keamanan, audit, soft delete, retensi 1 tahun | Target performa (OQ-05), backup/DR (OQ-03), offline/push/email (OQ-13) |

## 8.6 Checklist QA dokumentasi

| # | Pemeriksaan | Status |
|---|---|---|
| 1 | Seluruh 50 FR tercakup di RTM | Ya — 50 baris; 28 COVERED, 8 PARTIAL, 14 GAP |
| 2 | BR-01–BR-19 konsisten dengan §6 | Ya — dipetakan |
| 3 | 16 UC memiliki spesifikasi | Ya |
| 4 | Diagram sesuai alur bisnis | Ya — [activity](../diagrams/activity/mahasiswa.md) & [use case](../diagrams/use-case/mahasiswa.md) |
| 5 | Kardinalitas ERD konsisten | Ya — [konseptual](../diagrams/database/ERD_CONCEPTUAL.md) & [logis](../diagrams/database/ERD_LOGICAL_DRAFT.md) |
| 6 | Tidak ada dependensi UC yang salah (include/extend) | Ya — dependency memakai data/guard, bukan include |
| 7 | Tidak ada TBD yang dinaikkan tanpa persetujuan | Ya — D-01–D-18 eksplisit; sisanya OQ |
| 8 | Source Mermaid dapat dirender | Ya — [manifest render](../diagrams/rendered/manifest.json) |
| 9 | Requirement tanpa pengujian diidentifikasi | Ya — §8.4 |
| 10 | Konflik/kepastian tersisa dilaporkan | Ya — §7 |




