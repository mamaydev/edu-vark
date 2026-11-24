# Audit simulasi kegagalan deploy — `scripts/eduvark-deploy.sh`

Tanggal audit: 2026-11-24 · Agen: `p1-access` · Task: `task-15`
Mode: simulasi **offline** di macOS. Tidak ada perubahan pada workflow, script deploy,
server homelab, atau deployment aktif. PID 13363/port 3998 tidak disentuh.

## Lingkup

1. Urutan rilis yang diwajibkan runbook (§5): tutup trafik → snapshot → migrasi →
   switch → smoke → reopen.
2. Perilaku saat **migrasi gagal** (service lama harus tetap hidup).
3. Perilaku **auto-rollback image-only** saat smoke gagal (DB tidak direstore, trafik
   tidak ditutup).
4. Perilaku **smoke saat API publik tertutup** (maintenance/deny-all di edge).

File yang diaudit (read-only): `.github/workflows/deploy-homelab.yml`,
`scripts/eduvark-deploy.sh`, `docs/audit/evidence/ROLLOUT-RUNBOOK.md` (§5, §7),
`docs/audit/EXECUTION-REPORT-2026-11-24.md`.

## Metode simulasi

Harness: `run-sim.sh` (di direktori ini). Prinsip:

- Salinan **verbatim** `scripts/eduvark-deploy.sh` (sha256 sumber
  `20bd3369e93c143662ce7f867ae306e400894ee472aff3951368dd5a0f54b924`) dibuat, baris
  terakhir `main "$@"` dibuang, lalu **hanya path absolut** (`REPO_DIR`, `BACKUP_DIR`,
  `STATE_DIR`, `HOST_HEALTH_URL`, `PUBLIC_BASE_URL`) ditulis ulang ke sandbox `/tmp`.
  Body `cmd_deploy`/`rollback_to`/`wait_host_health`/`smoke_tests` tidak diubah.
- Binari istimewa diganti shim yang **merekam semua panggilan** ke `calls/*.log`:
  `docker`, `git`, `curl`, plus `flock`, `date -Is`, `stat -c`, `sed -i`, `sleep`
  (macOS tidak punya GNU tool tersebut). `require_root` dan `check_capacity` di-no-op
  karena harness berjalan non-root; `prune_backups` di-no-op hanya pada skenario sanity
  karena `find -printf` tidak ada di macOS. Semua override tersebut tidak menyentuh
  logika urutan/rollback yang diuji.
- Setiap skenario menjalankan `cmd_deploy` sungguhan di subshell, lalu assertion
  memeriksa exit code, `state/state.json`, `state/deploy.log`, `calls/docker.log`,
  `calls/curl.log`, dan `BACKEND_TAG` di `.env` sandbox.

Reproduksi:

```bash
bash docs/audit/evidence/deploy-sim/run-sim.sh   # exit 0 = 33/33 assertion PASS
```

Ringkasan run terakhir: `sim-output/RUN-OUTPUT.txt`; assertion per skenario:
`sim-output/<skenario>/assertions.txt`.

## Skenario dan hasil aktual

| # | Skenario | Perlakuan shim | Hasil (artefak) | Assertion |
|---|---|---|---|---|
| A | Migrasi gagal | `migrate.js` exit 1 | exit 1; `state.result=failed_migration`; **tidak ada** `up -d`; `BACKEND_TAG` tetap `b7b6cd6d2be1`; log: "migration failed; running service untouched"; backup dibuat sebelum migrasi | **7/7 PASS** |
| B | Smoke gagal, API publik terbuka | `/api/v1/health` publik gagal | exit 1; `state.result=failed_smoke`; dua switch: `d3aed78abcfe` lalu rollback `b7b6cd6d2be1`; log "rollback succeeded"; `BACKEND_TAG` kembali `b7b6cd6d2be1`; **0** restore DB | **11/11 PASS** |
| C | Smoke saat API publik tertutup | `/health/ready` + `/api/v1/health` publik gagal (setara deny-all/maintenance) | exit 1; log: "WARN: public endpoint health check failed"; probe publik 10x; smoke gagal → rollback image-only; `state.result=failed_smoke` | **12/12 PASS** |
| D | Sanity sukses (kontrol harness) | semua endpoint publik OK | exit 0; `state.result=success`; `BACKEND_TAG=d3aed78abcfe`; log "deploy success" | **3/3 PASS** |

Total **33 assertion PASS, 0 FAIL**. Skenario D membuktikan harness tidak selalu
memaksa jalur gagal (kontrol positif).

### Urutan aktual yang tereksekusi (dari log)

```
A: backup → build → MIGRASI (gagal) → [berhenti; service lama tetap jalan]
B/C/D: backup → snapshot(001,002) → build → MIGRASI (001,002,003) → switch(new)
       → host health → public health → smoke → [D: sukses | B/C: rollback image-only → berhenti]
```

Tidak ada satu pun langkah "tutup trafik", "maintenance/deny-all", atau "reopen trafik"
di log maupun di script (grep `ingress|maintenance|traefik|iptables|cloudflared|deny`
pada `scripts/eduvark-deploy.sh` hanya menemukan komentar "ingress issue?" di baris 343).

## Temuan terhadap kriteria

| # | Kriteria (runbook §5/§7) | Status | Bukti |
|---|---|---|---|
| 1 | Snapshot/backup sebelum migrasi | **PASS** | skenario A: `pg_dump` baris 5 < `migrate.js` baris 9 di `calls/docker.log`; static `cmd_deploy` memanggil `backup_database` lalu `migration_snapshot` |
| 2 | Migrasi selesai (exit 0) sebelum image baru menerima trafik | **PASS** | static + skenario A: migrasi gagal → abort sebelum switch; simulasi tidak punya cabang migrasi-lalu-switch |
| 3 | Migrasi gagal → service lama tetap hidup, image tidak diganti | **PASS** | skenario A: exit 1, tanpa `up -d`, `BACKEND_TAG` tidak berubah, state `failed_migration`, backup path tercatat |
| 4 | **Tutup trafik publik se-belum** migrasi/switch | **FAIL** | tidak ada langkah tersebut di engine; migrasi berjalan saat backend lama masih melayani trafik (log A/B: migrasi sebelum switch) |
| 5 | **Reopen trafik** setelah verifikasi | **FAIL** | tidak ada kode reopen/maintenance-mode di engine |
| 6 | Smoke tests dapat dijalankan saat API publik **tertutup** | **FAIL** | `smoke_tests` memakai `PUBLIC_BASE_URL` (`eduvark-deploy.sh:207-214`); skenario C → smoke gagal dan rollback walaupun backend host sehat. `EDUVARK_SKIP_PUBLIC=1` hanya melewati `check_public_health` (baris 191-195), **bukan** smoke |
| 7 | Kegagalan kuat pada kesehatan publik (rollback/stop) | **FAIL** | `check_public_health` gagal hanya `WARN` lalu deploy diteruskan (`eduvark-deploy.sh:340-344`); pada skenario C deploy baru berhenti karena smoke ikut gagal |
| 8 | Smoke gagal → auto-rollback image | **PASS** | skenario B/C: switch baru → smoke gagal → `rollback_to` → `up -d` image lama → "rollback succeeded"; state `failed_smoke` |
| 9 | Rollback **memulihkan database** | **FAIL** (by design) | `cmd_rollback`/`rollback_to` hanya mengganti image tag; log eksplisit "database was NOT restored". Skenario B: migrasi 001,002→003 namun image lama kembali → DB tetap skema baru. Runbook §7 menuntut restore manual (pra-003) — tidak ada di engine |
| 10 | Rollback destruktif (`down -v`, `DROP DATABASE`, volume prune) | **PASS** | tidak ada perintah destruktif; simulasi 0 restore; grep static bersih (komentar aturan di baris 21) |
| 11 | Verifikasi pasangan kompatibel / layar nyata (runbook §5.2 langkah 6) | **FAIL** | `smoke_tests` hanya `GET /api/v1/health` dan ekspektasi `GET /api/v1/topics` = 401; tidak ada pemeriksaan pasangan kompatibel |
| 12 | Concurrency/deploy lock | **PASS** (static) | `flock` + `concurrency: deploy-homelab` (workflow) |
| 13 | Perilaku deploy sungguhan di homelab | **NOT RUN** | simulasi offline saja; server aktif tidak disentuh sesuai instruksi |

## Risiko yang tersisa (bukan status lulus)

1. Karena tidak ada penutupan trafik, migrasi dieksekusi saat backend lama masih
   melayani request; `backend/src/db/migrate.ts` menerapkan per-file dalam transaksi,
   tetapi bila file migrasi ke-N gagal setelah file sebelumnya sukses, DB sudah bergeser
   sebagian sementara engine tetap membiarkan image lama hidup ("running service
   untouched"). Kompatibilitas image lama terhadap skema separuh-baru **NEEDS
   VERIFICATION** per rilis.
2. Bila smoke gagal, rollback hanya mengembalikan image; DB yang sudah dimigrasi tidak
   dikembalikan. Untuk migrasi yang tidak backward-compatible ini bukan rollback penuh
   (sesuai catatan script sendiri); prosedur restore manual di runbook §7 tidak
   diotomasi.
3. Smoke lewat URL publik berarti jendela cutover "tutup trafik → smoke → reopen" yang
   diminta runbook **tidak dapat dieksekusi** oleh engine ini tanpa perubahan (mis.
   smoke ke `HOST_HEALTH_URL`/origin dengan `--resolve`, atau smoke bypass edge).
4. `check_public_health` yang hanya WARN dapat menutupi masalah ingress; keberhasilan
   akhir bergantung pada smoke yang hanya dua request.

## Artefak

| Path | Isi |
|---|---|
| `run-sim.sh` | harness (dapat dijalankan ulang; sha256 `f0f3d11f5678cb14261391cfa13b2939d845239bab673e6fe11edb672d34fee7`) |
| `sim-output/RUN-OUTPUT.txt` | output lengkap run terakhir (33 assertion PASS) |
| `sim-output/<skenario>/assertions.txt` | assertion per skenario (A 7, B 11, C 12, D 3) |
| `sim-output/<skenario>/sandbox/state/deploy.log` | log engine hasil simulasi |
| `sim-output/<skenario>/sandbox/state/state.json` | state JSON hasil simulasi |
| `sim-output/<skenario>/sandbox/calls/*.log` | rekaman panggilan docker/git/curl (bukti urutan & tidak ada restore/traffic-close) |
| `sim-output/<skenario>/sandbox/repo/.env` | `BACKEND_TAG` awal/akhir (bukti rollback) |
| `sim-output/<skenario>/engine.sha256` | hash engine sandbox (identik antar skenario) |

## Keterbatasan

- Simulasi memakai shim; ia membuktikan **alur kontrol engine**, bukan perilaku Docker
  atau jaringan sungguhan. Hasil produksi tetap **NOT RUN** sampai diuji di homelab.
- Harness berjalan non-root dan tanpa akses Docker daemon; `require_root`/`check_capacity`
  di-no-op dan `prune_backups` di-no-op pada skenario D. Bagian tersebut tidak relevan
  dengan pertanyaan urutan/rollback, tetapi bukan bukti bahwa bagian itu benar.
- Tidak ada klaim bahwa release hold aktif; lihat `../release-hold/EVIDENCE.md`.
