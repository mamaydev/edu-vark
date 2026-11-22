# Phase 7 Report — Analitik Dosen

**Tanggal:** 20 November 2026  
**Status:** Selesai secara fungsional dan terverifikasi.  
**Lingkup:** FR-029–FR-033; BR-04, BR-05; D-12.

## 1. Gate Fase 7

| Gate | Target | Hasil |
|---|---|---|
| Agregasi feedback | Mean + n + histogram per topik/kategori | **Lulus** (test) |
| Belum feedback | Dihitung terpisah, bukan 0% | **Lulus** (test; mean null, n=0) |
| Filter kategori | VISUAL/AURAL/READ_WRITE | **Lulus** (test) |
| Filter mahasiswa | Feedback mahasiswa tertentu | **Lulus** (test) |
| Definisi agregasi | Ditampilkan di response | **Lulus** (mean of latest feedback per student-topic-category) |
| Kinesthetic terpisah | Tidak dicampur rerata subjektif | **Lulus** (test; 80 ≠ 60) |
| Otorisasi | Dosen kelas lain / VARK mismatch | **Lulus** (test) |
| Backend test | Semua hijau | **Lulus; 70 test** |
| Android build/test/lint | Semua hijau | **Lulus; 22 unit test** |

## 2. Backend

### 2.1 Endpoint

```text
GET /api/v1/lecturer/analytics/topics/:id
    ?category=VISUAL|AURAL|READ_WRITE
    &studentId=<uuid>
```

### 2.2 Implementasi

- Agregasi server-side feedback terbaru per mahasiswa–topik–kategori (tabel unik).
- Response:
  - `category`, `studentId` (filter aktif);
  - `respondentCount`, `mean`, `histogram` 5 bucket;
  - `notRespondedCount` terpisah;
  - `aggregation`;
  - `students[]` berisi feedback V/A/R per mahasiswa;
  - `kinesthetic { assessedCount, meanScore, notSubmittedCount }`.
- `mean` null + n=0 jika belum ada feedback; tidak dihitung 0%.
- Filter kategori dan mahasiswa; VARK mismatch → 403.
- Dosen kelas lain → 403 `CLASS_FORBIDDEN`.
- Tanpa filter kategori: agregasi semua V/A/R; Kinesthetic tetap terpisah.
- Query server-side; klien tidak menghitung mean/histogram.

### 2.3 Bukti

```text
npm run typecheck -> lulus
npm run lint      -> lulus
npm test          -> 12 files, 70 tests passed
  - analytics integration: 6 tests
    VISUAL n=2 mean=50 histogram + notResponded=1,
    Kinesthetic assessed=1 meanScore=80 terpisah,
    semua V/A/R n=3 mean=60,
    filter mahasiswa/category, mahasiswa tanpa feedback mean=null,
    VARK mismatch/kelas lain/invalid category
```

## 3. Android

### 3.1 Implementasi

- Tombol **Analitik** pada kartu topik dosen.
- Layar analitik:
  - filter kategori: Semua/Visual/Audio/Baca-Tulis;
  - filter mahasiswa (dropdown);
  - kartu rata-rata + n + belum feedback + definisi agregasi;
  - histogram 5 bucket dengan bar;
  - daftar feedback per mahasiswa;
  - kartu **Kinesthetic** terpisah (dinilai, rata-rata, belum submit);
  - empty state “Belum ada feedback (n=0), bukan 0%.”
- Repository/use case/ViewModel terpisah; query server-side.

### 3.2 Bukti

```text
./gradlew :app:assembleDebug :app:testDebugUnitTest -> BUILD SUCCESSFUL
  Android unit tests: 22
    - LecturerAnalyticsViewModelTest 1
    - sebelumnya 21

./gradlew :app:lintDebug -> BUILD SUCCESSFUL
```

## 4. Catatan dan batasan

- Belum ada emulator/device run; grafik dan filter belum diuji end-to-end di perangkat.
- Histogram memakai bar sederhana, bukan library chart.
- Analitik hanya menghitung mahasiswa dengan sesi aktif sesuai VARK; mahasiswa tanpa sesi aktif tidak masuk denominator.
- Belum ada ekspor PDF/Excel/CSV (OQ-06).
- SRS asli tetap tidak berubah.

## 5. Langkah berikutnya

Fase 8 — hardening dan acceptance:

- audit menyeluruh gate AT-P01–AT-P13;
- uji perangkat/emulator;
- perbaikan bug, performa, dan keamanan;
- dokumentasi acceptance akhir.
