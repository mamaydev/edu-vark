# Decision Log — EduVARK v0.7

Tanggal keputusan: **20 November 2026**  
Sumber: jawaban wawancara atas Q1–Q13 pada analisis awal.

> Status **CONFIRMED** di sini berarti telah disetujui melalui wawancara, bukan klaim bahwa seluruh detail turunannya sudah final. Detail yang belum diputuskan tetap dicatat di `srs/OPEN_QUESTIONS.md`.

| ID | Topik | Keputusan | Dampak utama |
|---|---|---|---|
| D-01 | Platform & identitas | Android native (Kotlin/Compose) sebagai satu-satunya klien; backend REST; proyek/package menjadi **EduVARK** / `com.eduvark.app`; monorepo `app/` + `backend/`. SRS §2.4/§4.2 perlu addendum. | Menyelesaikan konflik “browser” vs mandat Android. Rename dilakukan saat implementasi, bukan di tahap dokumentasi. |
| D-02 | Database | **PostgreSQL melalui Supabase** sebagai database utama. | Skema memakai fitur PostgreSQL: `CHECK`, `UNIQUE`, `timestamptz`, trigger, transaksi. |
| D-03 | Mekanisme auth | **JWT access token + refresh token + tabel sesi server**. Bukan JWT murni. | Logout/penonaktifan dapat mencabut sesi; VARK tersimpan di sesi server. |
| D-04 | Siklus sesi | **Satu sesi aktif untuk semua role**; idle 8 jam, absolut 12 jam; login baru mencabut sesi lama. | Mahasiswa tidak dapat memakai dua sesi dengan VARK berbeda; staf harus login ulang di perangkat lain. |
| D-05 | Storage materi | **Cloudflare R2 (S3-compatible)**, bucket privat, URL bertanda tangan 5 menit; banyak materi per topik–kategori; PDF, audio, video, gambar, tautan eksternal; batas 100 MB/file (configurable). | Materi tidak public; validasi MIME/ukuran di backend; “materi tersedia” = minimal satu materi non-deleted. |
| D-06 | Kelas & enrollment | Tabel `classes`, `enrollments`, `teaching_assignments`; mahasiswa otomatis di-enroll dan dosen otomatis di-assign saat akun dibuat; **tidak ada UI enrollment baru** di MVP. | FR-014 dapat ditegakkan; BR-15 punya tempat data; tidak menambah scope UI admin. |
| D-07 | Siklus topik/materi | **Soft delete** (`deleted_at`); hard delete ditolak jika ada submission/assessment; unpublish diperbolehkan. | Nilai, feedback, dan audit tetap terjaga; FR-009 “hapus” dimaknai sebagai arsip. |
| D-08 | Studi kasus Kinesthetic | **Satu studi kasus per topik**; teori + instruksi + deadline; materi Kinesthetic boleh ada sebagai bahan pendukung; teori hanya untuk sesi Kinesthetic; jika belum ada, tampilkan **“studi kasus belum tersedia”**. | Model 1:1 topik–studi kasus; submission tetap unik per mahasiswa–studi kasus. |
| D-09 | Deadline | **Tidak dapat diubah setelah submission pertama**; simpan UTC; tampilkan **WIB (UTC+7)**; status diturunkan dari deadline + submission + assessment. | Menjaga keadilan dan menghindari status ganda; TBD-10 selesai. |
| D-10 | Penilaian | Nilai **integer 0–100**; komentar **opsional**, maksimal **1.000 karakter**. | Validasi lebih sederhana; mahasiswa dapat nilai tanpa komentar. |
| D-11 | Akun | Login **NIM/NIP**; admin menetapkan password sementara; wajib ganti saat login pertama; admin dapat reset; penonaktifan mencabut semua sesi; **admin boleh membuat admin lain**, tetapi tidak boleh menonaktifkan diri sendiri/admin terakhir. | Menambah alur ganti password pertama; guardrail admin. |
| D-12 | Analitik | **Mean + n + histogram**; hitung “belum memberi feedback” terpisah; per topik/kategori; Kinesthetic di metrik terpisah. | Memenuhi FR-031/FR-032/FR-033 tanpa mencampur metrik. |
| D-13 | Audit & retensi | Audit login/logout, pemilihan VARK, penyimpanan nilai, dan perubahan akun; retensi **satu semester + 1 tahun**; soft delete; mahasiswa keluar kelas kehilangan akses tetapi histori nilainya tetap. | Mendukung TBD-01/BR-15; durasi purge tetap open question. |
| D-14 | Penamaan VARK | Enum kanonik **`AURAL`**, label UI **“Audio”**; glosarium mencatat ekuivalensi. | FR-005 tetap terwakili; UI konsisten. |
| D-15 | Cakupan Supabase | Supabase hanya sebagai **managed PostgreSQL**; auth custom (D-03); storage Cloudflare R2 (D-05); tidak memakai Supabase Auth/Storage. | OQ-01 selesai; tidak ada duplikasi identitas. |
| D-16 | Kebijakan password/token | Password minimal 12 karakter, tanpa aturan komposisi wajib, blokir password umum, Argon2id; access token 15 menit; refresh token 12 jam (rotasi); idle 8 jam/absolut 12 jam; lockout 5 gagal/15 menit. | OQ-02 selesai; selaras dengan D-04. |
| D-17 | Mahasiswa keluar kelas | Admin mengubah enrollment menjadi `LEFT` dengan tanggal efektif melalui endpoint admin (tanpa UI khusus); akses dicabut; histori tetap; re-enroll mengaktifkan kembali. | OQ-07 selesai; BR-15 punya mekanisme. |
| D-18 | Login/logout staf | UC-M01 dan UC-M07 diperluas untuk semua role; mahasiswa memilih VARK, dosen/admin langsung dashboard; tidak ada UC baru. | OQ-15 selesai; FR-001 tetap butuh AT-P11 untuk pengujian staf. |
| D-19 | Kebijakan password (override pengguna) | Password **hanya minimal 6 karakter**; tanpa aturan komposisi, tanpa blokir password umum, tanpa batas maksimum, dan boleh sama dengan password lama. Argon2id dan kebijakan token tetap. | Menggantikan bagian panjang/kompleksitas D-16 atas permintaan pengguna, 22 November 2026. |

## Catatan implementasi

- Rename proyek/package (D-01) **belum dilakukan** pada tahap dokumentasi ini.
- Supabase pada D-02 dimaknai sebagai **managed PostgreSQL** (D-15). Auth custom (D-03), storage R2 (D-05).
- D-03 dan D-04 saling terkait: JWT access token tidak menjadi satu-satunya sumber kebenaran; tabel sesi tetap diperiksa.
