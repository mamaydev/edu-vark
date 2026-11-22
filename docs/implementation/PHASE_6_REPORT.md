# Phase 6 Report — Penilaian Dosen & Hasil Final

**Tanggal:** 20 November 2026  
**Status:** Selesai secara fungsional dan terverifikasi.  
**Lingkup:** FR-026–FR-028, FR-042–FR-044, FR-045–FR-050; BR-12, BR-13, BR-15, BR-17, BR-19; D-10, D-11.

## 1. Gate Fase 6

| Gate | Target | Hasil |
|---|---|---|
| Daftar submission per case | Semua mahasiswa Kinesthetic + status | **Lulus** (test) |
| Status daftar | Belum/Tidak/Menunggu/Sudah | **Lulus** (test) |
| Detail submission | Teks, waktu, status, nilai/komentar | **Lulus** (test) |
| Nilai 0–100 | Di luar rentang 422 | **Lulus** (test) |
| Komentar ≤1.000 | 1.001 ditolak | **Lulus** (test) |
| Satu assessment | Kiriman kedua 409 | **Lulus** (test) |
| Final/immutable | Tidak ada update/delete | **Lulus** (trigger + no endpoint) |
| Mahasiswa lihat hasil | Case menjadi SUDAH_DINILAI + nilai | **Lulus** (test) |
| Otorisasi | Dosen kelas, pemilik submission | **Lulus** (test) |
| Backend test | Semua hijau | **Lulus; 64 test** |
| Android build/test/lint | Semua hijau | **Lulus; 21 unit test** |

## 2. Backend

### 2.1 Endpoint

```text
GET  /api/v1/lecturer/cases/:id/submissions
GET  /api/v1/submissions/:id
POST /api/v1/submissions/:id/assessment
```

### 2.2 Implementasi

- Daftar submission:
  - semua mahasiswa **Kinesthetic** aktif di kelas;
  - status diturunkan: belum/tidak mengumpulkan, menunggu, sudah dinilai;
  - `submissionId` null jika belum mengumpulkan;
  - skor null untuk yang belum dinilai.
- Detail submission: teks jawaban, waktu, status, nilai/komentar.
- Assessment:
  - nilai integer 0–100; komentar opsional ≤1.000 karakter;
  - satu assessment per submission (`UNIQUE(submission_id)`);
  - `201` final; duplikat `409 ALREADY_ASSESSED`;
  - tanpa endpoint update/delete; trigger DB menolak update/delete;
  - audit `ASSESSMENT_CREATED`.
- Otorisasi:
  - mahasiswa hanya submission sendiri (`403 SUBMISSION_FORBIDDEN`);
  - dosen hanya kelas yang diampu (`403 CLASS_FORBIDDEN`);
  - admin tidak dapat mengakses submission/penilaian.
- Mahasiswa melihat hasil melalui `GET /topics/:id/case`: status `SUDAH_DINILAI`, nilai, komentar.
- Tidak ada nilai 0 palsu untuk `TIDAK_MENGUMPULKAN`.

### 2.3 Bukti

```text
npm run typecheck -> lulus
npm run lint      -> lulus
npm test          -> 11 files, 64 tests passed
  - assessments integration: 6 tests
    daftar Kinesthetic + status, nilai 85 + hasil mahasiswa,
    duplikat 409 + count=1, nilai 101/komentar 1001 422,
    dosen lain/mahasiswa lain 403, TIDAK_MENGUMPULKAN tanpa row/nilai
```

## 3. Android

### 3.1 Implementasi

- Dosen:
  - tombol **Lihat Submission** pada layar studi kasus;
  - daftar mahasiswa Kinesthetic + status + nilai;
  - buka detail jawaban;
  - form nilai 0–100 + komentar;
  - setelah dinilai, tampilan final read-only.
- Mahasiswa:
  - layar studi kasus menampilkan status `SUDAH_DINILAI`, nilai, dan komentar.
- Repository/use case/ViewModel terpisah.

### 3.2 Bukti

```text
./gradlew :app:assembleDebug :app:testDebugUnitTest -> BUILD SUCCESSFUL
  Android unit tests: 21
    - LecturerSubmissionsViewModelTest 1
    - LecturerGradeViewModelTest 1
    - sebelumnya 19

./gradlew :app:lintDebug -> BUILD SUCCESSFUL
```

## 4. Catatan dan batasan

- Belum ada emulator/device run; daftar dan form nilai belum diuji end-to-end di perangkat.
- Daftar submission hanya menampilkan mahasiswa dengan sesi aktif **Kinesthetic**; mahasiswa tanpa sesi aktif tidak muncul.
- Tidak ada fitur edit nilai; sesuai aturan final/immutable.
- Analitik dosen (FR-029–FR-033) belum diimplementasikan.
- SRS asli tetap tidak berubah.

## 5. Langkah berikutnya

Fase 7 — analitik dosen:

- agregasi feedback V/A/R per topik;
- rata-rata, histogram, jumlah belum merespons;
- ringkasan Kinesthetic (jumlah dinilai, rata-rata);
- UI grafik/daftar.
