# Open Questions — EduVARK v0.7

**Status:** Daftar keputusan yang belum final.  
**Aturan:** TBD/CONFLICT tidak boleh diperlakukan sebagai keputusan implementasi sampai ada persetujuan eksplisit.  
**Sumber keputusan yang sudah final:** [`../DECISIONS.md`](../DECISIONS.md) (D-01–D-18).

## Cara menjawab

Cukup tulis: `OQ-01=A, OQ-02=A, OQ-03=A, ...` atau `setuju semua rekomendasi kecuali ...`.

## Keputusan P0 yang sudah final

| OQ | Keputusan | Ref |
|---|---|---|
| OQ-01 | Supabase hanya managed PostgreSQL; auth custom; storage R2 | D-15 |
| OQ-02 | Password min. 12; access 15 menit; refresh 12 jam; lockout 5/15 menit; idle 8 jam/absolut 12 jam | D-16 |
| OQ-07 | Admin mengubah enrollment menjadi `LEFT` via endpoint; histori tetap; re-enroll mengaktifkan kembali | D-17 |
| OQ-15 | UC-M01/M07 diperluas untuk semua role; staf tanpa VARK; tidak ada UC baru | D-18 |

## Prioritas

| Prioritas | Open question | Alasan |
|---|---|---|
| **P0 — selesai** | OQ-01, OQ-02, OQ-07, OQ-15 | Sudah CONFIRMED melalui D-15–D-18 |
| **P1 — penting** | OQ-03, OQ-04, OQ-05, OQ-08, OQ-09, OQ-11 | Memengaruhi keamanan, operasi, dan pengujian |
| **P2 — dapat menyusul** | OQ-06, OQ-10, OQ-12, OQ-13, OQ-14 | Memengaruhi UI/scope, bukan aturan inti |

---

## OQ-01 — Cakupan Supabase

**Status:** **CONFIRMED (D-15)**  
**Masalah:** Q2 memilih Supabase, Q3a memilih JWT + refresh + session table, Q4 memilih Cloudflare R2. Supabase juga menyediakan Auth/Storage; belum jelas apakah yang dimaksud hanya PostgreSQL.

**Opsi:**
- **A. Supabase hanya managed PostgreSQL; auth custom; storage R2.**
  - + selaras D-03/D-05; kontrol penuh sesi/VARK; tidak ada duplikasi identitas.
  - − harus membangun auth sendiri.
- **B. Supabase Auth + PostgreSQL; session VARK di tabel aplikasi; storage R2.**
  - + auth terkelola; reset password/email built-in.
  - − integrasi JWT Supabase dengan session table; dua sumber identitas.
- **C. Supabase Auth + Storage; R2 tidak dipakai.**
  - + satu platform.
  - − bertentangan D-05; lock-in.

**Keputusan:** **A (D-15)** — Supabase hanya managed PostgreSQL; auth custom; storage R2; Supabase Auth/Storage tidak dipakai.  
**Pertanyaan:** — (selesai).

---

## OQ-02 — Kebijakan password dan token

**Status:** **CONFIRMED (D-16)**  
**Masalah:** D-11 menetapkan password sementara dan wajib ganti, tetapi belum menetapkan panjang, kompleksitas, TTL token, rotasi refresh, dan lockout.

**Opsi:**
- **A. Minimal 12 karakter, tanpa komposisi wajib, blokir password umum; access 15 menit; refresh 12 jam (rotasi); lockout 5 gagal/15 menit.**
  - + praktik modern; selaras sesi absolut 12 jam.
  - − butuh daftar password umum.
- **B. Minimal 8 karakter dengan campuran karakter; access 30 menit; refresh 30 hari.**
  - + familiar.
  - − aturan komposisi lemah; refresh melebihi sesi.
- **C. Passphrase minimal 16 karakter; access 10 menit; refresh 12 jam; lockout 10/30 menit.**
  - + kuat.
  - − friksi tinggi.

**Keputusan:** **A (D-16)** — password min. 12, tanpa komposisi wajib, blokir password umum, Argon2id; access 15 menit; refresh 12 jam rotating; lockout 5 gagal/15 menit; idle 8 jam/absolut 12 jam.  
**Override implementasi (D-19, 22 Nov 2026):** password hanya minimal **6 karakter**, tanpa blokir password umum/batas maksimum, dan boleh sama dengan password lama. Token/sesi tetap mengikuti D-16.  
**Pertanyaan:** — (selesai).

---

## OQ-03 — Backup, DR, dan purge

**Status:** TBD  
**Masalah:** Retensi 1 tahun (D-13) sudah disetujui, tetapi frekuensi backup, RPO/RTO, versioning R2, dan purge belum.

**Opsi:**
- **A. Supabase daily backup + PITR 7 hari; R2 versioning; purge otomatis setelah 1 tahun dengan persetujuan; RPO 24 jam, RTO 4 jam.**
  - + cukup untuk satu kelas; biaya terkendali.
  - − PITR terbatas 7 hari.
- **B. Backup mingguan tanpa PITR; purge manual; RPO 7 hari, RTO 24 jam.**
  - + murah.
  - − risiko kehilangan data seminggu.
- **C. Simpan selamanya.**
  - + tidak ada risiko kehilangan.
  - − biaya dan kepatuhan retensi tidak jelas.

**Rekomendasi:** A.  
**Pertanyaan:** Setujui RPO 24 jam/RTO 4 jam dan purge setelah 1 tahun?

---

## OQ-04 — Akses audit log

**Status:** TBD  
**Masalah:** D-13 menetapkan event audit, tetapi belum menetapkan siapa yang dapat membacanya.

**Opsi:**
- **A. Admin melihat semua (read-only); dosen melihat aksi sendiri; mahasiswa melihat riwayat login sendiri.**
  - + transparansi terbatas; mendukung investigasi.
  - − UI/API tambahan.
- **B. Hanya admin.**
  - + sederhana.
  - − dosen tidak dapat menelusuri nilainya sendiri.
- **C. Tidak ada UI; akses DB oleh operator.**
  - + tanpa pekerjaan UI.
  - − tidak operasional; akses tidak terkontrol.

**Rekomendasi:** A.  
**Pertanyaan:** Setujui A, atau cukup admin?

---

## OQ-05 — Target performa dan kapasitas

**Status:** TBD  
**Masalah:** §5.1 SRS menyatakan target kuantitatif belum ditetapkan.

**Opsi:**
- **A. p95 < 2 detik baca, < 3 detik tulis; 100 pengguna bersamaan; media dari R2.**
  - + realistis untuk satu kelas.
  - − belum menangani lonjakan besar.
- **B. p95 < 1 detik; 500 pengguna; butuh caching/optimasi.**
  - + ambisius.
  - − biaya/kompleksitas.
- **C. Best effort, tanpa target.**
  - + tidak ada beban uji.
  - − tidak dapat dinyatakan lulus acceptance.

**Rekomendasi:** A.  
**Pertanyaan:** Setujui target A?

---

## OQ-06 — Ekspor laporan

**Status:** TBD  
**Masalah:** §1.1 menyebut “laporan”, tetapi FR-029–FR-033 hanya grafik; tidak ada ekspor.

**Opsi:**
- **A. Grafik saja; ekspor di luar scope MVP.**
  - + sesuai FR; fokus.
  - − dosen tidak dapat mengunduh data.
- **B. Tambah ekspor CSV feedback/nilai.**
  - + berguna untuk arsip.
  - − fitur baru; perlu persetujuan.
- **C. Tambah ekspor PDF.**
  - + presentasi rapi.
  - − pekerjaan paling besar.

**Rekomendasi:** A untuk MVP; B sebagai change request.  
**Pertanyaan:** Apakah ekspor diperlukan?

---

## OQ-07 — Mekanisme mahasiswa keluar kelas

**Status:** **CONFIRMED (D-17)**  
**Masalah:** D-13 menetapkan kebijakan kehilangan akses/histori tetap, tetapi belum menetapkan siapa yang mengubah status, tanggal efektif, dan re-enroll.

**Opsi:**
- **A. Admin mengubah status enrollment menjadi `LEFT` dengan tanggal efektif via endpoint admin (tanpa UI khusus); akses dicabut; histori tetap; re-enroll mengaktifkan kembali.**
  - + jelas dan auditabel.
  - − endpoint admin baru di luar FR-034.
- **B. Hapus baris enrollment; histori lewat arsip.**
  - + bersih.
  - − kehilangan relasi historis.
- **C. Tidak mendukung di MVP.**
  - + tanpa pekerjaan.
  - − BR-15 tetap TBD.

**Keputusan:** **A (D-17)** — admin mengubah enrollment menjadi `LEFT` dengan tanggal efektif via endpoint admin (tanpa UI khusus); akses dicabut; histori tetap; re-enroll mengaktifkan kembali.  
**Pertanyaan:** — (selesai).

---

## OQ-08 — Dosen mana yang boleh menilai?

**Status:** TBD  
**Masalah:** D-06 meng-auto-assign dosen ke satu kelas. Jika ada beberapa dosen, belum jelas apakah semuanya boleh menilai dan bagaimana race-nya.

**Opsi:**
- **A. Semua dosen kelas boleh menilai satu kali; unique assessment; audit mencatat penilai.**
  - + sederhana; sesuai satu kelas.
  - − dosen lain bisa menilai topik bukan buatannya.
- **B. Hanya pembuat topik/kasus.**
  - + tanggung jawab jelas.
  - − menyulitkan jika berhalangan.
- **C. Admin menunjuk satu penilai per kasus.**
  - + eksplisit.
  - − fitur/UI baru.

**Rekomendasi:** A.  
**Pertanyaan:** Setujui A?

---

## OQ-09 — Bootstrap admin pertama

**Status:** TBD/RECOMMENDATION  
**Masalah:** D-11 membolehkan admin membuat admin, tetapi admin pertama belum ditentukan.

**Opsi:**
- **A. Seed sekali via environment/CLI; password sementara; wajib ganti; tidak ada password default di repo.**
  - + aman dan reproducible.
  - − butuh prosedur deploy.
- **B. Manual SQL insert.**
  - + cepat.
  - − rawan kesalahan; tidak terdokumentasi.
- **C. Endpoint setup publik.**
  - + mudah.
  - − risiko keamanan besar.

**Rekomendasi:** A.  
**Pertanyaan:** Setujui A?

---

## OQ-10 — Bahasa dan copy UI

**Status:** RECOMMENDATION  
**Masalah:** Hanya pesan FR-016 dan “studi kasus belum tersedia” yang disetujui. Copy lain belum.

**Opsi:**
- **A. Bahasa Indonesia saja; string terpusat; product owner review.**
  - + konsisten; mudah diuji.
  - − tidak multibahasa.
- **B. ID + EN.**
  - + fleksibel.
  - − pekerjaan tambahan.
- **C. Bebas per layar.**
  - + cepat.
  - − tidak konsisten.

**Rekomendasi:** A.  
**Pertanyaan:** Setujui A?

---

## OQ-11 — MIME, ukuran per tipe, dan antivirus

**Status:** RECOMMENDATION  
**Masalah:** D-05 menetapkan 100 MB/file dan tipe luas; belum ada batas per tipe, allowlist MIME, dan scanning.

**Opsi:**
- **A. PDF 25 MB; gambar 10 MB; audio 50 MB; video 100 MB; tautan tanpa batas; MIME allowlist + magic bytes; antivirus opsional.**
  - + proporsional; aman.
  - − lebih banyak aturan.
- **B. 100 MB untuk semua tipe.**
  - + sederhana.
  - − video besar/PDF besar tidak dibedakan.
- **C. 50 MB untuk semua.**
  - + hemat.
  - − video kualitas tinggi terbatas.

**Rekomendasi:** A.  
**Pertanyaan:** Setujui A?

---

## OQ-12 — Konvensi API

**Status:** RECOMMENDATION  
**Masalah:** SRS tidak menetapkan versi, pagination, format error, dan kontrak.

**Opsi:**
- **A. `/api/v1` + envelope + pagination + OpenAPI.**
  - + stabil; Android/backend selaras.
  - − sedikit pekerjaan awal.
- **B. Tanpa versi; array sederhana.**
  - + cepat.
  - − sulit berevolusi.
- **C. GraphQL.**
  - + fleksibel.
  - − tidak sesuai stack Retrofit/REST yang diminta.

**Rekomendasi:** A.  
**Pertanyaan:** Setujui A?

---

## OQ-13 — Offline, push, email

**Status:** RECOMMENDATION  
**Masalah:** Tidak ada requirement.

**Opsi:**
- **A. Di luar scope MVP.**
  - + fokus.
  - − tidak ada notifikasi.
- **B. Cache lokal read-only.**
  - + baca materi terakhir.
  - − kompleksitas sinkronisasi.
- **C. Offline penuh.**
  - + pengalaman terbaik.
  - − sangat besar; tidak diminta.

**Rekomendasi:** A.  
**Pertanyaan:** Setujui A?

---

## OQ-14 — Privasi analytics

**Status:** RECOMMENDATION  
**Masalah:** FR-030 memungkinkan dosen melihat feedback per mahasiswa; belum jelas apakah nama ditampilkan.

**Opsi:**
- **A. Nama terlihat oleh dosen kelas; admin tidak.**
  - + mendukung intervensi pedagogis.
  - − data pribadi terlihat.
- **B. Agregat anonim.**
  - + privasi.
  - − dosen tidak bisa menindaklanjuti individu.
- **C. Dapat dikonfigurasi.**
  - + fleksibel.
  - − kompleksitas.

**Rekomendasi:** A.  
**Pertanyaan:** Setujui A?

---

## OQ-15 — Use case login/logout dosen dan admin

**Status:** **CONFIRMED (D-18)**  
**Masalah:** SRS hanya memiliki UC-M01 (login + pilih VARK) dan UC-M07 (logout) untuk mahasiswa. FR-001 mencakup login/logout ketiga role, tetapi dosen/admin tidak memiliki UC tersendiri; saat ini diperlakukan sebagai *shared precondition*. Ini menyulitkan traceability dan pengujian RBAC.

**Opsi:**
- **A. Tambahkan dua use case baru: `UC-X01 Login staf` dan `UC-X02 Logout staf`, dengan actor Dosen/Admin.**
  - + traceability jelas; pengujian RBAC eksplisit.
  - − menambah jumlah UC dari 16 menjadi 18; menyimpang dari SRS.
- **B. Perluas UC-M01 dan UC-M07 agar berlaku untuk semua role, dengan cabang mahasiswa memilih VARK dan staf langsung dashboard.**
  - + tetap 16 UC; tidak menambah ID.
  - − nama UC-M (mahasiswa) menjadi kurang tepat; perlu catatan.
- **C. Biarkan sebagai *shared precondition* dan dokumentasikan GAP.**
  - + tidak mengubah SRS.
  - − FR-001 tetap PARTIAL; pengujian login staf tidak punya UC.

**Keputusan:** **B (D-18)** — UC-M01/M07 menjadi “Login/Logout (semua role)”; cabang VARK hanya untuk mahasiswa; tidak ada UC baru. AT-P11 tetap diperlukan untuk menguji login/logout staf.

**Pertanyaan:** — (selesai).
