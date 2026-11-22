# API Contract — EduVARK v0.7

**Status:** Diimplementasikan pada backend Express (`backend/src/modules`). Kontrak di bawah
menjadi acuan Android.  
**Base path:** `/api/v1` (dipakai production di `https://pam.sequein.my.id/api/v1/`).  
**Sumber:** SRS v0.7, D-01–D-18, §6 aturan validasi, ERD logis.  
**Auth:** JWT access + refresh + tabel sesi (D-03/D-16).

## 1. Konvensi

### 1.1 Header

| Header | Wajib | Isi |
|---|---|---|
| `Authorization` | Ya, kecuali login/refresh/health | `Bearer <access_token>` |
| `Content-Type` | Ya untuk body | `application/json`; multipart untuk upload |
| `X-Request-Id` | Tidak | Jika tidak ada, server membuat |
| `Idempotency-Key` | Tidak | Opsional untuk submission; unique constraint tetap utama |

### 1.2 Response sukses

```json
{
  "data": {},
  "meta": {
    "requestId": "req_...",
    "page": 1,
    "pageSize": 20,
    "total": 0
  }
}
```

- `meta` hanya untuk list/pagination; untuk single resource boleh hanya `requestId`.
- `201` untuk resource baru; `204` untuk logout/reset/deaktivasi/soft delete.

### 1.3 Response error

```json
{
  "error": {
    "code": "DEADLINE_PASSED",
    "message": "Deadline telah lewat.",
    "details": [],
    "requestId": "req_..."
  }
}
```

### 1.4 Tanggal dan zona waktu

- Semua timestamp dikirim **ISO 8601 UTC** (`2026-11-20T10:00:00Z`).
- Deadline ditampilkan UI sebagai **WIB (UTC+7)**.
- API menyertakan `timezone: "Asia/Jakarta"` pada resource deadline.
- Perbandingan deadline memakai `now()` database, bukan waktu klien.

### 1.5 Enum

| Enum | Nilai |
|---|---|
| `Role` | `STUDENT`, `LECTURER`, `ADMIN` |
| `UserStatus` | `ACTIVE`, `INACTIVE` |
| `VarkType` | `VISUAL`, `AURAL`, `READ_WRITE`, `KINESTHETIC` |
| `TopicStatus` | `DRAFT`, `PUBLISHED` |
| `MaterialKind` | `FILE`, `LINK` |
| `EnrollmentStatus` | `ACTIVE`, `LEFT` |
| `SubmissionStatus` | `BELUM_MENGUMPULKAN`, `TIDAK_MENGUMPULKAN`, `MENUNGGU_PENILAIAN`, `SUDAH_DINILAI` |

> Label UI “Audio” dipetakan ke `AURAL` (D-14).

### 1.6 Pagination

- Query: `page` (default 1), `pageSize` (default 20, max 100).
- Response: `meta.page`, `meta.pageSize`, `meta.total`.
- List admin/users dan submissions memakai pagination.

### 1.7 Status code

| Code | Arti |
|---|---|
| `200` | OK |
| `201` | Created |
| `204` | No Content |
| `400` | Malformed request |
| `401` | Tidak terautentikasi/token invalid |
| `403` | Tidak berwenang |
| `404` | Tidak ditemukan |
| `405` | Operasi tidak didukung (mis. update submission) |
| `409` | Konflik state (sudah submit, sudah dinilai, deadline, VARK lock) |
| `413` | File terlalu besar |
| `422` | Validasi field/business rule |
| `428` | VARK belum dipilih |
| `429` | Rate limit |
| `500` | Internal |
| `502` | Storage/upstream gagal |

## 2. Autentikasi

### 2.1 `POST /auth/login`

**Akses:** publik.  
**Body:**

```json
{ "loginId": "2210512001", "password": "..." }
```

**Sukses `200`:**

```json
{
  "data": {
    "accessToken": "...",
    "refreshToken": "...",
    "expiresIn": 900,
    "user": {
      "id": "uuid",
      "loginId": "2210512001",
      "fullName": "Nama",
      "role": "STUDENT",
      "mustChangePassword": false
    },
    "varkRequired": true,
    "activeVark": null
  }
}
```

- `varkRequired = true` hanya untuk mahasiswa yang belum memilih VARK.
- Dosen/admin: `varkRequired = false`, `activeVark = null` (D-18).
- Login baru mencabut sesi lama (D-04).

**Gagal:** `401 INVALID_CREDENTIALS`; `403 ACCOUNT_INACTIVE`; `403 PASSWORD_CHANGE_REQUIRED` (jika hanya endpoint tertentu); `429 TOO_MANY_ATTEMPTS`.

### 2.2 `POST /auth/refresh`

**Body:** `{ "refreshToken": "..." }`  
**Sukses `200`:** access token baru + refresh token baru (rotasi).  
**Gagal:** `401 REFRESH_INVALID`; `401 SESSION_REVOKED/EXPIRED`.

### 2.3 `POST /auth/logout`

**Akses:** terautentikasi.  
**Sukses `204`.** Mencabut sesi saat ini. Semua role.

### 2.4 `GET /auth/me`

**Sukses `200`:** profil, role, status, `activeVark`, `mustChangePassword`, `classId` (jika ada).

### 2.5 `POST /auth/change-password`

**Body:** `{ "currentPassword": "...", "newPassword": "..." }`  
**Sukses `204`.** Wajib saat `mustChangePassword = true`. Password **min. 6 karakter** tanpa aturan komposisi lain (D-19). Setelah ganti, sesi lain dicabut.

### 2.6 `POST /auth/vark`

**Akses:** mahasiswa, sekali per sesi.  
**Body:** `{ "vark": "VISUAL" }`  
**Sukses `200`:** `{ "activeVark": "VISUAL", "locked": true }`.  
**Gagal:** `409 VARK_ALREADY_SET`; `422 INVALID_VARK`; `403 FORBIDDEN` untuk staf.  
**Aturan:** tidak ada endpoint untuk mengganti VARK dalam sesi yang sama.

## 3. Admin — Akun dan Enrollment

### 3.1 `GET /admin/users`

**Query:** `q`, `role`, `status`, `page`, `pageSize`.  
**Sukses `200`:** list user (tanpa password/token).

### 3.2 `POST /admin/users`

**Body:**

```json
{
  "loginId": "2210512001",
  "fullName": "Nama",
  "role": "STUDENT",
  "temporaryPassword": "..."
}
```

**Sukses `201`:** user + enrollment/teaching assignment otomatis (D-06).  
**Gagal:** `409 LOGIN_ID_TAKEN`; `422 INVALID_ROLE`; `422 WEAK_PASSWORD`.

### 3.3 `GET /admin/users/:id`

**Sukses `200`:** detail. `404` jika tidak ada.

### 3.4 `PATCH /admin/users/:id`

**Body:** `{ "fullName"?, "role"? }`  
**Sukses `200`.** Perubahan role mencabut semua sesi target.  
**Gagal:** `409 LOGIN_ID_TAKEN` (jika login berubah—tidak disediakan di MVP); `422 INVALID_ROLE`; `403 ADMIN_ACADEMIC_FORBIDDEN` jika menyentuh data akademik.

> Deaktivasi tidak melalui endpoint ini; gunakan `POST /admin/users/:id/deactivate`.

### 3.5 `POST /admin/users/:id/reset-password`

**Body:** `{ "temporaryPassword": "..." }`  
**Sukses `204`.** Mencabut semua sesi target; `mustChangePassword = true`.

### 3.6 `POST /admin/users/:id/deactivate`

**Sukses `204`.** Status `INACTIVE`; semua sesi dicabut; histori tetap.  
**Gagal:** `409 LAST_ADMIN_PROTECTED`; `404`.

### 3.7 `PATCH /admin/enrollments/:id`

**Body:** `{ "status": "LEFT", "leftAt": "2026-11-20T10:00:00Z" }`  
**Sukses `200`.** Akses dicabut; histori tetap; re-enroll mengaktifkan kembali (D-17).  
**Gagal:** `404`; `422 INVALID_ENROLLMENT_STATUS`.

## 4. Topik dan Materi

### 4.1 `GET /topics`

**Akses:** mahasiswa (terbit, enrolled, VARK aktif) dan dosen (kelas sendiri).  
**Query:** `page`, `pageSize`.  
**Sukses `200`:** list topik dengan `sortOrder`, `status`, `materialAvailable` (untuk kategori sesi mahasiswa).

### 4.2 `GET /topics/:id`

**Sukses `200`:** detail topik + `materialAvailable` + `caseAvailable`.  
**Gagal:** `404 TOPIC_NOT_FOUND`; `403 NOT_ENROLLED`.

### 4.3 `POST /lecturer/topics`

**Body:** `{ "title": "...", "description": "...", "sortOrder": 1 }`  
**Sukses `201`.** Dosen harus assigned ke kelas.

### 4.4 `PATCH /lecturer/topics/:id`

**Body:** `{ "title"?, "description"?, "sortOrder"? }`  
**Sukses `200`.**

### 4.5 `POST /lecturer/topics/:id/publish`

**Sukses `200`.** Boleh meski materi belum lengkap (FR-013).

### 4.6 `POST /lecturer/topics/:id/unpublish`

**Sukses `200`.** Topik disembunyikan dari mahasiswa; data tetap.

### 4.7 `DELETE /lecturer/topics/:id`

**Sukses `204`.** Soft delete.  
**Gagal:** `409 TOPIC_HAS_ACADEMIC_DATA` jika sudah ada submission/assessment; sarankan unpublish/arsip.

### 4.8 `PATCH /lecturer/topics/reorder`

**Body:** `{ "topicIds": ["uuid1", "uuid2"] }`  
**Sukses `200`.** Urutan final sesuai array.

### 4.9 `POST /lecturer/topics/:id/materials/file`

**Content-Type:** `multipart/form-data`; field `file`, `title`, `vark`.  
**Sukses `201`:** metadata + `materialId`.  
**Gagal:** `413 FILE_TOO_LARGE`; `422 INVALID_FILE_TYPE`; `502 STORAGE_UNAVAILABLE`.

### 4.10 `POST /lecturer/topics/:id/materials/link`

**Body:** `{ "title": "...", "vark": "AURAL", "url": "https://..." }`  
**Sukses `201`.**

### 4.11 `PATCH /lecturer/materials/:id`

**Body:** `{ "title"?, "vark"? }`  
**Sukses `200`.**

### 4.12 `DELETE /lecturer/materials/:id`

**Sukses `204`.** Soft delete.

### 4.13 `GET /topics/:id/materials`

**Akses:** mahasiswa. Mengembalikan **hanya** materi kategori VARK sesi.  
**Sukses `200`:** list materi + `signedUrl` (TTL 5 menit) untuk file.  
**Gagal:** `404 MATERIAL_NOT_AVAILABLE` dengan pesan persis FR-016; `403 MATERIAL_CATEGORY_FORBIDDEN` jika meminta kategori lain.

## 5. Feedback

### 5.1 `GET /topics/:id/feedback`

**Akses:** mahasiswa V/A/R.  
**Sukses `200`:** `{ "value": 75 | null, "updatedAt": "..." }`.  
**Gagal:** `403 FEEDBACK_NOT_APPLICABLE` untuk Kinesthetic; `404 MATERIAL_NOT_AVAILABLE`.

### 5.2 `PUT /topics/:id/feedback`

**Body:** `{ "value": 75 }` (integer 0–100).  
**Sukses `200/201`.** Upsert triple (student, topic, session VARK).  
**Gagal:** `422 INVALID_FEEDBACK_VALUE`; `403 VARK_MISMATCH`; `403 FEEDBACK_FORBIDDEN`; `404 MATERIAL_NOT_AVAILABLE`.

## 6. Studi Kasus Kinesthetic

### 6.1 `GET /topics/:id/case`

**Akses:** mahasiswa Kinesthetic.  
**Sukses `200`:** teori, instruksi, `deadlineAt`, `deadlineWib`, status submission.  
**Gagal:** `404 CASE_NOT_FOUND` (“studi kasus belum tersedia”); `403 VARK_MISMATCH`.

### 6.2 `PUT /lecturer/topics/:id/case`

**Body:** `{ "theory"?, "instructions": "...", "deadlineAt": "2026-12-01T16:59:59Z" }`  
**Sukses `201/200`.** Upsert 1:1; deadline UTC; audit.  
**Gagal:** `422 DEADLINE_INVALID`; `409 DEADLINE_LOCKED` jika sudah ada submission; `403 CLASS_FORBIDDEN`.

### 6.3 `POST /topics/:id/case/submission`

**Body:** `{ "answerText": "..." }`  
**Sukses `201`:** submission + status `MENUNGGU_PENILAIAN`.  
**Gagal:** `422 ANSWER_LENGTH_INVALID`; `409 ALREADY_SUBMITTED`; `409 DEADLINE_PASSED`; `403 VARK_MISMATCH`; `404 CASE_NOT_FOUND`.

### 6.4 `GET /submissions/:id`

**Akses:** pemilik atau dosen kelas.  
**Sukses `200`:** teks, waktu, status, nilai/komentar jika ada.  
**Gagal:** `403`; `404`.

## 7. Penilaian

### 7.1 `GET /lecturer/cases/:id/submissions`

**Sukses `200`:** list mahasiswa + status + submissionId (jika ada).

### 7.2 `POST /submissions/:id/assessment`

**Body:** `{ "score": 85, "comment": "..." }` (comment opsional ≤1.000).  
**Sukses `201`:** assessment final.  
**Gagal:** `422 INVALID_SCORE`; `422 INVALID_COMMENT`; `409 ALREADY_ASSESSED`; `403 CLASS_FORBIDDEN`; `403 ADMIN_ACADEMIC_FORBIDDEN`.

## 8. Analitik

### 8.1 `GET /lecturer/analytics/topics/:id`

**Query:** `category` (VISUAL/AURAL/READ_WRITE; opsional).  
**Sukses `200`:**

```json
{
  "data": {
    "topicId": "uuid",
    "category": "VISUAL",
    "respondentCount": 12,
    "mean": 72.5,
    "histogram": [
      { "bucket": "0-20", "count": 0 },
      { "bucket": "21-40", "count": 1 }
    ],
    "notRespondedCount": 3,
    "aggregation": "mean of latest feedback per student-topic-category",
    "kinesthetic": {
      "assessedCount": 10,
      "meanScore": 80.1
    }
  }
}
```

- `notRespondedCount` terpisah; tidak dihitung 0%.
- `kinesthetic` metrik objektif terpisah.

## 9. Matriks izin endpoint

| Endpoint | Mahasiswa | Dosen | Admin |
|---|---|---|---|
| `POST /auth/login` | ✓ | ✓ | ✓ |
| `POST /auth/logout` | ✓ | ✓ | ✓ |
| `POST /auth/vark` | ✓ | — | — |
| `GET /topics` | ✓ | ✓ | — |
| `GET /topics/:id/materials` | ✓ | ✓ | — |
| `PUT /topics/:id/feedback` | ✓ | — | — |
| `POST /topics/:id/case/submission` | ✓ | — | — |
| `GET /submissions/:id` | ✓ (own) | ✓ (class) | — |
| `POST /lecturer/topics` | — | ✓ | — |
| `POST /submissions/:id/assessment` | — | ✓ | — |
| `GET /lecturer/analytics/*` | — | ✓ | — |
| `/admin/*` | — | — | ✓ |

## 10. Error codes utama

| Code | HTTP | Konteks |
|---|---|---|
| `INVALID_CREDENTIALS` | 401 | Login |
| `ACCOUNT_INACTIVE` | 403 | Login |
| `PASSWORD_CHANGE_REQUIRED` | 403 | Sebelum ganti password |
| `TOO_MANY_ATTEMPTS` | 429 | Rate limit |
| `SESSION_REVOKED` | 401 | Logout/deaktivasi |
| `VARK_REQUIRED` | 428 | Endpoint akademik sebelum pilih VARK |
| `INVALID_VARK` | 422 | Nilai VARK salah |
| `VARK_ALREADY_SET` | 409 | Pilih VARK kedua |
| `VARK_LOCKED` | 409 | Ganti VARK dalam sesi |
| `VARK_MISMATCH` | 403 | Akses kategori lain |
| `MATERIAL_NOT_AVAILABLE` | 404 | FR-016 |
| `MATERIAL_CATEGORY_FORBIDDEN` | 403 | FR-015 |
| `INVALID_FEEDBACK_VALUE` | 422 | Di luar 0–100 |
| `FEEDBACK_NOT_APPLICABLE` | 403 | Sesi Kinesthetic |
| `ANSWER_LENGTH_INVALID` | 422 | 0/<1/>10.000 |
| `ALREADY_SUBMITTED` | 409 | Submission kedua |
| `DEADLINE_PASSED` | 409 | `server_now > deadline_at` |
| `DEADLINE_LOCKED` | 409 | Ubah deadline setelah submit |
| `ALREADY_ASSESSED` | 409 | Nilai kedua |
| `INVALID_SCORE` | 422 | Di luar 0–100 |
| `INVALID_COMMENT` | 422 | >1.000 |
| `LOGIN_ID_TAKEN` | 409 | Duplikasi |
| `LAST_ADMIN_PROTECTED` | 409 | Guardrail admin |
| `CLASS_FORBIDDEN` | 403 | Dosen kelas lain |
| `NOT_ENROLLED` | 403 | Mahasiswa tidak terdaftar |
| `STORAGE_UNAVAILABLE` | 502 | R2 gagal |

## 11. Idempotensi dan konkurensi

- Submission: `UNIQUE(student_id, case_study_id)`; request paralel → satu `201`, satu `409`.
- Assessment: `UNIQUE(submission_id)`; request paralel → satu `201`, satu `409`.
- Deadline: `SELECT ... FOR UPDATE` pada `case_studies` + `now()` database di transaksi.
- Login: transaksi menonaktifkan sesi lama sebelum membuat sesi baru.
- Feedback: upsert pada unique triple.
- Reorder topik: transaksi dua fase untuk menghindari konflik `sort_order`.

## 12. Catatan versioning

- `/api/v1` adalah proposal; OQ-12 belum final.
- Perubahan breaking harus menaikkan versi.
- OpenAPI spec akan dibuat pada tahap implementasi; dokumen ini adalah kontrak naratif.
