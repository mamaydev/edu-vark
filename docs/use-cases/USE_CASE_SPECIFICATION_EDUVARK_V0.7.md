# Use Case Specification — EduVARK v0.7

**Dokumen:** `USE_CASE_SPECIFICATION_EDUVARK_V0.7`  
**Sumber:** `SRS_EduVARK_v0.7` + keputusan wawancara D-01–D-18  
**Status:** Analisis/dokumentasi; bukan implementasi.

## 1. Aktor

| Aktor | Tipe | Deskripsi |
|---|---|---|
| Mahasiswa | Primary | Login, memilih VARK, membaca materi, feedback, submit Kinesthetic, melihat hasil |
| Dosen | Primary | Mengelola topik/materi/kasus, menilai, melihat analitik |
| Admin | Primary | Mengelola akun; tidak mengakses data akademik |
| Sistem Waktu | Supporting | Sumber `server_now` untuk deadline; bukan aktor manusia |
| Penyimpanan Objek (R2) | Supporting | Menyimpan materi; diakses melalui signed URL |

## 2. Prinsip dependensi (Opsi A)

- Prasyarat dinilai dari **keadaan data dan otorisasi saat use case dijalankan**, bukan kewajiban menjalankan use case sebelumnya secara berantai.
- Jika akun/topik/materi/submission sudah ada dari sesi/hari lain, aktor boleh langsung menjalankan use case berikutnya.
- Relasi `<<include>>` hanya dipakai jika suatu perilaku **selalu** dipanggil sebagai bagian dari use case lain.
- Relasi `<<extend>>` hanya dipakai untuk perilaku opsional bersyarat.
- **Tidak ada** rantai `UC-A01 → UC-A02 → UC-A03` sebagai urutan wajib.
- Kondisi “akun target tersedia”, “topik tersedia”, “submission belum dinilai”, dan “VARK sesi aktif” ditempatkan sebagai **precondition/guard**, bukan include.

## 3. Indeks use case

| UC | Nama | Aktor | FR utama |
|---|---|---|---|
| UC-M01 | Login dan memilih VARK (VARK hanya mahasiswa) | Mahasiswa, Dosen, Admin | FR-001–FR-008 |
| UC-M02 | Melihat daftar topik | Mahasiswa | FR-014, FR-017 |
| UC-M03 | Mengakses materi sesuai VARK | Mahasiswa | FR-014–FR-017 |
| UC-M04 | Mengisi atau memperbarui feedback | Mahasiswa | FR-018–FR-022 |
| UC-M05 | Mengumpulkan studi kasus Kinesthetic | Mahasiswa | FR-023–FR-025, FR-038–FR-041, FR-045–FR-050 |
| UC-M06 | Melihat status pengumpulan dan hasil koreksi | Mahasiswa | FR-027–FR-028, FR-042, FR-048 |
| UC-M07 | Logout (semua role) | Mahasiswa, Dosen, Admin | FR-001, FR-006–FR-008 |
| UC-D01 | Mengelola topik IBD | Dosen | FR-009, FR-013, FR-017 |
| UC-D02 | Mengelola format materi VARK | Dosen | FR-010–FR-016 |
| UC-D03 | Membuat studi kasus dan deadline | Dosen | FR-023, FR-040 |
| UC-D04 | Memeriksa pengumpulan Kinesthetic | Dosen | FR-028, FR-042, FR-048 |
| UC-D05 | Memberikan penilaian final | Dosen | FR-026–FR-028, FR-043–FR-044 |
| UC-D06 | Melihat grafik pemahaman | Dosen | FR-029–FR-033 |
| UC-A01 | Membuat akun | Admin | FR-034–FR-036 |
| UC-A02 | Melihat dan memperbarui akun | Admin | FR-034–FR-037 |
| UC-A03 | Menonaktifkan akun | Admin | FR-034 |

> **D-18:** UC-M01/M07 diperluas untuk semua role. Mahasiswa memilih VARK; dosen/admin langsung dashboard. Tidak ada UC baru; ID SRS tetap.

---

## UC-M01 — Login dan Memilih VARK

| Field | Nilai |
|---|---|
| **Use Case ID** | UC-M01 |
| **Use Case Name** | Login dan Memilih VARK (VARK hanya untuk mahasiswa) |
| **Primary Actor** | Mahasiswa, Dosen, Admin |
| **Description** | Pengguna memulai sesi. Mahasiswa memilih tepat satu kategori VARK dan sistem menguncinya sampai logout; dosen/admin langsung menuju dashboard sesuai role tanpa memilih VARK. |
| **Trigger** | Pengguna membuka aplikasi dan mengirim kredensial; atau sesi sebelumnya berakhir. |
| **Preconditions** | 1) Akun pengguna ada dan berstatus aktif. 2) Pengguna belum memiliki sesi terautentikasi, atau sesi lama akan digantikan. 3) Aplikasi dapat menjangkau backend. |
| **Postconditions** | 1) Sesi terautentikasi terbentuk. 2) Untuk mahasiswa: tepat satu `vark_type` tersimpan. 3) Untuk dosen/admin: tidak ada VARK; dashboard role terbuka. 4) Sesi lama (jika ada) dicabut. |

### Main Success Scenario

1. Pengguna membuka aplikasi dan memasukkan NIM/NIP serta password.
2. Sistem memverifikasi kredensial, role, dan status akun.
3. Jika `must_change_password = true`, sistem meminta pengguna mengganti password terlebih dahulu (D-11).
4. Jika role = **Mahasiswa**, sistem menampilkan empat pilihan: **Visual, Audio, Read/Write, Kinesthetic**.
5. Mahasiswa memilih tepat satu kategori.
6. Sistem menyimpan kategori pada sesi dan menguncinya; sesi lama pengguna dicabut (D-04).
7. Jika role = **Dosen/Admin**, sistem melewati langkah VARK dan langsung menyiapkan dashboard role.
8. Sistem menampilkan dashboard; untuk mahasiswa, endpoint materi sekarang dapat diakses.

### Alternative Flows

- **A1 — Belum memilih VARK:** Mahasiswa menutup layar pilihan. Dashboard materi tetap terkunci; setiap endpoint akademik mengembalikan `428 VARK_REQUIRED`.
- **A2 — Sudah ada sesi aktif:** Login baru mencabut sesi lama dan membuat sesi baru; perangkat lama menerima `401 SESSION_REVOKED`.
- **A3 — Salah memasukkan password:** Sistem menampilkan pesan generik; tidak ada sesi dibuat.
- **A4 — Mencoba mengganti VARK dalam sesi:** Permintaan ditolak `409 VARK_LOCKED`; kategori tetap.
- **A5 — Logout lalu login lagi:** Mahasiswa dapat memilih kategori berbeda; ini satu-satunya jalur pergantian.
- **A6 — Login dosen/admin:** Tidak ada langkah VARK; setelah autentikasi, dashboard role langsung terbuka (D-18).

### Exception Flows

- **E1 — Akun nonaktif:** `403 ACCOUNT_INACTIVE`; tidak ada sesi.
- **E2 — Rate limit login:** `429 TOO_MANY_ATTEMPTS` + `Retry-After`.
- **E3 — Gangguan jaringan:** Aplikasi menampilkan retry; tidak mengklaim login berhasil.
- **E4 — Gagal ganti password pertama:** Tetap di layar ganti password; akses akademik belum terbuka.

### Business Rules

- BR-02: setiap login memilih satu VARK; dikunci sampai logout.
- BR-03: hanya kategori aktif yang boleh diakses.
- D-03/D-04: JWT + refresh + session table; satu sesi aktif.
- D-11: password sementara wajib diganti.
- D-14: enum `AURAL`, label UI “Audio”.
- D-18: UC-M01 mencakup semua role; cabang VARK hanya untuk mahasiswa.

### Related Functional Requirements

FR-001, FR-002, FR-003, FR-004, FR-005, FR-006, FR-007, FR-008.

### Acceptance Criteria

- [ ] Empat pilihan VARK tampil setelah login mahasiswa.
- [ ] Tepat satu VARK tersimpan di sesi; tidak ada kategori kedua.
- [ ] Sebelum memilih VARK, endpoint materi mengembalikan `428`.
- [ ] Mengganti VARK dalam sesi yang sama ditolak, termasuk via API.
- [ ] Setelah logout dan login ulang, mahasiswa dapat memilih kategori berbeda.
- [ ] Akun nonaktif dan kredensial salah ditolak tanpa membuat sesi.
- [ ] Login baru mencabut sesi lama (D-04).
- [ ] Dosen/admin dapat login tanpa memilih VARK dan langsung melihat dashboard role (D-18).

---

## UC-M02 — Melihat Daftar Topik

| Field | Nilai |
|---|---|
| **Use Case ID** | UC-M02 |
| **Use Case Name** | Melihat Daftar Topik |
| **Primary Actor** | Mahasiswa |
| **Description** | Mahasiswa melihat topik IBD yang sudah terbit, sesuai urutan dosen, beserta status ketersediaan materi untuk kategori sesinya. |
| **Trigger** | Mahasiswa membuka menu topik/dashboard. |
| **Preconditions** | 1) Sesi terautentikasi. 2) VARK aktif. 3) Mahasiswa terdaftar pada kelas. |
| **Postconditions** | Daftar topik terbit ditampilkan; topik tanpa materi kategori sesi tetap terlihat dengan status tidak tersedia. |

### Main Success Scenario

1. Mahasiswa membuka daftar topik.
2. Backend memeriksa sesi, VARK, dan enrollment.
3. Backend mengambil topik `PUBLISHED` yang tidak terarsip, diurutkan `sort_order`.
4. Untuk setiap topik, backend menghitung `materialAvailable` untuk kategori sesi.
5. Aplikasi menampilkan daftar; topik tanpa materi tetap muncul dengan penanda.
6. Mahasiswa memilih topik untuk membuka detail/materi.

### Alternative Flows

- **A1 — Belum ada topik:** Tampilkan empty state; tidak ada error.
- **A2 — Topik terbit tanpa materi kategori sesi:** Topik tetap tampil; saat dibuka, UC-M03 menampilkan pesan persis.
- **A3 — Topik belum terbit:** Tidak muncul di daftar; akses langsung ke detail ditolak.
- **A4 — VARK belum dipilih:** Daftar tidak terbuka; arahkan ke layar pilih VARK.

### Exception Flows

- **E1 — Sesi kedaluwarsa:** `401 SESSION_EXPIRED`; aplikasi logout lokal.
- **E2 — Tidak terdaftar:** `403 NOT_ENROLLED`.
- **E3 — Gangguan jaringan:** Tampilkan error + retry; jangan tampilkan daftar kosong palsu.

### Business Rules

- BR-03: hanya kategori aktif yang relevan.
- BR-07: topik boleh terbit tanpa materi lengkap.
- BR-08: pesan materi belum tersedia saat topik dibuka.
- FR-017: urutan topik sesuai dosen.

### Related Functional Requirements

FR-014, FR-017.

### Acceptance Criteria

- [ ] Hanya topik `PUBLISHED` yang tampil.
- [ ] Urutan sesuai `sort_order` dosen.
- [ ] Status ketersediaan kategori sesi terlihat.
- [ ] Topik tanpa materi kategori sesi tetap tampil.
- [ ] Empty state muncul jika belum ada topik.
- [ ] Mahasiswa tidak dapat melihat topik yang belum terbit.

---

## UC-M03 — Mengakses Materi Sesuai VARK

| Field | Nilai |
|---|---|
| **Use Case ID** | UC-M03 |
| **Use Case Name** | Mengakses Materi Sesuai VARK |
| **Primary Actor** | Mahasiswa |
| **Description** | Mahasiswa membuka materi topik yang sesuai dengan kategori VARK sesi aktif; akses kategori lain ditolak di backend. |
| **Trigger** | Mahasiswa memilih topik dari daftar atau membuka tautan materi. |
| **Preconditions** | 1) Sesi terautentikasi dengan VARK. 2) Topik terbit dan tidak terarsip. 3) Mahasiswa terdaftar. |
| **Postconditions** | Materi kategori sesi ditampilkan, atau pesan “materi belum tersedia…” jika kosong. |

### Main Success Scenario

1. Mahasiswa memilih topik.
2. Backend memeriksa role, enrollment, status topik, dan VARK sesi.
3. Backend mencari materi non-deleted dengan `vark_type = session.vark_type`.
4. Jika ada, backend membuat signed URL R2 berumur 5 menit untuk setiap materi file.
5. Aplikasi menampilkan materi (PDF/audio/video/gambar/tautan).
6. Mahasiswa dapat beralih ke feedback (UC-M04) jika kategori V/A/R.

### Alternative Flows

- **A1 — Materi kategori sesi kosong:** Tampilkan persis **“materi belum tersedia, harap hubungi dosen terlebih dahulu”**.
- **A2 — Ada beberapa materi:** Tampilkan daftar; semua dapat dibuka.
- **A3 — Materi berupa tautan eksternal:** Buka tautan; tetap tercatat sebagai materi topik–kategori.
- **A4 — Sesi Kinesthetic:** Tampilkan materi Kinesthetic jika ada; studi kasus tetap objek penilaian (UC-M05).
- **A5 — Materi dihapus/unpublish saat halaman terbuka:** Signed URL lama berlaku sampai TTL; permintaan baru `404`.

### Exception Flows

- **E1 — Meminta kategori lain via URL/API:** `403 MATERIAL_CATEGORY_FORBIDDEN`.
- **E2 — Topik belum terbit:** `404 TOPIC_NOT_FOUND`.
- **E3 — Tidak terdaftar:** `403 NOT_ENROLLED`.
- **E4 — Signed URL kedaluwarsa:** Aplikasi meminta URL baru; jika materi sudah tidak ada → `404`.
- **E5 — Gangguan jaringan:** Tampilkan retry.

### Business Rules

- BR-03: hanya kategori aktif.
- BR-08: pesan persis.
- D-05: R2 privat + signed URL 5 menit.
- D-07: soft delete.

### Related Functional Requirements

FR-014, FR-015, FR-016, FR-017.

### Acceptance Criteria

- [ ] Hanya materi kategori sesi yang dikembalikan.
- [ ] Akses langsung ke file/URL/API kategori lain ditolak `403`.
- [ ] Materi kosong menampilkan pesan persis.
- [ ] Signed URL berumur pendek dan tidak dapat diakses tanpa otorisasi.
- [ ] Topik belum terbit tidak dapat diakses.
- [ ] Materi soft-deleted tidak muncul.

---

## UC-M04 — Mengisi atau Memperbarui Feedback

| Field | Nilai |
|---|---|
| **Use Case ID** | UC-M04 |
| **Use Case Name** | Mengisi atau Memperbarui Feedback |
| **Primary Actor** | Mahasiswa |
| **Description** | Mahasiswa mencatat persentase pemahaman subjektif untuk topik dan kategori V/A/R; nilai terbaru menggantikan nilai sebelumnya tanpa riwayat baru. |
| **Trigger** | Mahasiswa membuka materi V/A/R dan menggeser slider lalu menekan Simpan. |
| **Preconditions** | 1) Sesi V/A/R (bukan Kinesthetic). 2) Topik terbit. 3) Materi kategori sesi tersedia. 4) Mahasiswa terdaftar. |
| **Postconditions** | Tepat satu baris feedback per mahasiswa–topik–kategori; nilai terbaru tersimpan; feedback kategori lain tidak berubah. |

### Main Success Scenario

1. Mahasiswa membuka materi V/A/R.
2. Backend memeriksa sesi, VARK, enrollment, dan ketersediaan materi.
3. Backend mengambil feedback yang ada untuk triple (mahasiswa, topik, VARK).
4. Aplikasi menampilkan nilai terakhir, atau label “belum ada feedback” jika `null`.
5. Mahasiswa mengatur slider integer 0–100.
6. Mahasiswa menekan Simpan.
7. Backend memvalidasi nilai dan melakukan upsert pada triple yang sama.
8. Aplikasi menampilkan nilai tersimpan dan waktu pembaruan.

### Alternative Flows

- **A1 — Belum pernah feedback:** Slider ditampilkan pada posisi awal, tetapi label “belum ada feedback” tetap ada; tidak ada baris dibuat sampai Simpan.
- **A2 — Memperbarui feedback:** Nilai lama diganti; tidak ada baris riwayat baru.
- **A3 — Berganti VARK lalu kembali:** Nilai kategori asal ditampilkan kembali; feedback kategori lain tetap.
- **A4 — Topik punya beberapa materi:** Feedback tetap per topik–kategori, bukan per materi.

### Exception Flows

- **E1 — Sesi Kinesthetic:** `403 FEEDBACK_NOT_APPLICABLE`.
- **E2 — Nilai di luar 0–100 / bukan integer:** `422 INVALID_FEEDBACK_VALUE`.
- **E3 — Mencoba mengubah feedback orang lain:** `403 FEEDBACK_FORBIDDEN`.
- **E4 — Materi sudah tidak tersedia:** `404 MATERIAL_NOT_AVAILABLE`; feedback lama tetap tersimpan.
- **E5 — Gangguan jaringan:** Tampilkan retry; jangan tampilkan nilai sebagai tersimpan sebelum konfirmasi server.

### Business Rules

- BR-04: slider 0–100 hanya V/A/R; satu feedback terbaru per triple.
- BR-09: feedback kategori lain tetap.
- FR-031: “belum feedback” bukan 0%.

### Related Functional Requirements

FR-018, FR-019, FR-020, FR-021, FR-022.

### Acceptance Criteria

- [ ] Slider hanya muncul untuk V/A/R dan materi tersedia.
- [ ] Nilai integer 0–100; di luar rentang ditolak.
- [ ] Hanya satu baris per mahasiswa–topik–kategori.
- [ ] Simpan ulang memperbarui nilai, bukan menambah riwayat.
- [ ] Feedback kategori lain tidak berubah.
- [ ] Nilai terakhir ditampilkan saat kategori dibuka kembali.
- [ ] “Belum feedback” tidak ditampilkan sebagai 0%.

---

## UC-M05 — Mengumpulkan Studi Kasus Kinesthetic

| Field | Nilai |
|---|---|
| **Use Case ID** | UC-M05 |
| **Use Case Name** | Mengumpulkan Studi Kasus Kinesthetic |
| **Primary Actor** | Mahasiswa |
| **Description** | Mahasiswa mengetik satu jawaban final untuk studi kasus Kinesthetic dan mengirimkannya sebelum/tetap pada deadline; tidak ada draft dan tidak ada revisi. |
| **Trigger** | Mahasiswa menekan tombol Kirim pada studi kasus. |
| **Preconditions** | 1) Sesi Kinesthetic. 2) Studi kasus ada dan topik terbit. 3) Mahasiswa terdaftar dan belum pernah submit. 4) Deadline belum lewat (atau tepat pada deadline). |
| **Postconditions** | Tepat satu submission permanen; teks dan `submitted_at` tersimpan; status `MENUNGGU_PENILAIAN`. |

### Main Success Scenario

1. Mahasiswa membuka studi kasus dan membaca teori, instruksi, serta deadline (WIB).
2. Mahasiswa mengetik jawaban pada text editor; tidak ada autosave/draft.
3. Mahasiswa menekan Kirim.
4. Backend memvalidasi sesi Kinesthetic, enrollment, keberadaan kasus, belum ada submission, panjang 1–10.000, dan `server_now <= deadline_at`.
5. Backend menyimpan teks + waktu dalam satu transaksi dengan unique constraint.
6. Aplikasi menampilkan status `Menunggu Penilaian`.
7. Mahasiswa tidak lagi melihat tombol Kirim/revisi.

### Alternative Flows

- **A1 — Jawaban kosong/whitespace-only:** `422 ANSWER_LENGTH_INVALID`; tidak ada submission.
- **A2 — Jawaban >10.000 karakter:** `422 ANSWER_LENGTH_INVALID`; tidak ada submission.
- **A3 — Sudah pernah submit:** `409 ALREADY_SUBMITTED`; jawaban lama tetap.
- **A4 — Dua request bersamaan:** Satu berhasil, satu `409` karena unique constraint.
- **A5 — Deadline lewat saat form terbuka:** `409 DEADLINE_PASSED`; tidak ada submission.
- **A6 — Tepat pada deadline:** Diterima (`server_now <= deadline_at`).
- **A7 — VARK bukan Kinesthetic:** `403 VARK_MISMATCH`.
- **A8 — Studi kasus belum ada:** `404` pesan “studi kasus belum tersedia”.

### Exception Flows

- **E1 — Gangguan jaringan setelah Kirim:** Jangan mengulang otomatis tanpa cek status; muat ulang status (UC-M06) untuk memastikan.
- **E2 — Server error:** Tidak mengklaim berhasil; tidak ada draft.
- **E3 — Sesi kedaluwarsa:** `401`; mahasiswa harus login ulang, tetapi submission yang sudah tersimpan tetap final.
- **E4 — Mencoba mengubah jawaban via API:** `405 SUBMISSION_IMMUTABLE`.

### Business Rules

- BR-10, BR-13, BR-16: sekali, final, tanpa draft/revisi.
- BR-11, BR-14, BR-19: deadline dosen; tolak setelah deadline; tepat deadline diterima.
- BR-17: panjang 1–10.000.
- BR-18: lewat deadline tanpa submit → `Tidak Mengumpulkan`.
- D-08/D-09: satu kasus per topik; deadline terkunci setelah submission pertama.

### Related Functional Requirements

FR-023, FR-024, FR-025, FR-038, FR-039, FR-040, FR-041, FR-045, FR-046, FR-047, FR-048, FR-049, FR-050.

### Acceptance Criteria

- [ ] Jawaban 1–10.000 karakter diterima; 0 atau 10.001 ditolak.
- [ ] Tidak ada endpoint draft/autosave.
- [ ] Satu submission per mahasiswa–kasus; second submit `409`.
- [ ] Dua request paralel menghasilkan satu submission.
- [ ] Tepat deadline diterima; 1 ms setelahnya ditolak.
- [ ] Submission setelah deadline dari form lama tetap ditolak.
- [ ] Teks submission tidak berubah setelah dikirim/dinilai.
- [ ] Status berubah ke `Menunggu Penilaian`.

---

## UC-M06 — Melihat Status Pengumpulan dan Hasil Koreksi

| Field | Nilai |
|---|---|
| **Use Case ID** | UC-M06 |
| **Use Case Name** | Melihat Status Pengumpulan dan Hasil Koreksi |
| **Primary Actor** | Mahasiswa |
| **Description** | Mahasiswa melihat status studi kasusnya dan, jika sudah dinilai, melihat nilai 0–100 serta komentar dosen. |
| **Trigger** | Mahasiswa membuka halaman studi kasus/hasil. |
| **Preconditions** | 1) Sesi terautentikasi. 2) Mahasiswa memiliki akses ke studi kasus. |
| **Postconditions** | Status dan hasil milik mahasiswa ditampilkan; tidak ada opsi revisi. |

### Main Success Scenario

1. Mahasiswa membuka studi kasus.
2. Backend memeriksa akses dan mengambil deadline, submission, dan assessment.
3. Backend menentukan status:
   - belum submit + deadline belum lewat → `Belum Mengumpulkan`;
   - belum submit + deadline lewat → `Tidak Mengumpulkan`;
   - submit + belum dinilai → `Menunggu Penilaian`;
   - submit + dinilai → `Sudah Dinilai`.
4. Jika sudah dinilai, backend mengembalikan nilai dan komentar.
5. Aplikasi menampilkan status/hasil; tidak ada tombol Kirim/revisi.

### Alternative Flows

- **A1 — Belum submit sebelum deadline:** Status `Belum Mengumpulkan`; form Kirim tersedia.
- **A2 — Deadline lewat tanpa submit:** Status `Tidak Mengumpulkan`; tidak ada nilai 0; tidak ada submission palsu.
- **A3 — Sudah submit belum dinilai:** `Menunggu Penilaian`.
- **A4 — Sudah dinilai:** Nilai + komentar tampil; jawaban tetap terkunci.
- **A5 — Mahasiswa V/A/R:** Tidak ada status submission Kinesthetic.

### Exception Flows

- **E1 — Bukan pemilik:** `403`.
- **E2 — Studi kasus tidak ada:** `404`.
- **E3 — Sesi kedaluwarsa:** `401`.
- **E4 — Gangguan jaringan:** Tampilkan retry.

### Business Rules

- BR-12: nilai 0–100 + komentar; langsung terlihat.
- BR-13: final; tidak ada revisi.
- BR-18: `Tidak Mengumpulkan` bukan 0.
- FR-042: tiga status + `Tidak Mengumpulkan`.

### Related Functional Requirements

FR-027, FR-028, FR-042, FR-048.

### Acceptance Criteria

- [ ] Empat status ditampilkan dengan benar.
- [ ] `Tidak Mengumpulkan` tidak menampilkan nilai 0.
- [ ] Nilai/komentar muncul segera setelah dosen menyimpan.
- [ ] Mahasiswa hanya melihat miliknya.
- [ ] Tidak ada opsi revisi.

---

## UC-M07 — Logout

| Field | Nilai |
|---|---|
| **Use Case ID** | UC-M07 |
| **Use Case Name** | Logout (semua role) |
| **Primary Actor** | Mahasiswa, Dosen, Admin |
| **Description** | Pengguna mengakhiri sesi; VARK (jika ada) dilepas; login berikutnya meminta pilihan VARK hanya untuk mahasiswa. |
| **Trigger** | Pengguna menekan Logout. |
| **Preconditions** | Sesi aktif. |
| **Postconditions** | Sesi dicabut; token lokal dihapus; halaman login tampil; VARK tidak aktif lagi. |

### Main Success Scenario

1. Pengguna menekan Logout.
2. Aplikasi memanggil endpoint logout.
3. Backend mencabut sesi (`revoked_at`) dan refresh token.
4. Aplikasi menghapus token lokal dan state pengguna.
5. Halaman login ditampilkan.

### Alternative Flows

- **A1 — Token sudah tidak valid:** Aplikasi tetap menghapus token lokal dan menampilkan login.
- **A2 — Offline:** Aplikasi menghapus token lokal; revoke server dicoba saat online; token lama akan ditolak karena sesi tidak valid/kedaluwarsa.
- **A3 — Logout lalu login lagi:** Mahasiswa dapat memilih VARK baru; dosen/admin langsung dashboard tanpa VARK.

### Exception Flows

- **E1 — Gangguan jaringan:** Logout lokal tetap dilakukan; beri tahu bahwa sesi server akan dicabut saat koneksi tersedia.
- **E2 — Server error:** Sama seperti E1; jangan biarkan token tetap tersimpan.

### Business Rules

- BR-02: VARK berakhir bersama sesi (mahasiswa).
- D-04: satu sesi aktif; logout mencabut sesi tersebut.
- D-18: logout berlaku untuk semua role.

### Related Functional Requirements

FR-001, FR-006, FR-007, FR-008.

### Acceptance Criteria

- [ ] Setelah logout, token lama tidak dapat mengakses endpoint.
- [ ] Login berikutnya menampilkan pilihan VARK untuk mahasiswa.
- [ ] VARK sesi sebelumnya tidak diwariskan.
- [ ] Logout dosen/admin bekerja dan tidak meminta VARK.
- [ ] Logout lokal tetap bekerja saat offline.

---

## UC-D01 — Mengelola Topik IBD

| Field | Nilai |
|---|---|
| **Use Case ID** | UC-D01 |
| **Use Case Name** | Mengelola Topik IBD |
| **Primary Actor** | Dosen |
| **Description** | Dosen membuat, melihat, mengubah, mengurutkan, menerbitkan, meng-unpublish, dan mengarsipkan topik. |
| **Trigger** | Dosen membuka menu pengelolaan topik. |
| **Preconditions** | 1) Dosen terautentikasi. 2) Dosen di-assign ke kelas. |
| **Postconditions** | Data topik tersimpan; urutan/status publikasi sesuai; data akademik tetap jika topik diarsipkan. |

### Main Success Scenario

1. Dosen membuka daftar topik kelas.
2. Dosen memilih aksi: buat, ubah, urutkan, terbitkan, unpublish, atau arsipkan.
3. Sistem memvalidasi input dan kewenangan kelas.
4. Sistem menyimpan perubahan.
5. Sistem menampilkan daftar terbaru sesuai urutan.

### Alternative Flows

- **A1 — Materi belum lengkap:** Topik tetap boleh diterbitkan (BR-07).
- **A2 — Mengubah urutan:** `sort_order` diperbarui; daftar mahasiswa mengikuti.
- **A3 — Unpublish:** Topik disembunyikan dari mahasiswa; data tetap.
- **A4 — Hapus topik tanpa data akademik:** Soft delete.
- **A5 — Hapus topik dengan submission/assessment:** Ditolak; sarankan arsip/unpublish.

### Exception Flows

- **E1 — Input tidak valid:** `422`.
- **E2 — Kelas bukan kewenangan:** `403 CLASS_FORBIDDEN`.
- **E3 — Konflik urutan:** `409`; muat ulang.
- **E4 — Gangguan jaringan:** Retry.

### Business Rules

- BR-07: publikasi tanpa format lengkap.
- FR-017: urutan dosen.
- D-07: soft delete.

### Related Functional Requirements

FR-009, FR-013, FR-017.

### Acceptance Criteria

- [ ] Dosen dapat CRUD topik pada kelasnya.
- [ ] Topik dapat terbit meski materi belum lengkap.
- [ ] Urutan tersimpan dan tampil konsisten.
- [ ] Unpublish menyembunyikan topik dari mahasiswa.
- [ ] Topik dengan data akademik tidak dapat di-hard-delete.
- [ ] Dosen lain tidak dapat mengelola kelas ini.

---

## UC-D02 — Mengelola Format Materi VARK

| Field | Nilai |
|---|---|
| **Use Case ID** | UC-D02 |
| **Use Case Name** | Mengelola Format Materi VARK |
| **Primary Actor** | Dosen |
| **Description** | Dosen mengunggah atau menautkan materi untuk kategori VARK tertentu pada topik, secara independen per kategori. |
| **Trigger** | Dosen memilih topik dan kategori, lalu menekan Unggah/Tautkan. |
| **Preconditions** | 1) Dosen terautentikasi dan berwenang atas topik. 2) Topik ada. |
| **Postconditions** | Materi tersimpan dengan metadata; akses mahasiswa dibatasi VARK sesi. |

### Main Success Scenario

1. Dosen memilih topik.
2. Dosen memilih kategori VARK.
3. Dosen mengunggah file atau menautkan URL.
4. Backend memvalidasi MIME/magic bytes/ukuran/nama file.
5. Backend menyimpan objek ke R2 (bucket privat) dan metadata ke database.
6. Sistem menampilkan materi pada topik–kategori tersebut.
7. Dosen dapat menerbitkan topik tanpa menunggu kategori lain.

### Alternative Flows

- **A1 — Beberapa materi dalam satu kategori:** Diizinkan; semuanya tampil.
- **A2 — Mengganti/menghapus materi:** Soft delete; metadata tetap untuk audit.
- **A3 — Kategori lain belum ada:** Mahasiswa kategori itu mendapat pesan FR-016.
- **A4 — Materi Kinesthetic:** Boleh sebagai bahan pendukung; penilaian tetap studi kasus.

### Exception Flows

- **E1 — Tipe file tidak diizinkan:** `422 INVALID_FILE_TYPE`.
- **E2 — File >100 MB:** `413 FILE_TOO_LARGE`.
- **E3 — Storage gagal:** `502 STORAGE_UNAVAILABLE`; tidak ada metadata setengah jadi.
- **E4 — Bukan kelas dosen:** `403 CLASS_FORBIDDEN`.

### Business Rules

- BR-07, BR-08.
- D-05: R2, 100 MB, signed URL 5 menit.
- D-07: soft delete.

### Related Functional Requirements

FR-010, FR-011, FR-012, FR-013, FR-014, FR-015, FR-016.

### Acceptance Criteria

- [ ] Materi dapat diunggah per kategori secara independen.
- [ ] Metadata kategori/judul/tipe/ukuran/tanggal tersimpan.
- [ ] File invalid/terlalu besar ditolak.
- [ ] Bucket privat; akses hanya via signed URL.
- [ ] Materi kategori lain tidak terlihat mahasiswa.
- [ ] Materi soft-deleted tidak muncul.

---

## UC-D03 — Membuat Studi Kasus dan Deadline

| Field | Nilai |
|---|---|
| **Use Case ID** | UC-D03 |
| **Use Case Name** | Membuat Studi Kasus dan Deadline |
| **Primary Actor** | Dosen |
| **Description** | Dosen menambahkan teori, instruksi studi kasus, dan deadline pada topik Kinesthetic; satu topik memiliki satu studi kasus. |
| **Trigger** | Dosen membuka pengaturan studi kasus pada suatu topik. |
| **Preconditions** | 1) Dosen terautentikasi dan berwenang atas topik. 2) Topik ada. 3) Belum ada submission jika dosen akan mengubah deadline. |
| **Postconditions** | Studi kasus tersimpan dengan teori, instruksi, dan `deadline_at` UTC; mahasiswa Kinesthetic dapat melihatnya. |

### Main Success Scenario

1. Dosen memilih topik.
2. Dosen mengisi teori, instruksi studi kasus, dan deadline (input WIB).
3. Sistem mengonversi deadline ke UTC dan memvalidasi bahwa nilainya valid.
4. Sistem menyimpan studi kasus (1:1 dengan topik).
5. Sistem menampilkan deadline dalam WIB kepada mahasiswa Kinesthetic.
6. Sistem mencatat audit pembuatan/perubahan.

### Alternative Flows

- **A1 — Studi kasus sudah ada:** Dosen mengedit teori/instruksi/deadline selama belum ada submission.
- **A2 — Sudah ada submission pertama:** Deadline terkunci; perubahan ditolak `409 DEADLINE_LOCKED`.
- **A3 — Deadline di masa lalu:** Ditolak `422 DEADLINE_INVALID`.
- **A4 — Topik belum punya studi kasus:** Mahasiswa Kinesthetic mendapat pesan “studi kasus belum tersedia”.
- **A5 — Teori:** Hanya ditampilkan pada sesi Kinesthetic; bukan untuk V/A/R.

### Exception Flows

- **E1 — Bukan kelas dosen:** `403 CLASS_FORBIDDEN`.
- **E2 — Dua dosen mengubah bersamaan:** Optimistic concurrency/unique topic; yang kalah `409`.
- **E3 — Gangguan jaringan:** Retry; jangan simpan sebagian.
- **E4 — Input tidak valid:** `422`.

### Business Rules

- BR-11: dosen menentukan deadline.
- BR-14/BR-19: deadline server, inklusif.
- D-08: satu studi kasus per topik; teori hanya Kinesthetic.
- D-09: deadline terkunci setelah submission pertama; WIB.

### Related Functional Requirements

FR-023, FR-040.

### Acceptance Criteria

- [ ] Studi kasus 1:1 dengan topik.
- [ ] Teori/instruksi/deadline tersimpan.
- [ ] Deadline tampil WIB; tersimpan UTC.
- [ ] Deadline invalid ditolak.
- [ ] Deadline tidak dapat diubah setelah submission pertama.
- [ ] Mahasiswa Kinesthetic melihat pesan khusus jika belum ada kasus.

---

## UC-D04 — Memeriksa Pengumpulan Kinesthetic

| Field | Nilai |
|---|---|
| **Use Case ID** | UC-D04 |
| **Use Case Name** | Memeriksa Pengumpulan Kinesthetic |
| **Primary Actor** | Dosen |
| **Description** | Dosen melihat daftar mahasiswa dan status pengumpulan untuk suatu studi kasus, lalu membuka jawaban yang sudah terkumpul. |
| **Trigger** | Dosen membuka daftar pengumpulan pada studi kasus. |
| **Preconditions** | 1) Dosen terautentikasi dan berwenang atas kelas. 2) Studi kasus ada. |
| **Postconditions** | Dosen melihat status tiap mahasiswa; jawaban yang terkumpul dapat dibaca tanpa mengubahnya. |

### Main Success Scenario

1. Dosen memilih studi kasus.
2. Backend mengambil daftar mahasiswa terdaftar dan status masing-masing.
3. Backend menampilkan `Belum Mengumpulkan`, `Tidak Mengumpulkan`, `Menunggu Penilaian`, atau `Sudah Dinilai`.
4. Dosen memilih mahasiswa untuk membuka jawaban final.
5. Sistem menampilkan teks jawaban dan waktu kirim (read-only).

### Alternative Flows

- **A1 — Belum ada submission:** Daftar tetap tampil; status `Belum`/`Tidak Mengumpulkan`; detail jawaban kosong.
- **A2 — Deadline lewat tanpa submission:** `Tidak Mengumpulkan`; tidak ada nilai 0.
- **A3 — Sudah dinilai:** Dosen melihat nilai/komentar yang tersimpan (read-only).
- **A4 — Daftar kosong:** Empty state; tidak ada error.

### Exception Flows

- **E1 — Bukan kelas dosen:** `403 CLASS_FORBIDDEN`.
- **E2 — Studi kasus tidak ada:** `404`.
- **E3 — Gangguan jaringan:** Retry.

### Business Rules

- BR-18: `Tidak Mengumpulkan` bukan 0.
- FR-042: status tiga tahap.
- FR-028: akses terbatas.

### Related Functional Requirements

FR-028, FR-042, FR-048.

### Acceptance Criteria

- [ ] Daftar mahasiswa dan status tampil.
- [ ] `Tidak Mengumpulkan` tidak menampilkan nilai 0.
- [ ] Jawaban hanya dapat dibaca, tidak diubah.
- [ ] Dosen kelas lain tidak dapat melihat.
- [ ] Empty state tersedia.

---

## UC-D05 — Memberikan Penilaian Final

| Field | Nilai |
|---|---|
| **Use Case ID** | UC-D05 |
| **Use Case Name** | Memberikan Penilaian Final |
| **Primary Actor** | Dosen |
| **Description** | Dosen memberikan nilai integer 0–100 dan komentar opsional pada satu submission; penilaian pertama bersifat final dan langsung terlihat mahasiswa. |
| **Trigger** | Dosen menekan Simpan Penilaian pada submission. |
| **Preconditions** | 1) Dosen terautentikasi dan berwenang atas kelas. 2) Submission ada. 3) Submission belum memiliki assessment. |
| **Postconditions** | Tepat satu assessment tersimpan; nilai/komentar final; mahasiswa langsung melihatnya; teks submission tidak berubah. |

### Main Success Scenario

1. Dosen membuka jawaban submission.
2. Dosen memasukkan nilai integer 0–100 dan komentar opsional (≤1.000 karakter).
3. Dosen mengonfirmasi bahwa penilaian bersifat final.
4. Backend memvalidasi kewenangan, keberadaan submission, belum ada assessment, nilai, dan komentar.
5. Backend menyimpan assessment + audit dalam satu transaksi.
6. Mahasiswa langsung melihat nilai dan komentar.
7. Dosen tidak lagi melihat tombol ubah/hapus nilai.

### Alternative Flows

- **A1 — Nilai di luar 0–100:** `422 INVALID_SCORE`.
- **A2 — Komentar >1.000 karakter:** `422 INVALID_COMMENT`.
- **A3 — Sudah dinilai:** `409 ALREADY_ASSESSED`.
- **A4 — Dua dosen menilai bersamaan:** Satu berhasil, satu `409`.
- **A5 — Komentar kosong:** Disimpan sebagai `null`; nilai tetap tersimpan.

### Exception Flows

- **E1 — Bukan kelas dosen:** `403 CLASS_FORBIDDEN`.
- **E2 — Submission tidak ada:** `404`.
- **E3 — Audit gagal:** Transaksi rollback; assessment tidak tersimpan; submission tidak berubah.
- **E4 — Admin mencoba menilai:** `403 ADMIN_ACADEMIC_FORBIDDEN`.

### Business Rules

- BR-12: nilai 0–100 + komentar; langsung terlihat.
- BR-13: final; tidak dapat diubah.
- D-10: integer; komentar opsional ≤1.000.

### Related Functional Requirements

FR-026, FR-027, FR-028, FR-043, FR-044.

### Acceptance Criteria

- [ ] Nilai integer 0–100; di luar rentang ditolak.
- [ ] Komentar opsional ≤1.000; lebih dari itu ditolak.
- [ ] Hanya satu assessment per submission.
- [ ] Setelah tersimpan, tidak ada update/delete.
- [ ] Mahasiswa melihat hasil segera.
- [ ] Teks submission tetap sama setelah penilaian berhasil/gagal.
- [ ] Admin tidak dapat menilai.

---

## UC-D06 — Melihat Grafik Pemahaman

| Field | Nilai |
|---|---|
| **Use Case ID** | UC-D06 |
| **Use Case Name** | Melihat Grafik Pemahaman |
| **Primary Actor** | Dosen |
| **Description** | Dosen melihat agregasi feedback subjektif V/A/R per topik/kategori dan nilai Kinesthetic sebagai metrik terpisah. |
| **Trigger** | Dosen membuka dashboard analitik dan memilih topik/kategori. |
| **Preconditions** | 1) Dosen terautentikasi. 2) Dosen berwenang atas kelas. |
| **Postconditions** | Grafik mean/n/histogram ditampilkan; mahasiswa tanpa feedback dihitung terpisah; Kinesthetic terpisah. |

### Main Success Scenario

1. Dosen membuka dashboard analitik.
2. Dosen memilih topik dan/atau kategori V/A/R.
3. Backend mengambil feedback terbaru per mahasiswa–topik–kategori.
4. Backend menghitung mean, jumlah responden `n`, dan histogram.
5. Backend menghitung jumlah mahasiswa terdaftar yang belum memberi feedback.
6. Backend mengambil nilai Kinesthetic dari assessment sebagai metrik terpisah.
7. Aplikasi menampilkan grafik + definisi agregasi + `n`.

### Alternative Flows

- **A1 — Belum ada feedback:** Empty state `n=0`; bukan grafik 0%.
- **A2 — Sebagian mahasiswa belum feedback:** Ditampilkan sebagai count terpisah, tidak masuk mean.
- **A3 — Filter per mahasiswa:** Menampilkan feedback mahasiswa tersebut per kategori asal.
- **A4 — Tidak ada topik/submission:** Tetap boleh membuka; empty state.

### Exception Flows

- **E1 — Bukan kelas dosen:** `403 CLASS_FORBIDDEN`.
- **E2 — Gangguan jaringan:** Retry.
- **E3 — Data tidak konsisten:** Gunakan query agregasi server; jangan hitung di klien.

### Business Rules

- BR-04: feedback terbaru per triple.
- BR-05: Kinesthetic objektif; jangan dicampur.
- D-12: mean + n + histogram; belum feedback terpisah.

### Related Functional Requirements

FR-029, FR-030, FR-031, FR-032, FR-033.

### Acceptance Criteria

- [ ] Grafik per topik/kategori V/A/R.
- [ ] Mean, `n`, dan histogram ditampilkan.
- [ ] Definisi agregasi terlihat.
- [ ] Mahasiswa tanpa feedback tidak dihitung 0%.
- [ ] Nilai Kinesthetic terpisah.
- [ ] Filter per mahasiswa bekerja.
- [ ] Dosen kelas lain tidak dapat melihat.

---

## UC-A01 — Membuat Akun

| Field | Nilai |
|---|---|
| **Use Case ID** | UC-A01 |
| **Use Case Name** | Membuat Akun |
| **Primary Actor** | Admin |
| **Description** | Admin membuat akun mahasiswa, dosen, atau admin lain dengan identitas unik dan role yang valid; akun otomatis di-enroll/di-assign ke kelas tunggal. |
| **Trigger** | Admin menekan Buat Akun. |
| **Preconditions** | 1) Admin terautentikasi. 2) `login_id` belum dipakai. 3) Role valid. |
| **Postconditions** | Akun baru tersimpan; password sementara; enrollment/teaching assignment dibuat; audit tercatat. |

### Main Success Scenario

1. Admin membuka manajemen pengguna.
2. Admin mengisi nama, NIM/NIP, role, dan password sementara.
3. Sistem memvalidasi field, keunikan `login_id`, dan role.
4. Sistem membuat akun dengan `must_change_password = true`.
5. Sistem membuat `enrollments` (mahasiswa) atau `teaching_assignments` (dosen) ke kelas tunggal.
6. Sistem mencatat audit `ACCOUNT_CREATED`.
7. Sistem menampilkan akun baru.

### Alternative Flows

- **A1 — Admin membuat admin lain:** Diizinkan (D-11); tetap dicatat audit.
- **A2 — Role di luar tiga kategori:** `422 INVALID_ROLE`.
- **A3 — Login duplikat:** `409 LOGIN_ID_TAKEN`.
- **A4 — Daftar akun kosong:** Tawarkan pembuatan akun pertama.

### Exception Flows

- **E1 — Input tidak valid:** `422` dengan detail field.
- **E2 — Gagal membuat enrollment/assignment:** Transaksi rollback; akun tidak setengah jadi.
- **E3 — Gangguan jaringan:** Retry; cek daftar untuk menghindari duplikasi.

### Business Rules

- BR-06: admin hanya manajemen akun.
- D-06: auto-enroll/assign.
- D-11: password sementara; admin boleh membuat admin.

### Related Functional Requirements

FR-034, FR-035, FR-036.

### Acceptance Criteria

- [ ] Akun dibuat dengan role valid.
- [ ] Login duplikat ditolak.
- [ ] Password sementara + wajib ganti.
- [ ] Mahasiswa otomatis enrolled; dosen otomatis assigned.
- [ ] Admin dapat membuat admin lain.
- [ ] Audit tercatat.
- [ ] Admin tidak dapat memasukkan data akademik.

---

## UC-A02 — Melihat dan Memperbarui Akun

| Field | Nilai |
|---|---|
| **Use Case ID** | UC-A02 |
| **Use Case Name** | Melihat dan Memperbarui Akun |
| **Primary Actor** | Admin |
| **Description** | Admin mencari, melihat, dan memperbarui atribut akun yang diizinkan (nama, role, reset password) tanpa menyentuh data akademik. Deaktivasi status ditangani UC-A03. |
| **Trigger** | Admin memilih akun dari daftar. |
| **Preconditions** | 1) Admin terautentikasi. 2) Akun target ada. |
| **Postconditions** | Perubahan tersimpan; audit tercatat; sesi target dicabut jika role berubah. |

### Main Success Scenario

1. Admin membuka daftar pengguna dan mencari akun.
2. Admin memilih akun target.
3. Sistem menampilkan atribut yang diizinkan.
4. Admin mengubah nama/role atau mereset password.
5. Sistem memvalidasi keunikan dan role.
6. Sistem menyimpan perubahan, mencabut sesi target jika perlu, dan mencatat audit.

### Alternative Flows

- **A1 — Login duplikat:** `409 LOGIN_ID_TAKEN`.
- **A2 — Role invalid:** `422 INVALID_ROLE`.
- **A3 — Target tidak ada:** `404`.
- **A4 — Admin mencoba mengubah materi/feedback/nilai:** `403 ADMIN_ACADEMIC_FORBIDDEN`.
- **A5 — Admin mencoba menonaktifkan akun lewat layar ubah:** arahkan ke UC-A03; perubahan status tidak dilakukan di UC-A02.

### Exception Flows

- **E1 — Konflik update bersamaan:** `409`; muat ulang.
- **E2 — Gangguan jaringan:** Retry.
- **E3 — Reset password gagal:** Tidak ada perubahan setengah jadi.

### Business Rules

- BR-06: hanya manajemen akun.
- D-11: role; reset password; guardrail admin. Deaktivasi status ada di UC-A03.
- D-13: audit.

### Related Functional Requirements

FR-034, FR-035, FR-036, FR-037.

### Acceptance Criteria

- [ ] Admin dapat melihat dan mencari akun.
- [ ] Perubahan role tervalidasi.
- [ ] Login tetap unik.
- [ ] Sesi target dicabut saat role berubah.
- [ ] Admin tidak dapat mengubah data akademik.
- [ ] Audit tercatat.

> Deaktivasi status akun ditangani UC-A03; UC-A02 tidak mengubah status.

---

## UC-A03 — Menonaktifkan Akun

| Field | Nilai |
|---|---|
| **Use Case ID** | UC-A03 |
| **Use Case Name** | Menonaktifkan Akun |
| **Primary Actor** | Admin |
| **Description** | Admin menonaktifkan akun target sehingga tidak dapat login lagi; semua sesi aktif dicabut; histori akademik tetap. |
| **Trigger** | Admin menekan Nonaktifkan pada akun. |
| **Preconditions** | 1) Admin terautentikasi. 2) Akun target ada. 3) Target bukan diri sendiri/admin aktif terakhir. |
| **Postconditions** | Akun `INACTIVE`; semua sesi dicabut; login berikutnya ditolak; histori tetap. |

### Main Success Scenario

1. Admin membuka akun target.
2. Admin memilih Nonaktifkan.
3. Sistem meminta konfirmasi.
4. Sistem memvalidasi target bukan self/admin terakhir.
5. Sistem mengubah status menjadi `INACTIVE`, mencabut semua sesi, dan mencatat audit.
6. Login berikutnya ditolak.

### Alternative Flows

- **A1 — Akun sudah nonaktif:** Tampilkan status saat ini; tidak ada perubahan.
- **A2 — Target tidak ada:** `404`.
- **A3 — Target adalah admin terakhir/diri sendiri:** `409 LAST_ADMIN_PROTECTED`.
- **A4 — Sesi target sedang aktif:** Semua sesi dicabut segera.

### Exception Flows

- **E1 — Gangguan jaringan:** Retry; status harus konsisten.
- **E2 — Audit gagal:** Rollback; akun tetap aktif.
- **E3 — Konflik status:** `409`; muat ulang.

### Business Rules

- BR-06: admin hanya manajemen akun.
- D-11: deactivation + revoke sessions + guardrail.
- D-13: histori tetap.

### Related Functional Requirements

FR-034, FR-037.

### Acceptance Criteria

- [ ] Akun nonaktif tidak dapat login.
- [ ] Semua sesi aktif dicabut.
- [ ] Histori nilai/feedback tetap.
- [ ] Admin tidak dapat menonaktifkan diri sendiri/admin terakhir.
- [ ] Audit tercatat.
- [ ] Akun sudah nonaktif ditangani dengan benar.

---

## 4. Matriks dependensi ringkas

| Use case | Prasyarat data/otorisasi | Tidak mewajibkan |
|---|---|---|
| UC-M01 | Akun aktif | — |
| UC-M02 | Sesi + VARK + enrollment | Pembuatan topik |
| UC-M03 | Topik terbit + VARK + enrollment | Feedback |
| UC-M04 | V/A/R + materi tersedia | Submit Kinesthetic |
| UC-M05 | Kinesthetic + kasus + belum submit + deadline | Penilaian |
| UC-M06 | Akses ke kasus | Submission berhasil |
| UC-M07 | Sesi aktif | — |
| UC-D01 | Dosen + kelas | Pembuatan akun |
| UC-D02 | Topik ada | Pembuatan topik baru |
| UC-D03 | Topik ada | Submission |
| UC-D04 | Kasus ada | Submission |
| UC-D05 | Submission belum dinilai | Pembuatan kasus |
| UC-D06 | Kelas | Topik/submission/penilaian |
| UC-A01 | Admin | — |
| UC-A02 | Akun target ada | UC-A01 pada sesi yang sama |
| UC-A03 | Akun target ada | UC-A02 |

> **Catatan:** Tabel ini adalah *data/authorization preconditions*, bukan relasi UML `<<include>>`. Diagram dependensi ada di `USE_CASE_DEPENDENCIES_EDUVARK_V0.7.md`.


