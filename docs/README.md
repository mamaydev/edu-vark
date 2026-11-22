# EduVARK — Dokumentasi Analisis Sistem (v0.7)

Folder ini berisi **penyempurnaan dokumentasi analisis** untuk `SRS_EduVARK_v0.7`.  
Fokus pekerjaan: **analisis kebutuhan, dokumentasi, dan pemodelan sistem**. Tidak ada implementasi Android maupun backend di tahap ini.

## Sumber acuan

- [SRS asli](../SRS_EduVARK_v0.7.docx) — **tidak diubah**.
- [Decision log](DECISIONS.md) — keputusan wawancara 20 November 2026 (D-01–D-18).
- [Open questions](srs/OPEN_QUESTIONS.md) — keputusan yang masih terbuka.

## Peta dokumen

| Dokumen | Isi |
|---|---|
| [SRS Completion](srs/SRS_EDUVARK_V0.7_COMPLETION.md) | Penyempurnaan §6 Aturan Validasi dan Status, §7 Ketidakpastian Tersisa, §8 Matriks Cakupan |
| [Requirements Traceability Matrix](srs/REQUIREMENTS_TRACEABILITY_MATRIX.md) | RTM 50 FR → BR → UC → Activity → Entity → AT, termasuk GAP |
| [Open Questions](srs/OPEN_QUESTIONS.md) | Daftar keputusan terbuka, opsi, rekomendasi, dan pertanyaan konfirmasi |
| [Decision Log](DECISIONS.md) | Keputusan D-01–D-18 dan dampaknya |
| [Use Case Specification](use-cases/USE_CASE_SPECIFICATION_EDUVARK_V0.7.md) | 16 use case lengkap (M01–M07, D01–D06, A01–A03) |
| [Use Case Dependencies](use-cases/USE_CASE_DEPENDENCIES_EDUVARK_V0.7.md) | Diagram dependensi per role (Mermaid) |
| [Use Case Diagram Admin](diagrams/use-case/admin.md) | UML use case (PlantUML) |
| [Use Case Diagram Dosen](diagrams/use-case/dosen.md) | UML use case (PlantUML) |
| [Use Case Diagram Mahasiswa](diagrams/use-case/mahasiswa.md) | UML use case (PlantUML) |
| [Activity Mahasiswa](diagrams/activity/mahasiswa.md) | AD-M01–AD-M05 (Mermaid) |
| [Activity Dosen](diagrams/activity/dosen.md) | AD-D01–AD-D05 (Mermaid) |
| [Activity Admin](diagrams/activity/admin.md) | AD-A01–AD-A03 (Mermaid) |
| [ERD Konseptual](diagrams/database/ERD_CONCEPTUAL.md) | Entitas, relasi, kardinalitas |
| [ERD Logis Draft](diagrams/database/ERD_LOGICAL_DRAFT.md) | PK/FK, tipe, constraint, index, trigger |
| [Rendered Diagrams](diagrams/rendered/README.md) | 22 ekspor SVG + PNG dan manifest render |
| [Desain Teknis](design/README.md) | API contract, DDL, struktur folder, rencana implementasi |
| [Phase 1 Report](implementation/PHASE_1_REPORT.md) | Bukti build Android, backend, migration, seed, dan health check |
| [Phase 2 Report](implementation/PHASE_2_REPORT.md) | Bukti auth, sesi, VARK, RBAC, admin, dan test integrasi |
| [Phase 3 Report](implementation/PHASE_3_REPORT.md) | Bukti topik, materi, upload, filter VARK, dan akses mahasiswa |
| [Phase 4 Report](implementation/PHASE_4_REPORT.md) | Bukti feedback V/A/R, upsert, kategori terpisah, dan UI slider |
| [Phase 5 Report](implementation/PHASE_5_REPORT.md) | Bukti studi kasus Kinesthetic, deadline, submission sekali |
| [Phase 6 Report](implementation/PHASE_6_REPORT.md) | Bukti daftar submission, penilaian final, dan hasil mahasiswa |
| [Phase 7 Report](implementation/PHASE_7_REPORT.md) | Bukti analitik mean/n/histogram, filter, dan Kinesthetic terpisah |
| [Phase 8 Report](implementation/PHASE_8_REPORT.md) | Hardening, acceptance 13/13, uji emulator, dan bug materi |
| [Phase 9 Report](implementation/PHASE_9_REPORT.md) | UI admin: daftar pengguna, detail, reset/deactivate, enrollment |
| [Material Upload Fix](implementation/MATERIAL_UPLOAD_FIX.md) | Tombol upload VARK, local dev storage, dan bukti upload emulator |
| [Physical Device Server Config](implementation/PHYSICAL_DEVICE_SERVER_CONFIG.md) | Pengaturan server debug untuk HP fisik dan bukti health probe |
| [Password Policy Change](implementation/PASSWORD_POLICY_CHANGE.md) | Kebijakan password minimal 6 karakter (D-19) |
| [Login Network Fix](implementation/LOGIN_NETWORK_FIX.md) | Akar masalah jaringan login, permission local network, dan bukti login emulator |
| [Acceptance Matrix](acceptance/ACCEPTANCE_MATRIX.md) | Pemetaan AT-P01–AT-P13 ke bukti eksekusi |

## Konvensi ID

- `FR-001`–`FR-050`: functional requirement (dari SRS).
- `BR-01`–`BR-19`: business rule (dari SRS).
- `AT-01`–`AT-17`: acceptance test (dari SRS).
- `UC-M01`–`UC-M07`, `UC-D01`–`UC-D06`, `UC-A01`–`UC-A03`: use case (dari SRS).
- `VAL-*`: aturan validasi baru di §6.
- `AD-M*`, `AD-D*`, `AD-A*`: activity diagram.
- `D-01`–`D-14`: keputusan hasil wawancara.
- `OQ-01`–`OQ-15`: open question.
- `GAP-*`: kekosongan dokumentasi/pengujian.

## Status

- **SRS asli**: tetap v0.7 dan tidak ditimpa.
- **Dokumen ini**: *completion draft* berbasis SRS + keputusan wawancara.
- **Yang belum final**: lihat [Open Questions](srs/OPEN_QUESTIONS.md). Keputusan TBD **tidak** dinaikkan menjadi CONFIRMED tanpa persetujuan.

## Render diagram

- Source Mermaid/PlantUML berada di dalam file `.md` (dapat diedit ulang).
- Ekspor **22 diagram** tersedia dalam **SVG** dan **PNG** di [rendered](diagrams/rendered/README.md).
- SVG adalah vektor; PNG dipakai untuk pratinjau karena sebagian viewer tidak merender label Mermaid pada SVG.
- Untuk merender ulang: `node docs/diagrams/rendered/render_all.mjs` (memerlukan akses ke Kroki).
- Verifikasi sintaks: seluruh 22 diagram berhasil dirender (lihat [manifest](diagrams/rendered/manifest.json)).
