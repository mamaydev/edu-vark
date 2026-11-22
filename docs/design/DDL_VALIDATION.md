# DDL Validation Report — EduVARK v0.7

**Tanggal:** 20 November 2026  
**Objek:** [`DATABASE_SCHEMA_DRAFT.sql`](DATABASE_SCHEMA_DRAFT.sql)  
**Lingkungan:** PostgreSQL lokal disposable (`initdb` + `pg_ctl`), port `55432`, database `eduvark_test`. Cluster dihapus setelah pengujian. **Tidak memakai data produksi.**

## 1. Hasil apply DDL

- `BEGIN` … `COMMIT` berhasil.
- 12 tabel dibuat: `users`, `classes`, `enrollments`, `teaching_assignments`, `auth_sessions`, `topics`, `materials`, `feedback`, `case_studies`, `submissions`, `assessments`, `audit_logs`.
- Trigger dibuat: `updated_at` (6 tabel), `session_vark_lock`, `submission_immutable`, `assessment_immutable`, `audit_append_only`, `deadline_lock`, `prevent_topic_hard_delete`.
- Exit code apply: **0**.

## 2. Test constraint dan trigger

| # | Skenario | Ekspektasi | Hasil |
|---|---|---|---|
| 1 | `UPDATE submissions.answer_text` | `SUBMISSION_IMMUTABLE` | OK |
| 2 | `UPDATE assessments.score` | `ASSESSMENT_IMMUTABLE` | OK |
| 3 | Ubah `deadline_at` setelah ada submission | `DEADLINE_LOCKED` | OK |
| 4 | Ubah `vark_type` sesi yang sudah diisi | `VARK_LOCKED` | OK |
| 5 | Submission kedua untuk mahasiswa–kasus sama | `ux_submission_pair` | OK |
| 6 | Assessment kedua untuk submission sama | `ux_assessment_submission` | OK |
| 7 | Dua topik aktif dengan `sort_order` sama | `ux_topic_order` | OK |
| 8 | Dua sesi aktif untuk user yang sama | `ux_auth_active_user` | OK |
| 9 | Feedback 101 | `ck_feedback_value` | OK |
| 10 | Jawaban 10.001 karakter | `ck_submission_length` | OK |
| 11 | Enrollment `LEFT` tanpa `left_at` | `ck_enrollment_left` | OK |
| 12 | Upsert feedback pada triple yang sama | Tetap 1 baris | OK |
| 13 | `UPDATE materials.title` | Trigger `updated_at` berjalan tanpa error | OK |

**Output akhir:** `SCHEMA_TESTS_OK`  
**Exit code test:** **0**

## 2.1 Revalidasi Fase 8

- Ditemukan bug: trigger `trg_materials_updated` memanggil `updated_at` yang belum ada di tabel `materials`.
- Kolom `updated_at timestamptz NOT NULL DEFAULT now()` ditambahkan ke migration dan DDL draft.
- DDL draft di-apply ulang di PostgreSQL disposable; `UPDATE materials SET title = ...` berhasil dan menghasilkan `MATERIAL_UPDATED_AT_OK`.
- Revalidasi ini menutup bug update/soft-delete materi yang sebelumnya menghasilkan 500.

## 3. Catatan

- Yang diuji adalah **DDL/constraint/trigger**, bukan API, autentikasi, UI, atau concurrency end-to-end.
- `UNIQUE(student_id, case_study_id)` dan `UNIQUE(submission_id)` sudah terbukti menolak duplikasi pada level database; uji concurrency API tetap diperlukan di fase implementasi.
- Trigger `prevent_topic_hard_delete` belum diuji dengan data submission; FK `ON DELETE RESTRICT` juga melindungi. Uji tambahan disarankan saat integration test.
- DDL belum menjadi migration produksi; review index/EXPLAIN setelah data representatif tersedia.

## 4. Reproduksi

Pengujian dijalankan dengan:

1. `initdb` pada direktori sementara.
2. `pg_ctl start` pada port `55432`.
3. `psql -f docs/design/DATABASE_SCHEMA_DRAFT.sql`.
4. Skrip test constraint/trigger (fixture 4 user, 1 kelas, 1 topik, 1 kasus).
5. `pg_ctl stop` + hapus direktori data.

Skrip test tidak disimpan sebagai artefak aplikasi; hasil di atas adalah bukti eksekusi pada tanggal tersebut.
