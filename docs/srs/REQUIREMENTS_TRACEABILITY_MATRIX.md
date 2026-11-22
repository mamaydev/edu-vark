# Requirements Traceability Matrix — EduVARK v0.7

**Sumber:** `SRS_EduVARK_v0.7` + keputusan wawancara 20 November 2026 (D-01–D-18).  
**Cakupan:** FR-001–FR-050, BR-01–BR-19, UC-M01–M07 / UC-D01–D06 / UC-A01–A03, AT-01–AT-17.  
**Status:** Dokumen analisis; bukan implementasi.

## Legenda

| Status | Arti |
|---|---|
| **COVERED** | FR memiliki BR (jika relevan), UC, activity diagram, entitas, dan AT yang memadai. |
| **PARTIAL** | FR memiliki UC/entitas, tetapi AT atau activity belum lengkap. |
| **GAP** | FR belum memiliki pasangan AT/UC/activity yang memadai; dicatat di §GAP. |

Activity diagram:
- `AD-M01` Login & pilih VARK; `AD-M02` Akses materi; `AD-M03` Feedback; `AD-M04` Submit Kinesthetic; `AD-M05` Lihat hasil.
- `AD-D01` Kelola/terbitkan materi; `AD-D02` Buat studi kasus & deadline; `AD-D03` Penilaian final; `AD-D04` Grafik; `AD-D05` Periksa pengumpulan.
- `AD-A01` Buat akun; `AD-A02` Lihat/ubah akun; `AD-A03` Nonaktifkan akun.

## Matriks utama

| FR | Requirement (ringkas) | BR | UC | Activity | Entitas | AT | Status / catatan |
|---|---|---|---|---|---|---|---|
| FR-001 | Login dan logout untuk ketiga role | BR-02 | UC-M01, UC-M07 (semua role; D-18) | AD-M01 | USER, AUTH_SESSION | AT-03 (parsial) | **PARTIAL** — UC sudah mencakup staf; AT-P11 belum ada |
| FR-002 | Tolak kredensial tidak valid dan akun nonaktif | — | UC-M01 (alt) | AD-M01 | USER, AUTH_SESSION | — | **GAP** — AT-P01 |
| FR-003 | Tolak endpoint/halaman yang tidak diizinkan role | BR-06 | Semua UC | Semua AD role | USER.role | AT-10 | **PARTIAL** — baru menguji admin |
| FR-004 | Navigasi sesuai role; menu tersembunyi bukan kontrol izin | BR-06 | Semua UC | Semua AD role | USER.role | — | **GAP** — AT-P02 |
| FR-005 | Tampilkan empat pilihan VARK setelah login | BR-02 | UC-M01 | AD-M01 | AUTH_SESSION.vark_type | AT-03 | **COVERED** |
| FR-006 | Tepat satu VARK aktif per sesi, dikunci sampai sesi berakhir | BR-02 | UC-M01, UC-M07 | AD-M01 | AUTH_SESSION | AT-02 | **COVERED** |
| FR-007 | Wajib pilih VARK sebelum akses materi setiap login | BR-02, BR-03 | UC-M01, UC-M02, UC-M03 | AD-M01, AD-M02 | AUTH_SESSION | AT-02, AT-03 | **PARTIAL** — uji 428 belum ada |
| FR-008 | Tolak VARK invalid/pergantian dalam sesi, termasuk API | BR-02 | UC-M01, UC-M07 | AD-M01 | AUTH_SESSION | AT-02 | **COVERED** |
| FR-009 | Dosen CRUD topik IBD | BR-07 | UC-D01 | AD-D01 | TOPIC | — | **GAP** — AT-P03 |
| FR-010 | Unggah satu/lebih materi per kategori pada topik | BR-07 | UC-D02 | AD-D01 | MATERIAL | AT-04 | **PARTIAL** — metadata/validasi belum diuji |
| FR-011 | Catat kategori, judul, tipe, ukuran, tanggal unggah | — | UC-D02 | AD-D01 | MATERIAL | — | **GAP** — AT-P04 |
| FR-012 | Tolak file melebihi batas/tipe tidak diizinkan | — | UC-D02 | AD-D01 | MATERIAL | — | **GAP** — AT-P04 |
| FR-013 | Terbitkan topik meski materi belum lengkap | BR-07 | UC-D01, UC-D02 | AD-D01 | TOPIC.status | AT-04 | **COVERED** |
| FR-014 | Tampilkan hanya materi kategori VARK sesi | BR-03 | UC-M02, UC-M03 | AD-M02 | MATERIAL, AUTH_SESSION, ENROLLMENT | AT-01 | **COVERED** |
| FR-015 | Tolak akses langsung file/URL/API kategori lain | BR-03 | UC-M03 | AD-M02 | MATERIAL, AUTH_SESSION | AT-01 | **COVERED** |
| FR-016 | Pesan persis jika materi kategori aktif belum ada | BR-08 | UC-M03 | AD-M02 | MATERIAL, TOPIC | AT-04 | **COVERED** |
| FR-017 | Jaga urutan topik sesuai dosen | — | UC-M02, UC-D01 | AD-M02, AD-D01 | TOPIC.sort_order | — | **GAP** — AT-P03 |
| FR-018 | Slider integer 0–100 hanya V/A/R pada topik bermateri | BR-04 | UC-M04 | AD-M03 | FEEDBACK | AT-05 | **COVERED** |
| FR-019 | Satu feedback terbaru per mahasiswa–topik–kategori; unik | BR-04 | UC-M04 | AD-M03 | FEEDBACK | AT-05 | **COVERED** |
| FR-020 | Kirim ulang memperbarui nilai tanpa record riwayat | BR-04 | UC-M04 | AD-M03 | FEEDBACK | AT-05, AT-06 | **COVERED** |
| FR-021 | Tampilkan nilai terakhir; kategori lain tetap tersimpan | BR-09 | UC-M04, UC-M06 | AD-M03 | FEEDBACK | AT-05, AT-06 | **COVERED** |
| FR-022 | Tolak input di luar 0–100, sesi Kinesthetic, feedback orang lain | BR-04 | UC-M04 | AD-M03 | FEEDBACK, AUTH_SESSION | AT-05 (parsial) | **PARTIAL** — AT-P05 |
| FR-023 | Dosen menambahkan teori, instruksi, deadline pada topik Kinesthetic | BR-11 | UC-D03 | AD-D02 | CASE_STUDY | AT-09, AT-16 (parsial) | **PARTIAL** — AT-P06 |
| FR-024 | Jawaban lewat text editor, tanpa upload, tanpa draft server | BR-16 | UC-M05 | AD-M04 | SUBMISSION (final saja) | AT-13 | **COVERED** |
| FR-025 | Satu pengiriman final per mahasiswa–studi kasus | BR-10 | UC-M05 | AD-M04 | SUBMISSION | AT-07, AT-14 | **COVERED** |
| FR-026 | Dosen memberi nilai 0–100 dan komentar manual | BR-12 | UC-D05 | AD-D03 | ASSESSMENT | AT-08, AT-11 | **COVERED** |
| FR-027 | Mahasiswa melihat nilai/komentar segera setelah disimpan | BR-12 | UC-D05, UC-M06 | AD-D03, AD-M05 | ASSESSMENT, SUBMISSION | AT-08 | **COVERED** |
| FR-028 | Batasi akses jawaban/penilaian ke pemilik dan dosen berwenang | BR-05, BR-13 | UC-M06, UC-D04, UC-D05 | AD-M05, AD-D05, AD-D03 | SUBMISSION, ASSESSMENT, ENROLLMENT | — | **GAP** — AT-P07 |
| FR-029 | Grafik feedback terbaru per topik dan kategori V/A/R | BR-04 | UC-D06 | AD-D04 | FEEDBACK | — | **GAP** — AT-P08 |
| FR-030 | Feedback per mahasiswa dan filter kategori asal | BR-04 | UC-D06 | AD-D04 | FEEDBACK | — | **GAP** — AT-P08 |
| FR-031 | Bedakan belum feedback dari nilai 0% | BR-04 | UC-D06 | AD-D04 | FEEDBACK | — | **GAP** — AT-P08 |
| FR-032 | Tampilkan jumlah responden dan definisi agregasi | BR-04 | UC-D06 | AD-D04 | FEEDBACK | — | **GAP** — AT-P08 |
| FR-033 | Jangan gabungkan rerata subjektif dengan nilai Kinesthetic | BR-05 | UC-D06 | AD-D04 | FEEDBACK, ASSESSMENT | — | **GAP** — AT-P08 |
| FR-034 | Admin membuat/melihat/mengubah/menonaktifkan akun | BR-06 | UC-A01, UC-A02, UC-A03 | AD-A01, AD-A02, AD-A03 | USER | AT-10 (parsial) | **PARTIAL** — AT-P09 |
| FR-035 | Role hanya mahasiswa, dosen, admin | BR-06 | UC-A01, UC-A02 | AD-A01, AD-A02 | USER.role | — | **GAP** — AT-P09 |
| FR-036 | Cegah duplikasi identitas login unik | — | UC-A01, UC-A02 | AD-A01, AD-A02 | USER.login_id | — | **GAP** — AT-P09 |
| FR-037 | Admin tidak boleh mengubah feedback/materi/nilai | BR-06 | UC-A02 (alt); UC-D/UC-M bukan admin | AD-A02 | USER.role | AT-10 | **COVERED** |
| FR-038 | Tolak jawaban kosong dan pengiriman kedua | BR-10, BR-17 | UC-M05 | AD-M04 | SUBMISSION | AT-07, AT-15 | **COVERED** |
| FR-039 | Setelah terkirim, tolak semua perubahan isi jawaban | BR-10, BR-13 | UC-M05, UC-D05 | AD-M04, AD-D03 | SUBMISSION | AT-07, AT-12 | **COVERED** |
| FR-040 | Tampilkan deadline jelas; tolak otomatis setelah lewat | BR-11, BR-14 | UC-D03, UC-M05 | AD-D02, AD-M04 | CASE_STUDY.deadline_at | AT-09, AT-16 | **COVERED** |
| FR-041 | Pertahankan jawaban final saat penilaian berhasil/gagal | BR-13 | UC-D05 | AD-D03 | SUBMISSION, ASSESSMENT | AT-08 (parsial) | **PARTIAL** — AT-P10 |
| FR-042 | Bedakan belum mengumpulkan, terkumpul/belum dinilai, sudah dinilai | BR-12, BR-18 | UC-M06, UC-D04 | AD-M05, AD-D05 | SUBMISSION, ASSESSMENT, CASE_STUDY | AT-07, AT-08, AT-17 | **COVERED** |
| FR-043 | Tolak nilai di luar 0–100 dan komentar tidak valid | BR-12 | UC-D05 | AD-D03 | ASSESSMENT | AT-11 | **COVERED** |
| FR-044 | Tolak perubahan/penghapusan nilai setelah tersimpan | BR-13 | UC-D05 | AD-D03 | ASSESSMENT | AT-12 | **COVERED** |
| FR-045 | Tidak ada draft/autosave jawaban Kinesthetic | BR-16 | UC-M05 | AD-M04 | SUBMISSION | AT-13 | **COVERED** |
| FR-046 | Validasi deadline dengan waktu server saat pengiriman atomik | BR-14, BR-19 | UC-M05 | AD-M04 | SUBMISSION, CASE_STUDY | AT-09, AT-16 | **COVERED** |
| FR-047 | Terima panjang 1–10.000 karakter, tolak di luar itu | BR-17 | UC-M05 | AD-M04 | SUBMISSION | AT-15 | **COVERED** |
| FR-048 | Status Tidak Mengumpulkan tanpa nilai 0/submission palsu | BR-18 | UC-M06, UC-D04 | AD-M05, AD-D05 | CASE_STUDY, SUBMISSION | AT-17 | **COVERED** |
| FR-049 | Terima submission tepat deadline; tolak jika server_now > deadline | BR-19 | UC-M05 | AD-M04 | CASE_STUDY, SUBMISSION | AT-16 | **COVERED** |
| FR-050 | Backend memeriksa panjang, unique, VARK, deadline setiap Kirim | BR-14, BR-17, BR-19 | UC-M05 | AD-M04 | SUBMISSION, AUTH_SESSION, CASE_STUDY | AT-09, AT-14, AT-15, AT-16 | **COVERED** |

## Ringkasan

| Status | Jumlah | FR |
|---|---:|---|
| COVERED | 28 | FR-005, 006, 008, 013, 014, 015, 016, 018, 019, 020, 021, 024, 025, 026, 027, 037, 038, 039, 040, 042, 043, 044, 045, 046, 047, 048, 049, 050 |
| PARTIAL | 8 | FR-001, 003, 007, 010, 022, 023, 034, 041 |
| GAP | 14 | FR-002, 004, 009, 011, 012, 017, 028, 029, 030, 031, 032, 033, 035, 036 |
| **Total** | **50** | |

## GAP register

| GAP | FR | Kekosongan | Usulan |
|---|---|---|---|
| GAP-01 | FR-001, FR-002, FR-004 | Tidak ada UC/AT khusus login/logout staf dan navigasi role | AT-P01, AT-P02, AT-P11; keputusan OQ-15 |
| GAP-02 | FR-009, FR-011, FR-012, FR-017 | Tidak ada AT untuk CRUD topik, metadata upload, validasi file, urutan | AT-P03, AT-P04 |
| GAP-03 | FR-022 | Tidak ada AT untuk input invalid, sesi Kinesthetic, feedback orang lain | AT-P05 |
| GAP-04 | FR-023 | Tidak ada AT pembuatan studi kasus/deadline | AT-P06 |
| GAP-05 | FR-028 | Tidak ada AT akses submission lintas pengguna | AT-P07 |
| GAP-06 | FR-029–FR-033 | Tidak ada AT analitik | AT-P08 |
| GAP-07 | FR-034–FR-036 | Tidak ada AT CRUD akun/role/unique login | AT-P09 |
| GAP-08 | FR-041 | Tidak ada AT kegagalan penilaian yang mempertahankan jawaban | AT-P10 |

## Cakupan BR

| BR | FR yang menopang | Status |
|---|---|---|
| BR-01 | FR-009, FR-014, FR-034 | Tercakup |
| BR-02 | FR-005–FR-008 | Tercakup |
| BR-03 | FR-014–FR-016 | Tercakup |
| BR-04 | FR-018–FR-022, FR-029–FR-033 | Tercakup |
| BR-05 | FR-026–FR-028, FR-033 | Tercakup |
| BR-06 | FR-003, FR-034–FR-037 | Tercakup |
| BR-07 | FR-009–FR-013 | Tercakup |
| BR-08 | FR-016 | Tercakup |
| BR-09 | FR-021 | Tercakup |
| BR-10 | FR-025, FR-038–FR-039 | Tercakup |
| BR-11 | FR-023, FR-040 | Tercakup |
| BR-12 | FR-026–FR-027, FR-042–FR-043 | Tercakup |
| BR-13 | FR-028, FR-039, FR-041, FR-044 | Tercakup |
| BR-14 | FR-040, FR-046, FR-050 | Tercakup |
| BR-15 | FR-014, FR-034; D-13 | **TBD mekanisme** (OQ-07) |
| BR-16 | FR-024, FR-045 | Tercakup |
| BR-17 | FR-038, FR-047, FR-050 | Tercakup |
| BR-18 | FR-042, FR-048 | Tercakup |
| BR-19 | FR-046, FR-049, FR-050 | Tercakup |

## Cek silang use case

| UC | FR | Status |
|---|---|---|
| UC-M01 | FR-001–008 | Tercakup |
| UC-M02 | FR-014, FR-017 | Tercakup |
| UC-M03 | FR-014–017 | Tercakup |
| UC-M04 | FR-018–022 | Tercakup |
| UC-M05 | FR-023–025, 038–041, 045–050 | Tercakup |
| UC-M06 | FR-027–028, 042, 048 | Tercakup |
| UC-M07 | FR-001, 006–008 | Tercakup |
| UC-D01 | FR-009, 013, 017 | Tercakup |
| UC-D02 | FR-010–016 | Tercakup |
| UC-D03 | FR-023, 040 | Tercakup |
| UC-D04 | FR-028, 042, 048 | Tercakup |
| UC-D05 | FR-026–028, 043–044 | Tercakup |
| UC-D06 | FR-029–033 | Tercakup |
| UC-A01 | FR-034–036 | Tercakup |
| UC-A02 | FR-034–037 | Tercakup |
| UC-A03 | FR-034 | Tercakup |

## Cek silang entitas

| Entitas | FR | Status |
|---|---|---|
| USER | FR-001–008, 026–028, 034–037, 044 | Tercakup |
| CLASS | FR-001, 009, 014, 034 | Tercakup |
| ENROLLMENT | FR-014, 028, 034 | Tercakup |
| TEACHING_ASSIGNMENT | FR-003, 009–013, 026–028 | Tercakup |
| AUTH_SESSION | FR-001–008, 014, 022, 050 | Tercakup |
| TOPIC | FR-009–017, 023, 029–033 | Tercakup |
| MATERIAL | FR-010–016, 018 | Tercakup |
| FEEDBACK | FR-018–022, 029–033 | Tercakup |
| CASE_STUDY | FR-023–025, 040, 042–050 | Tercakup |
| SUBMISSION | FR-025, 038–042, 044, 048–050 | Tercakup |
| ASSESSMENT | FR-026–028, 033, 042–044 | Tercakup |
| AUDIT_LOG | D-13, mendukung FR-044 | Pendukung |

> **Catatan:** Tidak ada FR yang dihapus atau diturunkan prioritasnya. GAP berarti dokumentasi/pengujian belum lengkap, bukan requirement tidak berlaku.
