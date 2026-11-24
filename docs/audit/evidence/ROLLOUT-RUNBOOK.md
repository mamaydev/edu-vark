# Rollout Runbook — Cutover Backend Baru & APK Kompatibel (EduVARK)

Status dokumen: **satu-satunya acuan rollout** untuk remediasi FR-015/029–033 (C1-02, AT-18–21) dan
pengerasan kesiapan rilis 24 November 2026. Dokumen ini menggantikan §11, §13, §16.4 pada laporan
eksekusi dan P2 §9.5; bila dokumen lama bertentangan, dokumen ini yang dipakai.

- Tanggal verifikasi: **24 November 2026** (Asia/Jakarta).
- Lingkungan bukti: **LOKAL saja**. Emulator `Pixel_10_Pro_XL` API 37 (`emulator-5554`), backend uji
  pada port 3001/3002, DB uji terisolasi. **Tidak ada akses staging/produksi.**
- Status cutover: **NO-GO**. Dua alasan yang tidak boleh diabaikan:
  1. **Release hold belum ada/terbukti** — environment `homelab-testing` hanya punya branch policy,
     `can_admins_bypass: true`, dan **tidak punya required reviewers** (`docs/audit/evidence/release-hold/EVIDENCE.md`).
     Karena workflow deploy berjalan otomatis setelah CI `main` sukses, merge ke `main` hari ini
     **akan** memicu deploy. Sampai hold diaktifkan dan dibuktikan, **merge ke `main` = NO-GO**.
  2. Deployment otomatis **belum** menutup trafik publik, belum memverifikasi penolakan APK lama,
     dan rollback otomatis hanya mengembalikan image (bukan DB). Runbook ini menetapkan kontrol
     manual di luar workflow; perubahan workflow/repo settings **butuh persetujuan terpisah**.

- Artefak yang diuji dan menjadi acuan:

| Artefak | Identitas | Catatan |
|---|---|---|
| APK baseline | `versionCode=1`, `versionName=1.0.0`, SHA-256 `9b5756a08f7479e727155d17e3554fb279bdc1321cbd3e69edba4f29eaf6a113` | dibangun dari `b7b6cd6`; **tidak** mengirim header versi |
| APK kandidat | `versionCode=2`, `versionName=1.1.0`, SHA-256 `fa20e4635762824c194cf832a70c368355d31b4b4919796a7a2956d5b25091ed` | mengirim `X-EduVARK-Client-Version: 2` |
| Backend kandidat | kode working tree (HEAD `b7b6cd6` + perubahan remediasi), schema `001+002+003` | guard `CLIENT_COMPATIBILITY_ENFORCE=1`, `CLIENT_MIN_VERSION=2` |
| Backend lama | `b7b6cd6`, schema `001` | pembanding baseline/rollback |
| Snapshot rollback | `docs/audit/evidence/rollback-pre-002/baseline/pre-002-snapshot.dump` sha256 `5aedcca25b15c25742d5110484ed8a351cc8388d43789a74c2eb2983e066414c` | **pra-002**, bukan pra-003 |

- Hash backend/schema dan daftar screenshot: `docs/audit/evidence/compat-e2e/client-compatibility-artifacts.json`.
- Indeks bukti: §10.

---

## 1. Ringkasan keputusan (baca ini dulu)

1. **Pasangan rilis yang kompatibel: APK kandidat × backend kandidat.** Login, daftar topik/materi,
   ambil isi materi, dan Analytics dosen sudah dijalankan sampai layar nyata (§2, §6).
2. **APK baseline × backend kandidat kini mendapat penolakan terdefinisi.** Backend mengembalikan
   `426 CLIENT_VERSION_UNSUPPORTED` sebelum autentikasi/kontrak data. Di emulator, sesi Admin lama
   yang masih aktif memanggil `/auth/me`, menerima 426, dan layar menampilkan pesan backend
   "Versi aplikasi tidak didukung. Perbarui EduVARK sebelum melanjutkan." (§3).
3. **Kontrol ini menutup celah "klien lama lolos"**, tetapi **bukan** pengganti release hold atau
   penutupan trafik terkendali. APK baseline yang mencoba login/refresh tetap ditolak 426; health
   tetap 200. Tidak ada perubahan aturan autentikasi/otorisasi: guard berjalan sebelum auth dan
   hanya memeriksa header versi.
4. **Release hold BELUM terbukti (FAIL).** Aktifkan hold sebelum merge ke `main`; buktikan deploy
   tertahan. Tanpa itu merge/cutover tetap NO-GO (§7).
5. **Urutan operasional hanya satu** (§5): release hold → tutup/drain trafik → snapshot pra-002 →
   jalankan migrasi sebagai job pra-trafik → start backend kandidat → smoke internal → verifikasi
   APK lama ditolak 426 → keputusan reopen. Migrasi **bukan** otomatis di `server.ts`; deployment
   harus menjalankannya eksplisit sebelum trafik.
6. **Rollback sah = image/backend lama + restore snapshot pra-002**, bukan image lama di atas DB
   yang sudah 002/003. Rollback image saja sudah diuji dan **tidak** memulihkan perilaku lama (§8).
7. **Window URL lama** dihitung dari penerbitan terakhir + TTL maksimum yang terbukti. Backend
   kandidat **tidak lagi menerbitkan `signedUrl`**. TTL produksi/R2 tetap **NEEDS VERIFICATION**;
   TTL lokal terukur 299,349 detik (default config 300 detik). Penolakan klien lama (426) dan umur
   URL lama adalah dua kontrol terpisah (§4).

---

## 2. Matriks kompatibilitas lintas versi (hasil nyata)

Legenda: **PASS** = dijalankan dan berhasil · **FAIL** = dijalankan dan gagal/tidak kompatibel ·
**NOT RUN** = tidak dijalankan · **NEEDS VERIFICATION** = belum terbukti. Lapisan bukti dipisah:
**API** (curl/log server), **real-screen** (emulator + `uiautomator dump` + screenshot),
**unit/ViewModel**, **staging/production** (tidak diuji).

| Kombinasi | Login | Daftar/isi materi | Analytics dosen | Kesimpulan |
|---|---|---|---|---|
| **APK kandidat × backend kandidat** | **PASS** — API 200 (header versi 2), real-screen login mahasiswa & dosen | **PASS** — daftar topik/materi 200; `GET /materials/:id/content` 200; handoff FileProvider ke PDF viewer tercatat, **render PDF asli belum diverifikasi** (stub 51 B) | **PASS** — `/lecturer/analytics/topics/:id` 200; layar menampilkan Visual 75% (n=1), Aural 60% (n=1), R/W — (n=0), Kinesthetic 92,0/100 | pasangan rilis |
| **APK baseline × backend kandidat** | **PASS (kontrol)** — request tanpa header dan versi 1 ditolak **426**; sesi Admin lama yang aktif juga ditolak 426 di `/auth/me`; layar menampilkan pesan penolakan | **FAIL/tertahan** — jalur materi ditolak 426 sebelum data (belum mencapai kontrak lama) | **FAIL/tertahan** — semua API dijaga; tidak ada data lama yang dikirim | **APK lama berhasil ditahan**; ini yang mencegah silent no-op/analytics rusak |
| **APK baseline × backend lama** | **PASS (real-screen + API)** — login Admin `01401400001` 200, beranda admin tampil, screenshot tersimpan | **NOT RUN** | **NOT RUN** | baseline rollback terverifikasi untuk login |
| **APK kandidat × backend lama** | **NOT RUN** untuk artefak kandidat final (hash berbeda dari run lama) | **NOT RUN** | **NOT RUN** | bukan jalur yang didukung |

Catatan penting:
- Bukti API langsung: `docs/audit/evidence/compat-e2e/client-compatibility-api.json`.
- Bukti request server (Authorization hanya dicatat keberadaannya, token tidak disimpan):
  `docs/audit/evidence/compat-e2e/client-compatibility-server-evidence.json`.
- Screenshot/UI dump: `docs/audit/evidence/compat-e2e/real-screen-evidence/`.
- Hasil ini **hanya lokal**. Staging/produksi: **NOT RUN**.

---

## 3. Kontrol kompatibilitas klien (pengganti gate lama)

### 3.1 Desain yang diuji

- APK kandidat mengirim header `X-EduVARK-Client-Version: 2` pada semua request melalui interceptor
  OkHttp (termasuk refresh dan unduhan).
- Backend memasang `requireClientCompatibility` pada `/api/v1` **sebelum** rate limit/auth:
  - header hilang, bukan angka, atau `< CLIENT_MIN_VERSION` → `426 CLIENT_VERSION_UNSUPPORTED`;
  - `/health` dan `/health/ready` dikecualikan agar probe tetap bekerja;
  - `OPTIONS` dikecualikan;
  - `NODE_ENV=production` menegakkan guard secara default; compose homelab/production juga
    menetapkan `CLIENT_COMPATIBILITY_ENFORCE=1`, `CLIENT_MIN_VERSION=2`.
- Guard **tidak** mengubah autentikasi/otorisasi. Ia hanya menolak lebih awal berdasarkan versi;
  token lama tetap tidak bisa dipakai tanpa header (diuji), dan health tetap 200.

### 3.2 Bukti API

| Uji | Hasil |
|---|---|
| `GET /health` tanpa header | **200** |
| `POST /auth/login` tanpa header | **426** `CLIENT_VERSION_UNSUPPORTED` |
| `POST /auth/login` dengan versi `1` | **426** |
| `POST /auth/login` dengan versi `2` | **200** |
| Token valid + tanpa header (`GET /topics`) | **426** |
| Token valid + versi 2, setelah pilih VARK | **200** |
| `GET /materials/:id/content` versi 2 | **200** |
| Enam tes integrasi guard | **PASS** (`backend/tests/integration/client-compatibility.test.ts`) |

### 3.3 Bukti emulator

1. APK baseline login ke backend lama → beranda admin; screenshot
   `old-apk-active-session-before-switch.png`; log backend lama mencatat login/me/admin-users 200.
2. Tanpa logout, arahkan `adb reverse` ke backend kandidat dan buka ulang layar:
   `/api/v1/auth/me` → **426**; layar menampilkan pesan penolakan; screenshot
   `old-apk-active-session-blocked-426.png` + UI dump `.xml`. Ini membuktikan sesi lama yang
   **sudah aktif** pun ditahan, bukan hanya login baru.
3. Install APK kandidat (hash di atas), login mahasiswa → pilih VARK Visual → daftar topik/materi →
   buka materi; lalu login dosen → Analytics. Screenshot:
   `candidate-apk-materials.png`, `candidate-apk-material-open.png`, `candidate-apk-analytics.png`.

### 3.4 Batas yang harus dinyatakan jujur

- Kontrol ini menolak klien yang **tidak** mengirim header versi. APK yang dimodifikasi untuk
  memalsukan header berada di luar model ancaman; bukan kontrol anti-tamper.
- Ia **tidak** menarik APK lama dari perangkat. Setelah reopen, APK lama tetap ada tetapi tidak
  bisa memakai API (426). Ini kontrol server-side, bukan paksa update.
- Ia **tidak** membuktikan APK kandidat adalah build yang beredar di toko; hash artefak yang diuji
  dicatat di atas. Build produksi harus memakai hash yang sama atau diulang smoke pada artefak final.
- FileProvider handoff berhasil membuka PDF viewer, tetapi **render PDF/audio/video nyata bukan
  objek produksi** → layar nyata media tetap **NEEDS VERIFICATION**.

---

## 4. Window URL lama (signed URL) — kontrol terpisah dari penolakan klien

### 4.1 Fakta terverifikasi (lokal)

- Backend kandidat **tidak menerbitkan** `signedUrl`: respons materi berisi `url: null`,
  `signedUrl: null`, `expiresAt: null`, dan `contentUrl` tetap.
- Route lama `GET /api/v1/material-files?key=...&expires=...&signature=...` masih ada hanya untuk
  tautan yang **sudah terbit**; tanda tangan kedaluwarsa ditolak **403 `SIGNED_URL_EXPIRED`**.
- Penerbitan terakhir sebelum switch dan TTL terukur tercatat di
  `docs/audit/evidence/compat-e2e/old-signed-url-ttl.json` (signature sudah diredaksi):
  `computedTtlSeconds=299,349`, HTTP 200 saat terbit. Ini bukti **lokal**, bukan R2 produksi.
- Tanpa header versi, route lama juga terkena guard 426. Itu penolakan klien, **bukan** bukti
  kedaluwarsa; karena itu uji kedaluwarsa di atas dilakukan dengan header versi 2 agar mencapai
  pemeriksaan signature.

### 4.2 Aturan window (wajib dipakai saat cutover)

1. Catat `t_last_issue` = waktu penerbitan URL lama terakhir sebelum backend kandidat melayani.
   Bila tidak dapat dibuktikan per-URL, pakai batas konservatif: **waktu backend lama berhenti
   melayani** sebagai `t_last_issue`.
2. `T_max` = TTL maksimum yang **terbukti** pada konfigurasi produksi. Default kode 300 detik
   **bukan** bukti produksi; nilai R2/production tetap **NEEDS VERIFICATION**.
3. Jendela transisi = `t_last_issue + T_max`. Setelah itu, URL lama harus ditolak (403/404) dan
   tidak boleh ada penerbitan baru. Simpan bukti status HTTP + waktu.
4. Jangan mencampur jendela URL dengan reopen API. Reopen API boleh lebih awal **hanya** bila
   kontrol klien (426) sudah aktif dan verifikasi §5.3 lulus; URL lama tetap mati menurut
   `t_last_issue + T_max`.

---

## 5. Urutan cutover yang benar (coordinated maintenance)

### 5.1 Pra-kondisi (harus selesai sebelum jendela maintenance)

- [ ] **Release hold aktif dan terbukti** (§7). Tanpa ini jangan merge ke `main`.
- [ ] APK kandidat final sudah dibangun dan hash-nya dicatat; smoke diulang pada artefak final.
- [ ] Snapshot pra-002 diambil dari DB produksi **sebelum migrasi 002** dengan `pg_dump -Fc`,
      checksum disimpan, dan **diuji restore ke DB bersih** di lingkungan terpisah.
- [ ] Salinan image backend lama tersedia dan dapat dijalankan.
- [ ] Akses edge/load balancer untuk **menutup total** `/api/v1` (deny-all) tersedia dan diuji;
      health endpoint tetap bisa diakses operator.
- [ ] Smoke internal ke backend kandidat disiapkan (lihat §5.2) tanpa melewati edge publik.
- [ ] Rencana komunikasi downtime dan penanggung jawab tiap langkah dicatat.

### 5.2 Langkah cutover (urut, jangan dibalik)

1. **Aktifkan release hold.** Buktikan deploy yang dipicu CI `main` tertahan. Catat siapa yang
   mengaktifkan, waktu, cara, dan bukti (screenshot/API environment protection). Bila tidak bisa
   dibuktikan: **STOP**, merge/cutover NO-GO.
2. **Tutup trafik publik** di edge: semua `/api/v1/*` ditolak (maintenance/deny), kecuali jalur
   operator internal. Catat `t_close`. Ini terjadi **di luar** workflow deploy; workflow tidak
   melakukannya.
3. **Drain** koneksi/pekerjaan tulis yang sedang berjalan; pastikan tidak ada penulisan baru.
   Hentikan proses yang menulis DB (termasuk backend lama) sebelum snapshot.
4. **Ambil snapshot pra-002** dari DB bersih/titik henti, simpan checksum dan identitas artefak
   (image/tag backend lama, APK lama). Snapshot **harus** skema sebelum migrasi yang mengubah
   identitas Admin (pra-002), bukan pra-003.
5. **Jalankan migrasi 001–003 sebagai job pra-trafik** (contoh: `node dist/db/migrate.js` dengan
   image kandidat). Jangan menyalakan backend kandidat sebelum migrasi sukses. Migrasi bukan
   otomatis di `server.ts`.
6. **Start backend kandidat** pada origin internal (bukan publik), dengan env guard produksi.
   Catat versi/image, schema yang terpasang, dan waktu start.
7. **Smoke internal** ke origin kandidat:
   - health 200 tanpa header;
   - login APK/probe kandidat dengan header versi 2 → 200;
   - request tanpa header/versi 1 → 426;
   - materi/content + Analytics untuk akun uji;
   - **bukan** lewat `PUBLIC_BASE_URL`; gunakan port internal/`Host`/IP kandidat. Bila smoke harus
     lewat edge, buka **allowlist operator** sementara yang hanya meneruskan ke origin kandidat,
     lalu tutup kembali. Cara ini harus tercatat dan tidak membuka trafik umum.
8. **Verifikasi APK baseline ditolak** pada backend kandidat: sesi lama → `/auth/me` 426, login →
   426; pastikan layar menampilkan penolakan. Simpan screenshot/UI dump/log.
9. **Keputusan reopen** hanya bila §5.3 lulus. Bila gagal: tetap tutup trafik, jalankan rollback
   §8, jangan membuka publik.

### 5.3 Syarat pembukaan kembali (reopen)

Semua harus benar; health/rollback sukses saja **tidak cukup**:

- [ ] Smoke internal §5.2 lulus dan tercatat (API + layar untuk pasangan kandidat).
- [ ] APK baseline menerima 426 terdefinisi pada sesi aktif dan login baru.
- [ ] Tidak ada error health/ready; backend kandidat stabil selama jendela observasi.
- [ ] Migrasi sukses dan schema sesuai target (`001+002+003`), backup/snapshot tervalidasi.
- [ ] Tidak ada penulisan liar selama maintenance; drain selesai.
- [ ] Release hold tetap aktif sampai keputusan reopen dicatat; bukti hold disimpan.
- [ ] Rencana rollback siap dan snapshot pra-002 dapat direstore.
- [ ] Persetujuan Owner untuk reopen/cutover produksi dicatat.

Bila salah satu gagal: **jangan reopen**. Pilih rollback atau perpanjang maintenance.

### 5.4 Yang dilarang

- Menyalakan backend kandidat sebelum migrasi sukses.
- Mengandalkan tombol rollback image saja sebagai rollback penuh.
- Membuka trafik publik sebelum verifikasi APK lama ditolak.
- Menganggap smoke yang memakai URL publik sebagai bukti backend kandidat (bisa mengenai backend
  lama atau edge cache).
- Merge ke `main` saat release hold belum terbukti.

---

## 6. Verifikasi pasangan APK/API kompatibel (checklist yang sudah dijalankan)

| # | Cek | Bukti | Status |
|---|---|---|---|
| 1 | APK kandidat hash/versi sesuai artefak | badging + `client-compatibility-artifacts.json` | **PASS** |
| 2 | Login kandidat mengirim header versi 2 | log server, `x-eduvark-client-version: 2` | **PASS** |
| 3 | Login kandidat 200 dan masuk beranda | screenshot/log | **PASS** (real-screen) |
| 4 | Materi kandidat: daftar + content 200 | log/screenshot | **PASS** (real-screen; render media **NEEDS VERIFICATION**) |
| 5 | Analytics kandidat: data V/A/R + Kinesthetic tampil | log/screenshot | **PASS** (real-screen) |
| 6 | APK baseline ditolak 426 (login/me) | log + screenshot + UI dump | **PASS** |
| 7 | Health tetap 200 tanpa header | API | **PASS** |
| 8 | Auth/otorisasi tidak berubah | tes guard + tes auth existing | **PASS** (lokal) |
| 9 | Migration job pra-trafik | simulasi/deploy evidence | **PASS** (lokal); produksi **NOT RUN** |
| 10 | Staging/production | — | **NOT RUN** |

Catatan: build APK kandidat berbeda dari hash yang pernah dilaporkan sebelumnya; semua smoke di
atas dijalankan pada hash **`fa20e463…`**. Build final wajib diulang bila hash berubah.

---

## 7. Release hold — status FAIL dan cara membuktikannya

Fakta audit (`docs/audit/evidence/release-hold/EVIDENCE.md`, JSON pendukung):

- Environment `homelab-testing` memiliki `can_admins_bypass: true`.
- Hanya branch policy yang menjadi proteksi; **tidak ada required reviewers**.
- Karena workflow `deploy-homelab.yml` dipicu `workflow_run` setelah CI `main` sukses, merge ke
  `main` akan langsung memicu deploy. **Tidak ada mekanisme hold yang terbukti.**

### 7.1 Apa yang harus dilakukan (butuh persetujuan terpisah)

Perubahan workflow/repo settings **di luar izin saat ini**. Opsi yang harus disetujui Owner:

1. Tambahkan **required reviewers** pada environment `homelab-testing` dan matikan
   `can_admins_bypass`; aktifkan environment protection untuk branch `main`.
2. Atau tambahkan job/gate `hold` eksplisit di workflow yang menunggu approval, dengan bukti
   deploy tidak berjalan sampai disetujui.
3. Siapa pun yang berwenang menyetujui harus dicatat; jangan memakai akun admin yang bisa bypass.

### 7.2 Cara membuktikan hold (tanpa merge ke `main`)

Pemegang: **repo admin / pemilik environment `homelab-testing`** (bukan pemilik aplikasi).
Butuh persetujuan terpisah sebelum mengubah setting.

1. Aktifkan required reviewers pada environment `homelab-testing` (minimal satu reviewer
   non-penulis) dan set `can_admins_bypass: false`. Alternatif: tambahkan approval gate
   eksplisit di workflow deploy.
2. Verifikasi konfigurasi:
   `gh api repos/<owner>/<repo>/environments/homelab-testing` → `protection_rules` memuat
   `required_reviewers`, dan `can_admins_bypass` bernilai `false`.
3. Buktikan deploy tertahan **tanpa merge baru**: re-run workflow deploy terakhir yang sudah
   sukses (atau jalankan workflow dengan input hold bila tersedia); pastikan run berhenti di
   status menunggu persetujuan dan **tidak** ada job deploy yang berjalan di self-hosted.
   Catat `run_id`, waktu, dan screenshot/status.
4. Baru setelah bukti itu ada, merge ke `main` boleh dipertimbangkan (tetap butuh approval
   cutover). Bila re-run tidak diizinkan, hold harus dibuktikan dengan cara lain yang
   disetujui dan dicatat; jangan menganggap setting sebagai bukti tanpa observasi run.

### 7.3 Bukti yang wajib disimpan saat hold diaktifkan/dilepas

- Siapa yang mengaktifkan, waktu, dan perubahan yang dilakukan.
- Screenshot/API environment protection sesudah perubahan.
- Bukti deploy **tertahan**: workflow run berstatus menunggu/tidak ada job deploy; bandingkan
  dengan run `main` sebelum hold.
- Waktu hold dilepas, siapa yang melepas, dan approval cutover yang mendahuluinya.

**Sampai §7.1–§7.3 terbukti: merge ke `main` dan cutover produksi NO-GO.** Jangan mengubah
workflow/repo settings tanpa persetujuan terpisah.

---

## 8. Rollback — satu rencana (APK + image + restore DB pra-002)

### 8.1 Komponen rollback

- **Image/backend lama** (`b7b6cd6`) dan **APK lama** (hash baseline).
- **Snapshot DB pra-002** (bukan pra-003), diambil sebelum migrasi 002.
- Trafik **tetap ditutup** selama rollback; reopen hanya setelah verifikasi pasangan lama/lama.

### 8.2 Urutan rollback

1. **Pertahankan trafik publik tertutup.** Jangan reopen saat rollback berjalan.
2. Hentikan backend kandidat dan pekerjaan tulis.
3. **Restore snapshot pra-002 ke DB bersih** (jangan restore di atas DB 002/003), verifikasi
   checksum dan struktur.
4. Jalankan backend lama terhadap DB hasil restore; verifikasi:
   - login Admin legacy `admin` / akun uji sesuai baseline;
   - relasi UUID akademik (dosen–kelas–topik–materi);
   - materi dapat dibuka lewat kontrak lama;
   - Analytics bentuk lama.
5. **Revoke/rotate sesi** yang mungkin ikut kembali dari snapshot (snapshot membawa `auth_sessions`
   pra-002). Pastikan token lama tidak valid setelah rollback.
6. Reopen hanya setelah verifikasi pasangan lama/lama dan persetujuan Owner.

### 8.3 Bukti lokal

`docs/audit/evidence/rollback-pre-002/README.md` — simulasi penuh:
baseline schema 001 + Admin legacy → snapshot pra-002 → migrasi 002/003 → verifikasi backend baru →
restore ke DB bersih → verifikasi backend lama. Semua pemeriksaan lokal **PASS**, dengan batas:
real-screen APK lama × backend lama pasca-restore **NOT RUN** (verifikasi di level API/endpoint);
media PDF asli dan R2 **NEEDS VERIFICATION**.

### 8.4 Rollback image saja TIDAK cukup

`docs/audit/evidence/compat-e2e/rollback-image-without-db-restore.json` (sebelumnya dijalankan):
image lama di atas DB 002/003 **tidak** mengembalikan perilaku lama:
login Admin legacy 401, FILE APK baru 404. Karena itu:

- rollback penuh = image lama + **restore DB pra-002**;
- rollback image-only hanya darurat untuk menghentikan backend kandidat, bukan pemulihan perilaku;
- auto image-rollback di deploy script **tidak** merestore DB dan **tidak** membuka/menutup trafik.

### 8.5 Data loss: sebelum vs sesudah reopen

- **Rollback sebelum reopen / sebelum tulisan baru**: snapshot = titik henti; tidak ada data produk
  baru yang hilang (selain sesi/audit yang memang dibuang). Ini jalur yang direncanakan.
- **Rollback sesudah ada tulisan baru**: restore membuang semua perubahan setelah snapshot.
  Simulasi lokal mencatat 6 baris data produk (user+enrollment, topik, materi, submission,
  assessment) plus jejak audit/sesi hilang. Angka persis: `new-writes-lost.json`. Ini harus
  disetujui Owner; jangan klaim rollback tanpa kehilangan data.

### 8.6 Tabel keputusan

| Skenario | Tindakan | Hasil |
|---|---|---|
| Migrasi gagal sebelum trafik | hentikan, jangan start kandidat; restore snapshot bila perlu | aman; belum ada tulisan baru |
| Backend kandidat gagal health/smoke | tutup trafik, rollback image + restore DB pra-002 | perilaku lama kembali |
| Gagal setelah tulisan baru | tutup trafik, restore snapshot | **kehilangan data** pasca-snapshot; butuh approval |
| Hanya image yang di-rollback | image lama di DB 002/003 | **bukan rollback sah**; perilaku lama tidak kembali |
| APK lama masih terpasang pasca-reopen | server menolak 426 | aman dari sisi API; APK lama tetap di perangkat |

---

## 9. Deployment otomatis vs runbook (temuan dan penyesuaian)

Fakta dari workflow/scripts:

- `.github/workflows/deploy-homelab.yml` berjalan setelah CI `main` sukses dan men-deploy ke
  environment `homelab-testing` (self-hosted). Tidak ada gate manual yang terbukti.
- `scripts/eduvark-deploy.sh`: lock → cek env → backup (`pg_dump`) → **snapshot migrasi** →
  build image → `node dist/db/migrate.js` → switch image → health → smoke (memakai URL publik) →
  sukses/rollback. Pada kegagalan switch/health/smoke, `rollback_to` hanya mengembalikan **image**.
- **Tidak ada** langkah menutup trafik publik, drain, snapshot pra-002 eksplisit, smoke internal
  kandidat, verifikasi 426 APK lama, atau reopen. Public health hanya warning (tidak menggagalkan).
- `server.ts` hanya `app.listen`; migrasi harus dijalankan eksplisit (sudah ada di script, tetapi
  tetap harus dipahami sebagai job pra-trafik).

Konsekuensi:

1. Runbook §5 **wajib** dijalankan manual di luar workflow. Workflow tidak boleh dianggap sudah
   memenuhi urutan ini.
2. Bila script auto-rollback setelah migrasi, DB sudah 002/003 dan image lama dijalankan di atasnya
   → bukan rollback penuh. Operator harus tetap menutup trafik dan melakukan restore snapshot
   pra-002 sesuai §8.
3. Smoke script yang memakai `PUBLIC_BASE_URL` **tidak** membuktikan kandidat saat publik ditutup.
   Gunakan origin internal/kandidat (§5.2 langkah 7).
4. Perubahan workflow untuk menambah hold/traffic gate/smoke internal **butuh persetujuan terpisah**
   dan tidak dilakukan dalam remediasi ini.

Bukti simulasi lokal (tanpa menyentuh workflow/server aktif):
`docs/audit/evidence/deploy-sim/EVIDENCE.md` — urutan deploy/migrasi/rollback dan kegagalan
migrasi diuji dengan shim; 33/33 assertion PASS untuk skenario simulasi. Ini bukti **alur kontrol**,
bukan bukti Docker/jaringan produksi. Kegagalan migrasi: backend lama tetap berjalan, image tidak
switch. Auto image-rollback: image lama kembali, **DB tidak direstore**, trafik tetap tertutup
secara manual.

---

## 10. Indeks bukti

| File/folder | Isi |
|---|---|
| `compat-e2e/client-compatibility-api.json` | uji API guard 426/200, token tanpa header, expiry 403 |
| `compat-e2e/client-compatibility-server-evidence.json` | log request server yang disanitasi (tanpa token) |
| `compat-e2e/client-compatibility-artifacts.json` | hash APK/backend, schema, screenshot, window TTL |
| `compat-e2e/real-screen-evidence/` | screenshot + UI dump APK lama tertahan dan APK kandidat |
| `compat-e2e/old-signed-url-ttl.json` | penerbitan/TTL URL lama (signature diredaksi), produksi NV |
| `compat-e2e/rollback-image-without-db-restore.json` | bukti image-only bukan rollback penuh |
| `rollback-pre-002/` | snapshot, restore, verifikasi pasangan lama, data loss |
| `release-hold/EVIDENCE.md` | hold FAIL: tanpa required reviewers, admin bypass |
| `deploy-sim/EVIDENCE.md` | simulasi urutan deploy, auto-rollback image, migrasi gagal |
| `at-corrections/` | koreksi AT-04/AT-17 (layer bukti) |
| `sanitization-report.md` | sanitasi signature/token di evidence |

---

## 11. Satu halaman ringkas untuk pelaksana

1. **Aktifkan dan buktikan release hold** (§7). Tanpa bukti: STOP, merge NO-GO.
2. **Tutup total trafik publik**; catat `t_close`; drain; hentikan penulis.
3. **Snapshot DB pra-002** + checksum; simpan identitas image/APK lama.
4. **Jalankan migrasi 001–003** sebagai job pra-trafik; jangan start kandidat sebelum sukses.
5. **Start backend kandidat di origin internal**; smoke internal (health, login header 2, materi,
   Analytics, 426 tanpa header).
6. **Verifikasi APK baseline 426** pada sesi lama dan login baru; simpan screenshot/log.
7. **Reopen hanya bila §5.3 lulus**; health/rollback sukses saja tidak cukup.
8. **Window URL lama** = `t_last_issue + T_max` (TTL produksi **NEEDS VERIFICATION**); backend
   kandidat tidak menerbitkan URL baru; buktikan 403 setelah window.
9. Bila gagal: **tutup trafik**, rollback = image lama + **restore snapshot pra-002** + revoke
   sesi + verifikasi pasangan lama/lama; image-only bukan rollback penuh.
10. Catat persetujuan Owner untuk reopen/cutover; staging/produksi tetap **NOT RUN** sampai diuji.
