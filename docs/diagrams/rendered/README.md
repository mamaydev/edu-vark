# Rendered Diagrams — EduVARK v0.7

Semua diagram source berada di file `.md` masing-masing. Folder ini berisi ekspor **SVG** (vektor) dan **PNG** (pratinjau dengan label).

**Status render:** 22/22 berhasil (SVG dan PNG). Detail mesin: [`manifest.json`](manifest.json).  
**Catatan:** SVG Mermaid memakai `foreignObject`; sebagian viewer (mis. librsvg) tidak merender teksnya. Gunakan PNG untuk pratinjau, atau buka SVG di browser.

## Activity — Mahasiswa

| Diagram | Source | PNG | SVG |
|---|---|---|---|
| AD-M01 Login & VARK | [mahasiswa.md](../activity/mahasiswa.md) | [PNG](docs__diagrams__activity__mahasiswa__1.png) | [SVG](docs__diagrams__activity__mahasiswa__1.svg) |
| AD-M02 Akses materi | [mahasiswa.md](../activity/mahasiswa.md) | [PNG](docs__diagrams__activity__mahasiswa__2.png) | [SVG](docs__diagrams__activity__mahasiswa__2.svg) |
| AD-M03 Feedback | [mahasiswa.md](../activity/mahasiswa.md) | [PNG](docs__diagrams__activity__mahasiswa__3.png) | [SVG](docs__diagrams__activity__mahasiswa__3.svg) |
| AD-M04 Submit Kinesthetic | [mahasiswa.md](../activity/mahasiswa.md) | [PNG](docs__diagrams__activity__mahasiswa__4.png) | [SVG](docs__diagrams__activity__mahasiswa__4.svg) |
| AD-M05 Lihat hasil | [mahasiswa.md](../activity/mahasiswa.md) | [PNG](docs__diagrams__activity__mahasiswa__5.png) | [SVG](docs__diagrams__activity__mahasiswa__5.svg) |

## Activity — Dosen

| Diagram | Source | PNG | SVG |
|---|---|---|---|
| AD-D01 Kelola/terbitkan | [dosen.md](../activity/dosen.md) | [PNG](docs__diagrams__activity__dosen__1.png) | [SVG](docs__diagrams__activity__dosen__1.svg) |
| AD-D02 Studi kasus & deadline | [dosen.md](../activity/dosen.md) | [PNG](docs__diagrams__activity__dosen__2.png) | [SVG](docs__diagrams__activity__dosen__2.svg) |
| AD-D03 Penilaian final | [dosen.md](../activity/dosen.md) | [PNG](docs__diagrams__activity__dosen__3.png) | [SVG](docs__diagrams__activity__dosen__3.svg) |
| AD-D04 Grafik pemahaman | [dosen.md](../activity/dosen.md) | [PNG](docs__diagrams__activity__dosen__4.png) | [SVG](docs__diagrams__activity__dosen__4.svg) |
| AD-D05 Periksa pengumpulan | [dosen.md](../activity/dosen.md) | [PNG](docs__diagrams__activity__dosen__5.png) | [SVG](docs__diagrams__activity__dosen__5.svg) |

## Activity — Admin

| Diagram | Source | PNG | SVG |
|---|---|---|---|
| AD-A01 Buat akun | [admin.md](../activity/admin.md) | [PNG](docs__diagrams__activity__admin__1.png) | [SVG](docs__diagrams__activity__admin__1.svg) |
| AD-A02 Lihat/ubah akun | [admin.md](../activity/admin.md) | [PNG](docs__diagrams__activity__admin__2.png) | [SVG](docs__diagrams__activity__admin__2.svg) |
| AD-A03 Nonaktifkan akun | [admin.md](../activity/admin.md) | [PNG](docs__diagrams__activity__admin__3.png) | [SVG](docs__diagrams__activity__admin__3.svg) |

## Use Case Diagram

| Diagram | Source | PNG | SVG |
|---|---|---|---|
| Admin | [admin.md](../use-case/admin.md) | [PNG](docs__diagrams__use-case__admin__1.png) | [SVG](docs__diagrams__use-case__admin__1.svg) |
| Dosen | [dosen.md](../use-case/dosen.md) | [PNG](docs__diagrams__use-case__dosen__1.png) | [SVG](docs__diagrams__use-case__dosen__1.svg) |
| Mahasiswa | [mahasiswa.md](../use-case/mahasiswa.md) | [PNG](docs__diagrams__use-case__mahasiswa__1.png) | [SVG](docs__diagrams__use-case__mahasiswa__1.svg) |

## Use Case Dependencies

| Diagram | Source | PNG | SVG |
|---|---|---|---|
| Admin | [USE_CASE_DEPENDENCIES](../../use-cases/USE_CASE_DEPENDENCIES_EDUVARK_V0.7.md) | [PNG](docs__use-cases__USE_CASE_DEPENDENCIES_EDUVARK_V0.7__1.png) | [SVG](docs__use-cases__USE_CASE_DEPENDENCIES_EDUVARK_V0.7__1.svg) |
| Dosen | [USE_CASE_DEPENDENCIES](../../use-cases/USE_CASE_DEPENDENCIES_EDUVARK_V0.7.md) | [PNG](docs__use-cases__USE_CASE_DEPENDENCIES_EDUVARK_V0.7__2.png) | [SVG](docs__use-cases__USE_CASE_DEPENDENCIES_EDUVARK_V0.7__2.svg) |
| Mahasiswa | [USE_CASE_DEPENDENCIES](../../use-cases/USE_CASE_DEPENDENCIES_EDUVARK_V0.7.md) | [PNG](docs__use-cases__USE_CASE_DEPENDENCIES_EDUVARK_V0.7__3.png) | [SVG](docs__use-cases__USE_CASE_DEPENDENCIES_EDUVARK_V0.7__3.svg) |
| Lintas role | [USE_CASE_DEPENDENCIES](../../use-cases/USE_CASE_DEPENDENCIES_EDUVARK_V0.7.md) | [PNG](docs__use-cases__USE_CASE_DEPENDENCIES_EDUVARK_V0.7__4.png) | [SVG](docs__use-cases__USE_CASE_DEPENDENCIES_EDUVARK_V0.7__4.svg) |

## ERD

| Diagram | Source | PNG | SVG |
|---|---|---|---|
| ERD Konseptual | [ERD_CONCEPTUAL.md](../database/ERD_CONCEPTUAL.md) | [PNG](docs__diagrams__database__ERD_CONCEPTUAL__1.png) | [SVG](docs__diagrams__database__ERD_CONCEPTUAL__1.svg) |
| ERD Logis Draft | [ERD_LOGICAL_DRAFT.md](../database/ERD_LOGICAL_DRAFT.md) | [PNG](docs__diagrams__database__ERD_LOGICAL_DRAFT__1.png) | [SVG](docs__diagrams__database__ERD_LOGICAL_DRAFT__1.svg) |

## Regenerate

```bash
node docs/diagrams/rendered/render_all.mjs
```

Script mengekstrak setiap blok `mermaid`/`plantuml` dari `docs/**/*.md`, mengirimnya ke Kroki, lalu menyimpan SVG/PNG dan `manifest.json`.
