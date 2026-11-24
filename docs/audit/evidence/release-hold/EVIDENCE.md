# Audit release hold — environment `homelab-testing`

Tanggal audit: 2026-11-24 (Asia/Jakarta) · Agen: `p1-access` · Task: `task-15`
Mode: **read-only** (hanya `gh api` GET; tidak ada perubahan workflow, environment, atau server).

## Pertanyaan audit

Apakah rilis ke homelab benar-benar ditahan oleh *release hold* (persetujuan manusia)
sebelum `deploy` berjalan? Bukti yang diminta: konfigurasi GitHub Environment
`homelab-testing` (saat ini dilaporkan hanya `branch_policy` dan
`can_admins_bypass=true`).

## Metode

```bash
gh auth status
gh api repos/mamaydev/edu-vark/environments/homelab-testing
gh api repos/mamaydev/edu-vark/environments/homelab-testing/deployment-branch-policies
gh api repos/mamaydev/edu-vark/actions/workflows/deploy-homelab.yml/runs?per_page=5
gh api 'repos/mamaydev/edu-vark/deployments?environment=homelab-testing&per_page=5'
```

Semua respons mentah disimpan di direktori ini:

| File | Isi |
|---|---|
| `gh-environment.json` | konfigurasi environment `homelab-testing` |
| `gh-branch-policies.json` | deployment branch policy |
| `gh-workflow-runs.json` | 5 run terakhir workflow `deploy-homelab.yml` |
| `gh-deployments.json` | 5 deployment terakhir untuk environment ini |

Ringkasan akun `gh`: login `mamaydev` (aktif), scope token mencakup `repo`/`workflow`
(cukup untuk GET). Tidak ada operasi tulis yang dijalankan.

## Hasil aktual

### 1. Konfigurasi environment (inti release hold)

`gh api repos/mamaydev/edu-vark/environments/homelab-testing` (disalin apa adanya):

```json
{"id":28175169365,...,"name":"homelab-testing",...,"created_at":"2026-11-22T17:56:20Z",
 "updated_at":"2026-11-22T17:56:20Z",
 "can_admins_bypass":true,
 "protection_rules":[{"id":73513718,...,"type":"branch_policy"}],
 "deployment_branch_policy":{"protected_branches":false,"custom_branch_policies":true}}
```

Fakta yang dapat dibaca langsung:

- `protection_rules` hanya berisi **satu** entri: `type: "branch_policy"`.
- **Tidak ada** rule `required_reviewers` → tidak ada persetujuan manusia.
- **Tidak ada** rule `wait_timer` → tidak ada jeda penahan.
- `can_admins_bypass: true` → admin dapat melewati aturan environment.
- `deployment_branch_policy.custom_branch_policies = true`, protected branches `false`.

### 2. Branch policy

`gh api .../deployment-branch-policies`:

```json
{"total_count":1,"branch_policies":[{"id":67805628,...,"name":"main","type":"branch"}]}
```

Hanya `main` yang diizinkan — ini pembatas *asal commit*, **bukan** release hold.

### 3. Trigger dan job workflow

Dari `.github/workflows/deploy-homelab.yml` (tidak diubah):

- Trigger otomatis: `workflow_run` untuk workflow `CI` `types: [completed]` dengan
  `branches: [main]` (baris 10–14) — deploy dimulai otomatis begitu CI `main` selesai.
- Trigger manual: `workflow_dispatch` dengan input `sha` + `mode` (baris 15–29).
- Job `deploy` memakai `environment: {name: homelab-testing, url: https://pam.sequein.my.id}`
  (baris 104–111). Karena environment tidak punya `required_reviewers`, job **tidak**
  berhenti untuk approval.

### 4. Riwayat run dan deployment

`gh-workflow-runs.json` — 5 run `deploy-homelab.yml` terakhir semuanya:
`event=workflow_run`, `status=completed`, `conclusion=success`, `head_branch=main`
(2026-11-23T04:54Z … 13:21Z). Tidak ada indikasi run yang menunggu persetujuan.

`gh-deployments.json` — deployment environment `homelab-testing` tercatat (mis. id
`7999885776`, sha `d3aed78…`, `ref=main`, `production_environment=false`,
dibuat oleh `mamaydev`). Ini konsisten dengan deploy otomatis setelah CI, bukan
menunggu hold.

## Status terhadap kriteria

| Kriteria release hold | Status | Bukti |
|---|---|---|
| Environment `homelab-testing` ada dan dipakai job `deploy` | **PASS** | `gh-environment.json`; `deploy-homelab.yml:104-111` |
| Hanya commit dari `main` yang boleh dideploy (branch policy) | **PASS** | `gh-branch-policies.json` (hanya `main`) |
| **Reviewer wajib / approval manusia sebelum deploy** | **FAIL** | `protection_rules` hanya `branch_policy`; tidak ada `required_reviewers` |
| Wait timer penahan rilis | **FAIL** | tidak ada rule `wait_timer` |
| Admin tidak dapat bypass aturan environment | **FAIL** | `can_admins_bypass: true` |
| Deploy butuh aksi manusia (hold), bukan otomatis | **FAIL** | trigger `workflow_run` otomatis + tanpa reviewer; 5 run terakhir `workflow_run` sukses |
| Approval prompt end-to-end saat run berjalan | **NOT RUN** | tidak boleh/di luar scope menjalankan deployment; kesimpulan diambil dari protection rules yang definitif |
| Dokumentasi menyatakan release hold aktif | **FAIL** (dokumen usang) | lihat tabel drift di bawah |

**Kesimpulan: release hold TIDAK aktif.** Environment hanya menegakkan branch policy
`main`; tidak ada reviewer wajib, wait timer, maupun larangan bypass admin. CD berjalan
otomatis dari CI `main`. Karena itu **tidak boleh ada klaim bahwa rilis ditahan oleh
release hold** sampai environment diberi minimal `required_reviewers` (dan idealnya
`can_admins_bypass` dipertimbangkan ulang).

## Drift dokumentasi (read-only; dokumen tidak diubah)

| File:baris | Klaim dokumen | Kenyataan API |
|---|---|---|
| `docs/ci-cd/cicd.md:52-55` | environment `homelab-testing` punya **required reviewer `@mamaydev`**; job `deploy` menunggu review disetujui | tidak ada `required_reviewers`; tidak ada penundaan |
| `docs/ci-cd/cicd.md:99` | "required reviewer + branch policy `main`" | hanya branch policy `main` |
| `docs/ci-cd/deployment.md:6` dan `:41` | "approve the `homelab-testing` environment" sebelum deploy | tidak ada langkah approval (tidak ada reviewer rule) |
| `docs/ci-cd/security.md:25-26` | environment "requires reviewer `@mamaydev` dan hanya menerima deployment dari `main`" | hanya bagian "dari `main`" yang benar |
| `docs/ci-cd/operations.md:203` | log 2026-11-23: "required reviewers removed from `homelab-testing`" | **konsisten** dengan kenyataan API saat ini |

Catatan: `operations.md:203` sudah mencatat penghapusan reviewer; dokumen lain belum
diperbarui. Perbaikan dokumen bukan bagian task ini (dilarang mengubah runbook/laporan).

## Keterbatasan

- Audit hanya memakai endpoint GET; tidak ada perubahan konfigurasi.
- GitHub API tidak menampilkan riwayat approval per deployment di endpoint environment,
  tetapi ketiadaan rule `required_reviewers` sudah cukup untuk menyimpulkan hold tidak ada.
- Tidak ada akses ke UI Actions/Environment selain API; status "menunggu approval" tidak
  akan muncul untuk environment tanpa reviewer.
