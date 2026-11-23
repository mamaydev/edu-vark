# Diagram sistem EduVARK aktual

Hasil peninjauan kode dan rancangan per 23 November 2026. Buka folder `png` untuk gambar siap pakai. Folder `svg` berisi gambar vektor untuk memperbesar tanpa kehilangan ketajaman. Folder `source` berisi PlantUML yang dapat diedit. Penjelasan dan temuan ada di ANALISIS-SISTEM.md.

| Jenis | Nama berkas tanpa ekstensi | Cakupan |
|---|---|---|
| ERD | erd-keseluruhan | Seluruh tabel dan relasi FK; ringkasan kunci |
| ERD | erd-identitas | Kolom lengkap akun, kelas, enrollment, penugasan, sesi, audit |
| ERD | erd-akademik | Kolom lengkap topik, materi, feedback, kasus, submission, assessment |
| Use case | usecase-keseluruhan | Batas sistem dan seluruh aktor |
| Use case | usecase-mahasiswa | Tujuan dan hak mahasiswa |
| Use case | usecase-dosen | Tujuan dan hak dosen |
| Use case | usecase-admin | Tujuan dan hak admin |
| Activity | activity-login | Login, ganti password wajib, VARK, logout |
| Activity | activity-mahasiswa | Materi, feedback, pengiriman dan status Kinesthetic |
| Activity | activity-dosen | Topik/materi, kasus, penilaian, analitik |
| Activity | activity-admin | Akun, password, nonaktif dan enrollment |
| Sequence | sequence-01-auth-sesi | Login, password, VARK, refresh, logout |
| Sequence | sequence-02-materi-r2 | Upload file/tautan, metadata, akses R2 dan soft-delete |
| Sequence | sequence-03-feedback-analitik | Feedback upsert dan agregasi dosen |
| Sequence | sequence-04-submission-kinesthetic | Validasi, transaksi, deadline dan pengiriman final |
| Sequence | sequence-05-penilaian-final | Assessment final dan akses hasil mahasiswa |
| Sequence | sequence-06-admin-akun | Pembuatan akun, reset, nonaktif dan status enrollment |

Activity yang memuat banyak pilihan operasi berbentuk lebar; gunakan berkas resolusi penuh atau SVG dan perbesar saat membaca. Use case per aktor lebih cocok untuk laporan dibanding diagram keseluruhan yang tinggi. Diagram menampilkan alur aktual, bukan bukti bahwa semua pengujian atau deployment sudah berhasil.

## Render ulang

Pasang Java dan unduh PlantUML dari https://plantuml.com/download. Dari folder ini, jalankan (ganti path JAR):

```sh
PLANTUML_LIMIT_SIZE=18000 java -Xmx2g -Djava.awt.headless=true -jar /path/plantuml.jar -tpng -o ../png source/*.puml
PLANTUML_LIMIT_SIZE=18000 java -Xmx2g -Djava.awt.headless=true -jar /path/plantuml.jar -tsvg -o ../svg source/*.puml
```

Semua proses render paket ini berlangsung lokal; tidak mengirim kode proyek ke layanan render diagram publik.
