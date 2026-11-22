# Phase 3 Report — Topics, Materials, Storage & Student Access

**Tanggal:** 20 November 2026  
**Status:** Selesai secara fungsional dan terverifikasi.  
**Lingkup:** FR-009–FR-017; D-05, D-06, D-07, D-14.

## 1. Gate Fase 3

| Gate | Target | Hasil |
|---|---|---|
| CRUD topik dosen | Create/update/publish/unpublish/soft delete | **Lulus** |
| Publish tanpa materi lengkap | Topik terbit meski kategori kosong | **Lulus** (test) |
| Akses materi per VARK | Hanya kategori sesi | **Lulus** (test) |
| Pesan materi kosong | Pesan persis FR-016 | **Lulus** (test) |
| Filter VARK | Visual vs Aural terpisah | **Lulus** (test) |
| Upload file | Validasi magic bytes + R2 + signed URL | **Lulus dengan storage mock** |
| Soft delete | Topik/materi tidak muncul; data akademik dijaga | **Lulus** (test) |
| Reorder topik | Urutan tercermin ke mahasiswa | **Lulus** (test) |
| RBAC | Mahasiswa/admin tidak bisa kelola topik | **Lulus** (test) |
| Backend typecheck/lint/test | Semua hijau | **Lulus; 44 test** |
| Android build/test/lint | Semua hijau | **Lulus; 15 unit test** |
| Compose UI test | Compile | **Lulus compile; belum dijalankan di emulator** |

## 2. Backend

### 2.1 Endpoint

```text
GET    /api/v1/topics
GET    /api/v1/topics/:id
GET    /api/v1/topics/:id/materials

GET    /api/v1/lecturer/topics
POST   /api/v1/lecturer/topics
PATCH  /api/v1/lecturer/topics/reorder
GET    /api/v1/lecturer/topics/:id
PATCH  /api/v1/lecturer/topics/:id
DELETE /api/v1/lecturer/topics/:id
POST   /api/v1/lecturer/topics/:id/publish
POST   /api/v1/lecturer/topics/:id/unpublish
GET    /api/v1/lecturer/topics/:id/materials
POST   /api/v1/lecturer/topics/:id/materials/file
POST   /api/v1/lecturer/topics/:id/materials/link
PATCH  /api/v1/lecturer/materials/:id
DELETE /api/v1/lecturer/materials/:id
```

### 2.2 Implementasi

- Topik: draft/published, urutan dosen, publish tanpa format lengkap, soft delete, guard data akademik.
- Reorder dua fase untuk menghindari konflik `ux_topic_order`.
- Materi: file R2 + tautan HTTPS; metadata kategori/judul/tipe/ukuran/tanggal.
- Upload: multer memory, limit 100 MB, validasi `MAX_UPLOAD_BYTES`, magic bytes, MIME allowlist.
- Signed URL R2 TTL 5 menit; bucket privat.
- Mahasiswa: hanya topik terbit di kelas aktif; hanya materi VARK sesi.
- Pesan persis: **“materi belum tersedia, harap hubungi dosen terlebih dahulu”**.
- `requireVark` diterapkan pada endpoint topik/materi.
- Audit: topik dibuat/diubah/terbit/tarik/arsip/reorder; materi dibuat/diubah/dihapus.

### 2.3 Bukti

```text
npm run typecheck -> lulus
npm run lint      -> lulus
npm test          -> 8 files, 44 tests passed
  - topics integration: 9 tests
    publish tanpa materi, sembunyikan draft/unpublish,
    filter VARK, soft delete, guard data akademik, reorder,
    upload PDF + signed URL, tolak file palsu/kosong, RBAC + VARK gate
```

## 3. Android

### 3.1 Implementasi

- `TopicRepository` + `ApiExecutor` + DTO topik/materi.
- Student:
  - daftar topik terbit + status ketersediaan VARK;
  - daftar materi sesuai VARK;
  - buka tautan/signed URL via Intent.
- Lecturer:
  - daftar topik draft/terbit;
  - tambah topik, terbitkan/tarik, hapus, naik/turun urutan;
  - daftar materi semua kategori;
  - tambah tautan HTTPS;
  - unggah file via dokumen picker (PDF/gambar/audio/video);
  - hapus materi (soft delete).
- Home role-aware: mahasiswa → topik, dosen → kelola topik.

### 3.2 Bukti

```text
./gradlew :app:assembleDebug :app:testDebugUnitTest -> BUILD SUCCESSFUL
  Android unit tests: 15
    - VarkTypeTest 3
    - LoginViewModelTest 3
    - ChangePasswordViewModelTest 3
    - VarkSelectionViewModelTest 2
    - StudentTopicsViewModelTest 2
    - StudentMaterialsViewModelTest 2

./gradlew :app:lintDebug -> BUILD SUCCESSFUL
./gradlew :app:assembleDebugAndroidTest -> BUILD SUCCESSFUL
```

## 4. Catatan dan batasan

- **R2 tidak dikonfigurasi di lingkungan lokal.** Test upload memakai mock storage; integrasi R2 nyata belum diuji.
- Upload file di Android membaca seluruh file ke memori; untuk file besar perlu streaming di iterasi berikutnya.
- Multer memakai memory storage; batas 100 MB.
- Belum ada emulator/device run; UI dan file picker belum diuji end-to-end di perangkat.
- Belum ada endpoint feedback/Kinesthetic; itu Fase 4–5.
- SRS asli tetap tidak berubah.

## 5. Langkah berikutnya

Fase 4 — feedback pemahaman V/A/R:

- slider integer 0–100;
- upsert unik mahasiswa–topik–kategori;
- preserve kategori lain;
- hanya V/A/R dan materi tersedia;
- UI feedback + analitik dasar.
