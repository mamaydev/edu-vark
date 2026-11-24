# Rollback Utuh dari Snapshot Pra-002 — Bukti Lokal

**Tugas:** task-14 "Simulasi rollback utuh dari snapshot pra-002"
**Pelaksana:** rollout-verifier (teammate)
**Tanggal:** 2026-11-24 (waktu lokal mesin uji)
**Lingkup:** LOKAL saja. Staging/produksi tidak disentuh. Tidak ada commit/push/deploy.
**Direktori bukti:** `docs/audit/evidence/rollback-pre-002/` (hanya folder ini yang ditulis).

Simulasi ini membuktikan jalur rollback penuh untuk remediasi identitas Admin (migrasi 002 + 003):

> **baseline (schema 001 + Admin legacy `admin`)** → snapshot `pg_dump` pra-002 → migrasi 002/003
> → verifikasi backend baru → **restore snapshot ke DB bersih** → verifikasi **APK lama + backend lama**.

Rollback yang sah = **image/backend lama + restore snapshot pra-002**. Menjalankan image lama di atas
DB yang sudah 002/003 **tidak cukup** (kontrak materi/Analytics berbeda; lihat
`../compat-e2e/rollback-image-without-db-restore.json`).

---

## 1. Lingkungan uji

| Komponen | Nilai |
|---|---|
| Repo | `/Users/macbookpro/kuliah/Matkul TI Semester 5/PAM/try-pam` (branch `fix/eduvark-functional-audit-2026`) |
| Baseline DB | `eduvark_rb_pre002` — schema **001 saja** + Admin legacy `login_id='admin'` (clone dari DB uji pra-002) |
| DB restore | `eduvark_rb_pre002_restored` (DB bersih, hasil `pg_restore`) |
| Backend lama | image b7b6cd6, `/tmp/eduvark-oldbackend/backend/dist/server.js`, **PORT=3995** |
| Backend baru | kode working tree, `backend/dist/server.js`, **PORT=3996** |
| Migration runner | `backend/dist/db/migrate.js` (+ `dist/db/migrations/00{1,2,3}*.sql`) |
| Storage materi | `LOCAL_STORAGE_DIR=/tmp/eduvark-restore-storage` |
| Snapshot | `baseline/pre-002-snapshot.dump` (`pg_dump -Fc`), sha256 `5aedcca25b15c25742d5110484ed8a351cc8388d43789a74c2eb2983e066414c`, 46.741 B |
| APK lama | sha256 `9b5756a08f7479e727155d17e3554fb279bdc1321cbd3e69edba4f29eaf6a113` (b7b6cd6) |
| APK baru | sha256 `164912fa239f9862f071d6436140a9b0323a06fd90fb6cf1c1e2d56f70f3f59f` |
| Port terlarang | 3998 / PID 13363 (agen lain) **tidak disentuh**; server uji sendiri di 3995/3996 |

Fixture (dari DB uji): kelas `dd760d1b-…`, dosen `1990010001` (`aa09b48b-…`), siswa `2025000001`
(`f3da579a-…`) & `2025000002` (`d6c373cd-…`), Admin UUID `c1bf7114-67f1-4296-87fa-5098e8123fc4`,
topik `a50f237b-…` (PUBLISHED), materi FILE `81e9de87-…` (PDF stub 51 B), submission `b09114be-…`,
assessment score 92. Password fixture: `[REDACTED]`.

---

## 2. Status ringkas

| # | Pemeriksaan | Status |
|---|---|---|
| 1 | Baseline schema 001 + Admin legacy `admin` bisa login | **PASS** (HTTP 200) |
| 2 | Baseline: login numerik `01401400001` ditolak | **PASS** (HTTP 401) |
| 3 | Snapshot pra-002 (`pg_dump -Fc`) + checksum | **PASS** |
| 4 | Migrasi 002 + 003 diterapkan (`appliedCount:2`, `schema_migrations`=3 baris) | **PASS** |
| 5 | Identitas Admin: UUID & relasi akademik utuh; hanya `login_id` berubah | **PASS** |
| 6 | Sesi Admin dicabut oleh 002 (0 sesi aktif) | **PASS** |
| 7 | Backend baru: login numerik 200, legacy 401 | **PASS** |
| 8 | Backend baru: kontrak materi baru (`contentUrl`, `signedUrl:null`) + `GET /materials/:id/content` 200 (51 B, sha256 cocok) | **PASS** |
| 9 | Backend baru: Analytics bentuk baru 200 (memuat `definitions`) | **PASS** |
| 10 | Restore snapshot ke DB bersih: row count & struktur = snapshot | **PASS** |
| 11 | Backend lama di DB hasil restore: health 200, login `admin` 200, numerik 401 | **PASS** |
| 12 | Backend lama: materi terbuka via `signedUrl` → `GET /material-files` 200, 51 B, sha256 `90463624…` | **PASS** |
| 13 | Backend lama: endpoint `/content` (kontrak baru) 404 — sesuai kontrak lama | **PASS** (expected) |
| 14 | Backend lama: Analytics bentuk lama 200, **byte-identik** dengan baseline | **PASS** |
| 15 | Struktur & relasi akademik pasca-restore identik dengan baseline (semua tabel) | **PASS** |
| 16 | Tulisan baru pasca-migrasi hilang setelah restore (dikuantifikasi) | **PASS** |
| 17 | Rollback sebelum tulisan baru (skenario A): 0 baris data produk hilang | **PASS** |
| 18 | Rollback sesudah tulisan baru (skenario B): 6 baris produk + artefak migrasi hilang | **PASS** (perilaku sesuai snapshot) |
| 19 | Real-screen APK lama × backend lama di emulator pasca-rollback | **NOT RUN** (alasan §6) |
| 20 | Render PDF asli / berkas R2 produksi | **NOT RUN** (stub 51 B; R2 produksi di luar lingkup) |
| 21 | Restore & rollback di staging/produksi | **NOT RUN** (tidak disentuh) |
| 22 | Status sesi/token lama setelah restore di lingkungan nyata | **NEEDS VERIFICATION** (§5.3) |

Legend: **PASS** / **FAIL** / **NOT RUN** / **NEEDS VERIFICATION**. Semua PASS di atas adalah bukti
lokal pada DB uji, bukan klaim staging/produksi.

---

## 3. Langkah yang dieksekusi (perintah kunci)

### 3.1 Baseline + snapshot (pra-002)

```bash
# DB baseline: schema 001 + Admin legacy (clone DB uji pra-002, tanpa 002/003)
createdb eduvark_rb_pre002
pg_dump -d eduvark_test_rollout_old --no-owner --no-privileges | psql -d eduvark_rb_pre002

# bukti baseline (backend lama, PORT=3995, DB=eduvark_rb_pre002)
curl -X POST :3995/api/v1/auth/login -d '{"loginId":"admin","password":"[REDACTED]"}'   # 200
curl -X POST :3995/api/v1/auth/login -d '{"loginId":"01401400001", ...}'                    # 401

# snapshot pra-002
pg_dump -Fc -d eduvark_rb_pre002 -f baseline/pre-002-snapshot.dump
shasum -a 256 baseline/pre-002-snapshot.dump   # 5aedcca2…6414c
```

### 3.2 Migrasi 002/003 + verifikasi backend baru

```bash
# migrasi dijalankan eksplisit oleh runner (server TIDAK auto-migrate)
DATABASE_URL=postgres://…/eduvark_rb_pre002 node dist/db/migrate.js   # appliedCount:2
node dist/server.js                                                   # PORT=3996
```

Hasil: `schema_migrations` = 001, 002, 003; `admin_identity_changes` = (admin →
01401400001); login numerik 200, legacy 401; sesi Admin aktif 0; materi `contentUrl` +
`/content` 200; Analytics 200 bentuk baru.

### 3.3 Tulisan baru pasca-migrasi (skenario B)

Lewat API backend baru (semua 201/204):

| Aksi | ID hasil |
|---|---|
| `POST /admin/users` (siswa baru 2025999999) | user `b24377dd-…`, enrollment `4de21a9c-…` |
| `POST /auth/change-password` | — (204) |
| `POST /lecturer/topics` | topik `284f3e09-…` |
| `POST /lecturer/topics/:id/materials/link` | materi `ec7e9b09-…` |
| `POST /topics/:id/case/submission` (sesi KINESTHETIC) | submission `f4482b6e-…` |
| `POST /submissions/:id/assessment` (score 88) | assessment `03a6a3f8-…` |

### 3.4 Restore snapshot ke DB bersih + verifikasi backend lama

```bash
createdb eduvark_rb_pre002_restored
pg_restore --no-owner --no-privileges -d eduvark_rb_pre002_restored baseline/pre-002-snapshot.dump
# backend lama pada DB hasil restore (PORT=3995)
```

Hasil: `schema_migrations`=001; `login_id` Admin kembali `admin`; login `admin` 200, numerik 401;
materi via `signedUrl` 200 (51 B, sha256 `90463624…`); `/content` 404; Analytics 200 identik
dengan baseline; seluruh struktur/relasi identik dengan baseline; semua ID tulisan baru **absent**.

---

## 4. Dua skenario rollback & kuantifikasi kehilangan data

Snapshot pra-002 disimpan **sebelum** migrasi dan sebelum tulisan baru. Karena itu:

### Skenario A — rollback **sebelum** ada tulisan baru
- Baris data produk yang hilang: **0** (users/classes/enrollments/topics/case_studies/materials/
  submissions/assessments/teaching_assignments semuanya identik dengan baseline).
- Yang ikut hilang: **1 baris** `admin_identity_changes` (jejak audit penugasan identitas) dan
  efek 002/003 (login_id numerik, revokasi sesi). Login Admin kembali ke `admin` — ini konsekuensi
  rollback yang diharapkan, bukan kehilangan data.
- Relasi akademik & UUID Admin **tidak berubah** oleh 002/003, jadi restore tidak merusak relasi.

### Skenario B — rollback **sesudah** ada tulisan baru
Semua tulisan setelah snapshot **hilang tanpa peringatan** (tidak ada mekanisme merge):

| Tabel | Snapshot pra-002 | Pasca tulisan | Hilang saat restore |
|---|---|---|---|
| users | 4 | 5 | **1** (`b24377dd-…` 2025999999) |
| enrollments | 2 | 3 | **1** (`4de21a9c-…`) |
| topics | 1 | 2 | **1** (`284f3e09-…`) |
| materials | 1 | 2 | **1** (`ec7e9b09-…`) |
| submissions | 1 | 2 | **1** (`f4482b6e-…`) |
| assessments | 1 | 2 | **1** (`03a6a3f8-…`, score 88) |
| auth_sessions | 18 | 34 | 16 (termasuk trafik login verifikasi) |
| audit_logs | 30 | 57 | 27 (termasuk audit aksi verifikasi) |
| admin_identity_changes | 0 | 1 | 1 |

Jadi **6 baris data produk** (user+enrollment, topik, materi, submission, assessment) + jejak
audit/hitungan sesi hilang pada skenario B. Ini harus menjadi peringatan rollback di runbook:
restore snapshot = **titik waktu**, semua perubahan sesudahnya dibuang.

---

## 5. Temuan penting untuk operasional

1. **Migrasi tidak otomatis di server.** `node dist/server.js` hanya start; 002/003 harus dijalankan
   eksplisit (`node dist/db/migrate.js` / `npm run db:migrate`). Deployment/rollback harus
   memastikan langkah migrasi ini ada sebelum backend baru melayani trafik.
2. **Image lama pada DB 002/003 bukan rollback yang sah.** Materi/Analytics memakai kontrak baru;
   APK lama gagal membuka materi (lihat `../compat-e2e/rollback-image-without-db-restore.json`).
   Rollback utuh wajib restore snapshot pra-002 (atau DB pulih pra-002).
3. **Restore mengembalikan status sesi/token.** Snapshot membawa `auth_sessions` pra-002; revokasi
   yang dilakukan migrasi 002 ikut hilang. Di fixture ini 0 sesi Admin aktif di snapshot, tetapi
   secara umum token yang valid saat snapshot dapat kembali aktif → perlu revoke/rotate sesi setelah
   rollback (atau pastikan snapshot diambil saat sesi sudah tidak valid).
4. **Snapshot harus diambil sebelum migrasi** dan disimpan beserta checksum; restore harus ke DB
   bersih (jangan restore di atas DB 002/003).

---

## 6. Keterbatasan / yang TIDAK diklaim

- **Real-screen APK lama × backend lama pasca-rollback: NOT RUN.** Emulator `emulator-5554`
  sedang dipakai proses lain (fokus layar berada di `permissioncontroller`, agen lain di port 3998);
  mengganti APK akan mengganggu pekerjaan agen tersebut. Verifikasi pasangan lama/lama di sini
  adalah **level API/endpoint** yang dikonsumsi APK lama (login Admin legacy, relasi akademik,
  materi via `signedUrl`, Analytics bentuk lama) — bukan tangkapan layar device.
- Materi fixture adalah **PDF stub 51 byte**; render PDF asli di aplikasi **NOT RUN**.
- Tanda tangan URL & storage lokal hanya diuji terhadap `LOCAL_STORAGE_DIR`; perilaku R2 produksi
  (TTL, signature) **NEEDS VERIFICATION** di lingkungan nyata.
- Angka `auth_sessions`/`audit_logs` pasca-tulisan memuat trafik login verifikasi, bukan hanya
  aksi produk; angka produk yang eksplisit tercantum di `new-writes-lost.json`.
- Analytics backend lama bergantung pada sesi VARK aktif (fixture sesi VISUAL/KINESTHETIC);
  perbandingan baseline vs pasca-restore dilakukan dengan urutan langkah identik sehingga
  payload byte-identik.
- Staging/produksi tidak disentuh; tidak ada commit/push/deploy. Server uji sendiri (3995/3996)
  dihentikan setelah pengujian; proses agen lain (PID 13363/port 3998) tidak disentuh.

---

## 7. Indeks artefak

| File | Isi |
|---|---|
| `README.md` | laporan ini |
| `baseline/pre-002-snapshot.dump` | snapshot `pg_dump -Fc` pra-002 (46.741 B) |
| `baseline/pre-002-snapshot.sha256` | checksum snapshot |
| `baseline-verification.json` | bukti baseline: login legacy 200 / numerik 401, kontrak materi lama, signed URL, Analytics, snapshot |
| `baseline-structure.json` | dump struktur/relasi (UUID) pra-002 |
| `post-migration-verification.json` | migrasi 002/003, login baru, revokasi sesi, kontrak materi/Analytics baru |
| `post-migration-structure.json` | struktur pasca-migrasi + tulisan baru |
| `new-writes-created.json` | inventaris ID tulisan baru |
| `new-writes-lost.json` | kuantifikasi kehilangan data skenario A/B + hitungan tabel |
| `restore-verification.json` | restore ke DB bersih + verifikasi backend lama + kesamaan struktur/Analytics |
| `restored-structure.json` | struktur pasca-restore (identik dengan baseline) |
| `logs/rb-migrate.log` | log runner migrasi |
| `logs/rb-oldbackend-baseline.log` | log backend lama pada baseline |
| `logs/rb-newbackend-migrated.log` | log backend baru pasca-migrasi |
| `logs/rb-oldbackend-restored.log` | log backend lama pada DB hasil restore |

Verifikasi cepat JSON: `for f in *.json; do node -e "JSON.parse(require('fs').readFileSync('$f'))" || echo "INVALID $f"; done`
