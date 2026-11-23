# Analisis sistem EduVARK

Tanggal peninjauan: 23 November 2026. Analisis bersumber dari kode lokal dan dokumen proyek; tidak menjalankan pengujian atau memeriksa server produksi. Diagram menggambarkan implementasi yang tersedia, sedangkan status server mengikuti catatan deployment proyek.

## Arsitektur dan batas sistem

Klien Android native memakai Kotlin, Jetpack Compose, Hilt, Retrofit dan DataStore. Backend Express/TypeScript menyediakan API `/api/v1`, autentikasi dan otorisasi. PostgreSQL menyimpan identitas, sesi, kelas dan data akademik. Cloudflare R2 menyimpan berkas materi; database menyimpan metadata atau tautan HTTPS eksternal.

README dan runbook mencatat backend pada LXC 10100 dengan PostgreSQL 17 lokal, ingress Traefik dan Cloudflare Tunnel. Build release Android menunjuk `https://pam.sequein.my.id/api/v1/`; build debug dapat menggunakan server lokal. Supabase merupakan keputusan rancangan sebelumnya dan tidak menggambarkan deployment homelab yang tercatat saat ini.

## Peran dan proses

Mahasiswa login, mengganti password awal bila diwajibkan, kemudian memilih satu VARK untuk sesi tersebut. Akses akademik mensyaratkan enrollment aktif dan topik terbit. Visual, Aural dan Read/Write mengakses materi kategorinya dan dapat menyimpan feedback integer 0–100. Kinesthetic mengakses kasus, mengirim jawaban final, dan melihat status atau penilaian sendiri.

Dosen mengelola topik di kelas yang menjadi kewenangannya, mengurutkan dan menerbitkannya, mengelola file atau tautan per kategori, menyiapkan kasus, memeriksa pengumpulan, menilai sekali, dan membaca analitik. Topik dapat terbit sebelum semua kategori memiliki materi. Operasi tersebut adalah pilihan yang mempunyai prasyarat data; dosen tidak harus membuat topik baru setiap kali ingin menilai.

Admin mencari, membuat dan memperbarui akun, mereset password, menonaktifkan akun, dan mengubah status enrollment. Akun mahasiswa atau dosen baru dikaitkan ke kelas aktif menurut implementasi layanan. Admin tidak mempunyai wewenang akademik untuk menilai atau mengubah feedback. Penonaktifan diri sendiri dan perlindungan admin terakhir ditangani backend.

## Sesi dan keamanan akses

Kode autentikasi membuat sesi baru dan mencabut sesi aktif sebelumnya. VARK ditetapkan sekali dalam sesi mahasiswa; dosen/admin tidak memilih VARK. Refresh merotasi token dengan mempertahankan konteks sesi. Logout, reset password, perubahan password dan penonaktifan mencabut sesi sesuai operasinya.

Endpoint ganti password saat peninjauan memvalidasi 6–200 karakter, kecocokan konfirmasi, password saat ini, serta menolak penggunaan ulang password yang sama. Setelah berhasil, semua sesi dicabut dan Android kembali ke login. Ini berbeda dari dokumen PASSWORD_POLICY_CHANGE.md yang masih menyebut password sama boleh digunakan dan tidak ada batas maksimum; diagram mengikuti kode auth.schema.ts dan auth.service.ts.

API memeriksa akses sebelum menerbitkan URL file. Setelah diterbitkan, signed URL R2 adalah bearer URL yang dapat dipakai hingga kedaluwarsa (standar lima menit). R2 tidak mengulang pemeriksaan sesi/VARK pada setiap unduhan. Tautan eksternal juga berada di luar kontrol otorisasi EduVARK. Dengan demikian, pembatasan akses API tidak sama dengan pencabutan seketika semua URL yang sudah diterbitkan.

## Data dan konsistensi

Migrasi `001_init.sql` mendefinisikan 12 tabel domain: users, classes, enrollments, teaching_assignments, auth_sessions, topics, materials, feedback, case_studies, submissions, assessments, audit_logs. Runner migrasi membuat schema_migrations sebagai tabel teknis terpisah.

ERD keseluruhan menampilkan kunci dan relasi; dua ERD detail menampilkan seluruh kolom domain. PK dan FK mengikuti SQL, bukan asumsi dari nama atribut. `audit_logs.entity_id` adalah referensi polimorfik tanpa FK fisik sehingga tidak digambar sebagai FK. Tabel audit memiliki actor_id opsional. Role mahasiswa/dosen tetap berada di users, bukan tabel terpisah.

Database membatasi satu kelas aktif dan satu sesi aktif per pengguna melalui indeks unik parsial. Feedback unik per mahasiswa–topik–VARK dan diperbarui lewat upsert, tanpa riwayat nilai untuk kombinasi yang sama. Submission unik per mahasiswa–kasus; assessment unik per submission. Seorang dosen dapat membuat banyak assessment.

Satu topik memiliki maksimal satu kasus non-deleted melalui indeks unik parsial. Relasi FK fisik topic ke case_studies tetap 1:N bila memperhitungkan baris lama yang sudah ditandai terhapus; catatan pada ERD menjelaskan perbedaan dengan aturan satu kasus aktif.

## Kinesthetic dan penilaian

Tidak ada simpan draft server. Teks jawaban di-trim dan divalidasi 1–10.000 karakter. Backend menggunakan transaksi dan penguncian baris kasus untuk pemeriksaan pengiriman; PostgreSQL now() menjadi acuan perbandingan inklusif `now() <= deadline_at`. PostgreSQL now() mengacu pada awal transaksi, bukan waktu commit. Database juga menegakkan keunikan dan imutabilitas isi jawaban.

Status berasal dari deadline, submission dan assessment: BELUM_MENGUMPULKAN, TIDAK_MENGUMPULKAN, MENUNGGU_PENILAIAN, atau SUDAH_DINILAI. Melewati deadline tanpa submission tidak membuat nilai nol atau jawaban kosong. Deadline tidak dapat diubah setelah pengiriman pertama. Nilai integer 0–100 dan komentar opsional sampai 1.000 karakter disimpan satu kali; mahasiswa melihatnya saat memuat data berikutnya, tanpa publikasi terpisah atau notifikasi push yang diasumsikan.

## Materi dan analitik

File divalidasi sebelum disimpan ke R2, dengan batas maksimum konfigurasi 104.857.600 byte (100 MiB). Metadata dan audit disimpan di PostgreSQL. Penghapusan materi menandai metadata sebagai terhapus; kode delete materi tidak menghapus objek R2. Pemanggilan deleteObject pada alur upload merupakan kompensasi jika insert metadata gagal. Penyimpanan file lokal hanya tersedia untuk development ketika konfigurasi R2 kosong.

Feedback V/A/R adalah penilaian subjektif mahasiswa. Analitik menyajikan rerata, jumlah responden, histogram, filter kategori/mahasiswa, dan mahasiswa yang belum memberi feedback. Nilai Kinesthetic adalah metrik objektif terpisah. Tidak mengisi feedback tidak dihitung sebagai nol dan tidak ada penggabungan kedua jenis skor menjadi satu indikator.

## Cakupan dan keterbatasan

Activity memperlihatkan keputusan proses dan pembagian tanggung jawab; sequence memperlihatkan urutan interaksi Android, API, database dan storage. Percabangan alternatif mewakili kegagalan utama, bukan seluruh kemungkinan error jaringan. Use case menunjukkan hak aktor dan tujuan, tidak menggambar urutan operasi sebagai relasi include. Operasi internal seperti rotasi token dijelaskan di sequence.

Kesiapan produksi yang masih tercatat perlu dituntaskan meliputi E2E R2, uji beban, signing rilis, E2E perangkat ke API publik, dan backup serta uji restore. Paket ini tidak menyatakan pemeriksaan tersebut telah dilakukan.

## Sumber penelusuran

- `backend/src/db/migrations/001_init.sql`, `backend/src/db/migrate.ts`.
- `backend/src/modules/{auth,admin,topics,feedback,cases,assessments,analytics}`: routes, schema, service, repository.
- `backend/src/middleware/auth.ts`, `backend/src/lib/storage.ts`, `backend/src/lib/time.ts`.
- `app/build.gradle.kts`, navigasi dan layar pada `app/src/main/java/com/eduvark/app`.
- `README.md`, `DEPLOY-PROXMOX.md`, `docker-compose.homelab.yml`, `docs/DECISIONS.md` dan laporan implementasi.

Notasi digambar dengan PlantUML, merujuk dokumentasi resmi https://plantuml.com/ie-diagram, https://plantuml.com/activity-diagram-beta, dan https://plantuml.com/sequence-diagram.
