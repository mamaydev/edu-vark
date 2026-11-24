# Laporan sanitasi artefak bukti `compat-e2e/`

**Task:** `task-16` — sanitasi artefak dan audit bukti AT-04/AT-17
**Tanggal:** 24 November 2026, 21:14 WIB
**Branch:** `fix/eduvark-functional-audit-2026` (HEAD `b7b6cd6`)
**Cakupan tulis:** hanya `docs/audit/evidence/compat-e2e/`, `docs/audit/evidence/at-corrections/`, dan file ini. Laporan utama (`EXECUTION-REPORT-2026-11-24.md`), `ROLLOUT-RUNBOOK.md`, kode, dan workflow **tidak diubah**. Tidak ada commit/push.

**Kosakata status:** `PASS` (bersih atau sudah diredaksi dan tervalidasi) / `NEEDS VERIFICATION` / `NOT RUN`.

## 1. Ringkasan hasil

| Artefak | Temuan | Tindakan | Status |
|---|---|---|---|
| `old-signed-url-ttl.json` | Parameter `signature=` berisi HMAC mentah 64 heksadesimal pada `evidence.signedUrl` | Nilai signature diganti `[REDACTED]`; parameter `expires`, `expiresParamEpoch`, `expiresParamIso`, `expiresAtField`, `computedTtlSeconds`, status HTTP 200, dan `method` dipertahankan; blok `sanitization` ditambahkan | PASS (diredaksi) |
| `new-backend-numeric-admin-login-200.json` | `data.accessToken` dan `data.refreshToken` | Sudah `[REDACTED]` pada koreksi sebelumnya; diverifikasi ulang, struktur dan field user utuh, JSON valid | PASS |
| `server-request-evidence.json` | Hanya nama header (`requestHeaders`) dan boolean `authorizationPresent`; tidak ada nilai header | Tidak ada perubahan | PASS (tidak mengandung kredensial) |
| `logs/rollback-oldimage-newdb.log` | Log request berformat JSON-lines; header `authorization` sudah `[redacted]`; tidak ada body/token | Tidak ada perubahan | PASS |
| `logs/last-logcat-excerpt.txt` | Hanya baris method/URL/status/ukuran body HTTP; tidak ada token/JWT | Tidak ada perubahan | PASS |
| `screens/*.xml` + `screens/*.png` | Kolom kata sandi bermasking (`••••`), tidak ada kata sandi plaintext; memuat ID fixture (NIM/NIDN/ID Admin) yang bukan kredensial | Tidak diredaksi; lihat butir 4 | PASS (tidak ada kredensial) |
| JSON lain (`cross-version-matrix`, `min-version-gate-evidence`, `real-screen-evidence`, `rollback-image-without-db-restore`, `new-backend-analytics-vark-null`, `new-backend-material-list-old-client`, `new-backend-legacy-admin-login-401`, `old-backend-content-endpoint-404`) | Tidak ada token, JWT, kata sandi, signature, cookie, API key | Tidak ada perubahan | PASS |
| `README.md` | Prosa; tidak ada kredensial | Tidak ada perubahan | PASS |

**Tidak ada artefak lain di `compat-e2e/` yang memuat token, JWT, kata sandi, atau signature mentah.**

## 2. Metode pemeriksaan

1. Pemindaian rekursif seluruh kunci JSON (termasuk nilai string) untuk pola: `token`, `signature`, `password`, `secret`, `authorization`, `jwt`, `bearer`, `api_key`, `credential`.
2. Pemindaian regex isi file (JSON, log, XML, teks) untuk: JWT (`eyJ…`), `Bearer …`, `signature=<hex>`, `accessToken`/`refreshToken` non-`[REDACTED]`, `password: "…"`, `Cookie`/`Set-Cookie`.
3. Validasi JSON untuk semua berkas `.json` di `compat-e2e/`.
4. Inspeksi visual teks layar (`uiautomator` XML) untuk memastikan kolom kata sandi bermasking dan tidak ada jawaban/kredensial mentah.
5. Pemeriksaan ulang pasca-redaksi dengan pola yang sama untuk memastikan tidak ada sisa nilai rahasia.

## 3. Detail redaksi `old-signed-url-ttl.json`

- Sebelum: `…material-files?key=materials%2F0bd48328-…pdf&expires=1795528021&signature=6bcbf4da8e02d742…c3584493`
- Sesudah: `…material-files?key=materials%2F0bd48328-…pdf&expires=1795528021&signature=[REDACTED]`
- Yang dipertahankan (assertion tetap auditabel): `method`, status HTTP 200 pada `oldBackendMaterialList`, `expiresParamEpoch` 1795528021, `expiresParamIso`/`expiresAtField`, `computedTtlSeconds` 299.349, `productionTtl` dan `windowRule` (tetap `NEEDS VERIFICATION`, tidak dinaikkan).
- Blok `sanitization` mencatat waktu redaksi, field yang diredaksi, field yang dipertahankan, dan catatan bahwa signature lama tidak dapat dipakai ulang.
- Artefak dirujuk oleh `docs/audit/evidence/ROLLOUT-RUNBOOK.md`; redaksi tidak mengubah struktur rujukan.

## 4. Catatan tentang data non-kredensial

Artefak emulator masih memuat ID fixture: NIM/NIDN (`2025000001`, `2025000002`, `1990010001`, `01401400001`, `admin`), UUID kelas/topik/materi, dan teks materi uji. Semua ini adalah data fixture lokal, bukan kredensial atau data produksi; tidak diredaksi pada task ini karena dibutuhkan untuk keterlacakan bukti. Bila artefak akan dibagikan ke luar tim, pseudonimisasi ID dapat dilakukan sebagai langkah terpisah (di luar cakupan `task-16`).

## 5. Validasi akhir

- Validasi parse JSON: **11/11 berkas valid** di `docs/audit/evidence/compat-e2e/`.
- Pemindaian sisa rahasia: **tidak ditemukan** `signature=<hex>`, JWT (`eyJ…`), `Bearer <token>`, `accessToken`/`refreshToken` non-`[REDACTED]`, maupun kata sandi plaintext.
- Tidak ada perubahan di luar cakupan tulis yang diizinkan; tidak ada commit/push.

## 6. Batasan dan rekomendasi

- Signature sudah tidak dapat dipakai ulang, tetapi parameter `key=` dan `expires=` tetap ada karena diperlukan untuk assertion TTL. Risiko residu rendah (objek penyimpanan lokal, bukan kredensial).
- Bila bukti `compat-e2e/` dipublikasikan: jalankan ulang pemeriksaan regex di §2, dan pertimbangkan menghapus `key=` serta mem-pseudonimkan ID fixture.
- Audit bukti AT-04 dan AT-17 terpisah ada di [`at-corrections/AT-04.md`](at-corrections/AT-04.md) dan [`at-corrections/AT-17.md`](at-corrections/AT-17.md). Keduanya memakai kosakata status yang sama dan memisahkan lapisan API/unit/resource/ViewModel/komposabel/emulator/staging.
