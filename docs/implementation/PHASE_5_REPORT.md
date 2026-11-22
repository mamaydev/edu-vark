# Phase 5 Report — Studi Kasus Kinesthetic, Submission & Deadline

**Tanggal:** 20 November 2026  
**Status:** Selesai secara fungsional dan terverifikasi.  
**Lingkup:** FR-023–FR-025, FR-038–FR-041, FR-045–FR-050; BR-10, BR-11, BR-14, BR-16–BR-19; D-08, D-09.

## 1. Gate Fase 5

| Gate | Target | Hasil |
|---|---|---|
| Case 1:1 per topik | Upsert tidak membuat duplikat | **Lulus** (test) |
| Case belum ada | Pesan persis “studi kasus belum tersedia” | **Lulus** (test) |
| Khusus Kinesthetic | Sesi lain 403 | **Lulus** (test) |
| Submission sekali | Kiriman kedua 409 | **Lulus** (test) |
| Panjang 1–10.000 | Kosong/whitespace/10.001 ditolak | **Lulus** (test) |
| Deadline lampau | 409, tidak ada submission | **Lulus** (test) |
| Deadline lock | Ubah deadline setelah submit 409 | **Lulus** (test) |
| Status turunan | Belum/Tidak/Menunggu | **Lulus** (test) |
| Tanpa submission palsu | Tidak Mengumpulkan tidak membuat nilai 0 | **Lulus** (test) |
| Backend test | Semua hijau | **Lulus; 58 test** |
| Android build/test/lint | Semua hijau | **Lulus; 19 unit test** |

## 2. Backend

### 2.1 Endpoint

```text
GET  /api/v1/topics/:id/case                 (mahasiswa Kinesthetic)
POST /api/v1/topics/:id/case/submission      (mahasiswa Kinesthetic)
GET  /api/v1/lecturer/topics/:id/case        (dosen)
PUT  /api/v1/lecturer/topics/:id/case        (dosen)
```

### 2.2 Implementasi

- Satu studi kasus per topik (partial unique `topic_id`).
- Teori opsional, instruksi wajib, deadline UTC + tampilan WIB.
- Deadline dikunci setelah submission pertama; trigger DB + pengecekan service.
- Submission:
  - hanya sesi Kinesthetic;
  - teks 1–10.000 karakter (setelah trim);
  - tidak ada draft/endpoint simpan sementara;
  - sekali kirim; unique `(student_id, case_study_id)`;
  - validasi deadline memakai `now()` database di dalam transaksi dengan `FOR UPDATE`;
  - `server_now <= deadline_at` diterima; lebih besar ditolak.
- Status:
  - tidak ada submission + sebelum deadline → `BELUM_MENGUMPULKAN`;
  - tidak ada submission + setelah deadline → `TIDAK_MENGUMPULKAN` (tanpa submission/nilai 0);
  - submission tanpa assessment → `MENUNGGU_PENILAIAN`;
  - assessment ada → `SUDAH_DINILAI` (siap Fase 6).
- Pesan case kosong: **“studi kasus belum tersedia”**.
- Audit: case dibuat/diubah, submission dibuat.
- RBAC: mahasiswa Kinesthetic; dosen pemilik kelas; admin ditolak.

### 2.3 Bukti

```text
npm run typecheck -> lulus
npm run lint      -> lulus
npm test          -> 10 files, 58 tests passed
  - cases integration: 8 tests
    create/update 1:1, missing case message, non-Kinesthetic 403,
    submit sekali + second 409, empty/whitespace/10001 422,
    deadline lampau 409 tanpa row, deadline lock 409,
    VARK 428 + dosen 403
```

## 3. Android

### 3.1 Implementasi

- Mahasiswa:
  - topik Kinesthetic diarahkan ke layar studi kasus (bukan materi);
  - teori, instruksi, deadline WIB, status;
  - text editor tanpa autosave server;
  - konfirmasi final sebelum kirim;
  - status Belum/Tidak/Menunggu/Sudah Dinilai.
- Dosen:
  - layar studi kasus per topik;
  - teori, instruksi, deadline ISO UTC;
  - deadline dinonaktifkan jika sudah ada submission;
  - menampilkan deadline WIB tersimpan.
- Repository/use case/ViewModel terpisah.

### 3.2 Bukti

```text
./gradlew :app:assembleDebug :app:testDebugUnitTest -> BUILD SUCCESSFUL
  Android unit tests: 19
    - StudentCaseViewModelTest 2
    - LecturerCaseViewModelTest 1
    - sebelumnya 16

./gradlew :app:lintDebug -> BUILD SUCCESSFUL
```

## 4. Catatan dan batasan

- Belum ada emulator/device run; UI editor dan submission belum diuji end-to-end di perangkat.
- Deadline tepat pada batas dikodekan dengan `now() <= deadline_at`; pengujian otomatis memakai deadline lampau dan masa depan, bukan presisi milidetik.
- Penilaian dosen dan status `SUDAH_DINILAI` baru diimplementasikan Fase 6.
- Teks disimpan setelah trim; spasi di awal/akhir tidak dipertahankan.
- SRS asli tetap tidak berubah.

## 5. Langkah berikutnya

Fase 6 — penilaian dosen:

- daftar submission + status;
- nilai integer 0–100 + komentar opsional ≤1.000;
- satu assessment per submission;
- final/immutable;
- mahasiswa langsung melihat hasil.
