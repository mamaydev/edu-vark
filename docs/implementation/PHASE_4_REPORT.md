# Phase 4 Report — Feedback Pemahaman V/A/R

**Tanggal:** 20 November 2026  
**Status:** Selesai secara fungsional dan terverifikasi.  
**Lingkup:** FR-018–FR-022; BR-04, BR-09; D-14.

## 1. Gate Fase 4

| Gate | Target | Hasil |
|---|---|---|
| Feedback hanya V/A/R | Kinesthetic ditolak | **Lulus** (test) |
| Slider 0–100 | Nilai integer; di luar rentang ditolak | **Lulus** (test) |
| Satu feedback terbaru | Upsert unik triple | **Lulus** (test) |
| Tanpa riwayat | Simpan ulang = update baris | **Lulus** (test; count=1) |
| Kategori terpisah | Visual dan Audio punya baris sendiri | **Lulus** (test; count=2) |
| Belum feedback ≠ 0% | GET awal = null | **Lulus** (test) |
| Materi tersedia | Tanpa materi → 404 pesan persis | **Lulus** (test) |
| VARK gate | Tanpa VARK → 428 | **Lulus** (test) |
| RBAC | Dosen tidak bisa mengisi feedback | **Lulus** (test) |
| Backend test | Semua hijau | **Lulus; 50 test** |
| Android build/test/lint | Semua hijau | **Lulus; 16 unit test** |

## 2. Backend

### 2.1 Endpoint

```text
GET /api/v1/topics/:id/feedback
PUT /api/v1/topics/:id/feedback   { "value": 75 }
```

### 2.2 Implementasi

- Feedback hanya untuk sesi **VISUAL/AURAL/READ_WRITE**; Kinesthetic 403.
- Nilai integer 0–100; Zod + `CHECK`.
- `UNIQUE(student_id, topic_id, vark_type)` + `ON CONFLICT DO UPDATE`.
- Kategori asal disimpan di `feedback.vark_type`; tidak berubah saat ganti VARK.
- Membaca feedback terbaru; `value: null` jika belum ada (bukan 0).
- Hanya jika topik terbit, mahasiswa terdaftar, dan materi kategori tersedia.
- Pesan materi kosong persis FR-016.
- Audit `FEEDBACK_SAVED`.
- `requireVark` + `requireRole('STUDENT')` + `requirePasswordChanged`.

### 2.3 Bukti

```text
npm run typecheck -> lulus
npm run lint      -> lulus
npm test          -> 9 files, 50 tests passed
  - feedback integration: 6 tests
    null awal, upsert 40→75 (1 baris), Visual 75 + Audio 60 (2 baris),
    Kinesthetic 403, nilai -1/101 422, materi kosong 404 pesan persis,
    VARK 428, dosen 403
```

## 3. Android

### 3.1 Implementasi

- DTO/API `GET` dan `PUT` feedback.
- `FeedbackRepository` + use case.
- `StudentMaterialsViewModel` memuat VARK aktif via `/auth/me` dan feedback terbaru.
- Slider integer 0–100 pada layar materi V/A/R.
- Label **“Belum ada feedback”** saat `null`; nilai 0 tetap ditampilkan sebagai 0%.
- Tombol Simpan Feedback; error/loading state.
- Tidak ada slider untuk Kinesthetic.

### 3.2 Bukti

```text
./gradlew :app:assembleDebug :app:testDebugUnitTest -> BUILD SUCCESSFUL
  Android unit tests: 16
    - StudentMaterialsViewModelTest 3
      (load, load feedback 75 + save 80, material unavailable)

./gradlew :app:lintDebug -> BUILD SUCCESSFUL
```

## 4. Catatan dan batasan

- Belum ada emulator/device run; slider dan penyimpanan feedback belum diuji end-to-end di perangkat.
- Analitik dosen (FR-029–FR-033) belum diimplementasikan; baru feedback mahasiswa.
- Feedback bersifat subjektif; tidak dicampur dengan nilai Kinesthetic.
- SRS asli tetap tidak berubah.

## 5. Langkah berikutnya

Fase 5 — studi kasus Kinesthetic:

- satu studi kasus per topik + deadline;
- text editor tanpa draft;
- submission 1–10.000 karakter, sekali kirim;
- validasi deadline atomik `server_now <= deadline_at`;
- status Belum/Tidak Mengumpulkan/Menunggu Penilaian.
