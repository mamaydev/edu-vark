# Desain Teknis Final — EduVARK v0.7

**Status:** Paket desain teknis siap direview. **Belum ada implementasi Android/backend.**  
**Dasar:** SRS v0.7, D-01–D-18, RTM, §6 aturan validasi, use case, ERD.

## Isi paket

| Dokumen | Isi |
|---|---|
| [API Contract](API_CONTRACT.md) | Kontrak REST `/api/v1`: auth, admin, topik/materi, feedback, Kinesthetic, penilaian, analitik, error codes, RBAC |
| [Database Schema DDL](DATABASE_SCHEMA_DRAFT.sql) | DDL PostgreSQL draft: 12 tabel, constraint, index, trigger immutability |
| [DDL Validation](DDL_VALIDATION.md) | Bukti apply DDL + 12 test constraint/trigger di PostgreSQL disposable |
| [Folder Structure](FOLDER_STRUCTURE.md) | Struktur Android Clean Architecture + backend modular monolith |
| [Implementation Plan](IMPLEMENTATION_PLAN.md) | Fase 0–8, dependensi, gate, strategi test, risiko, DoD |
| [UI Redesign](UI_REDESIGN.md) | Design system, pemetaan 40 mockup ke layar Compose, navigasi & logout |
| [Infrastructure Alignment](INFRASTRUCTURE_ALIGNMENT.md) | Audit homelab, perubahan keamanan/config, risiko tersisa |

## Keputusan yang mengunci desain

| ID | Keputusan |
|---|---|
| D-01 | Android native; REST backend; rename EduVARK/`com.eduvark.app`; monorepo |
| D-02 | PostgreSQL via Supabase |
| D-03 | JWT access + refresh + tabel sesi |
| D-04 | Satu sesi aktif; idle 8 jam, absolut 12 jam |
| D-05 | Cloudflare R2; signed URL 5 menit; 100 MB/file |
| D-06 | `classes`, `enrollments`, `teaching_assignments`; auto-enroll/assign |
| D-07 | Soft delete topik/materi |
| D-08 | Satu studi kasus per topik; materi Kinesthetic opsional |
| D-09 | Deadline terkunci setelah submission pertama; UTC/WIB |
| D-10 | Nilai integer 0–100; komentar opsional ≤1.000 |
| D-11 | NIM/NIP; password sementara; admin guardrails |
| D-12 | Mean + n + histogram; belum feedback terpisah |
| D-13 | Audit; retensi 1 tahun; keluar kelas |
| D-14 | Enum `AURAL`, label “Audio” |
| D-15 | Supabase hanya managed PostgreSQL; auth custom; storage R2 |
| D-16 | Password min. 12 (historis); access 15m; refresh 12h; lockout 5/15m |
| D-19 | Password hanya min. 6; tanpa aturan komposisi/blokir umum/batas maksimum |
| D-17 | Leave class via admin endpoint; histori tetap; re-enroll |
| D-18 | UC-M01/M07 untuk semua role; staf tanpa VARK |

## Catatan eksekusi

1. DDL **sudah divalidasi** di database disposable (lihat [DDL Validation](DDL_VALIDATION.md)); belum menjadi migration produksi.
2. API contract memakai `/api/v1` sebagai proposal; OQ-12 belum final.
3. Fase 1 dapat dimulai setelah paket ini disetujui.
4. Fase yang membutuhkan OQ tertentu menunggu keputusan (lihat [Open Questions](../srs/OPEN_QUESTIONS.md)).
5. Tidak ada kode aplikasi yang ditulis pada tahap ini.

## Traceability

- [SRS Completion](../srs/SRS_EDUVARK_V0.7_COMPLETION.md)
- [RTM](../srs/REQUIREMENTS_TRACEABILITY_MATRIX.md)
- [Use Case Specification](../use-cases/USE_CASE_SPECIFICATION_EDUVARK_V0.7.md)
- [ERD Logis](../diagrams/database/ERD_LOGICAL_DRAFT.md)
- [Decision Log](../DECISIONS.md)
