# Audit dan cleanup codebase EduVARK

**Tanggal** 25 November 2026 (Asia/Jakarta)  
**Baseline** `4b91f6a2836305043f59507fe3f74b62db55d16c`, branch `codex/release-candidate-2026-11-24`. Remote lokal `origin` menunjuk ke `mamaydev/edu-vark`. Audit menilai checkout ini, termasuk SRS yang sudah direvisi di working tree.

## 1. Ringkasan

Audit menghasilkan **24 kelompok temuan**. Dua cleanup repositori tetap aktif dalam mode konservatif. Enam cleanup source dan tes yang sudah diuji dipisahkan ke patch lokal. Empat kelompok komponen dipertahankan karena pemakaiannya terkonfirmasi, dan dua belas temuan ditahan untuk review lebih lanjut. Temuan yang ditahan terdiri dari tiga risiko tinggi, tujuh risiko sedang, dan dua yang belum memiliki bukti cukup. Satu kelompok dapat mencakup beberapa simbol atau file.

| Paket | Hasil |
|---|---|
| A — cleanup aman | Pertahankan deduplikasi aturan ignore dan cegah direktori kerja simulasi masuk Git. Cleanup source dan mock tersimpan di patch lokal. |
| B — dependency | Nol dependency dihapus. Kandidat Espresso memengaruhi versi runtime instrumentation yang benar-benar dipilih. |
| C — struktur | Kandidat penghapusan jalur health Android tanpa pemanggil tersimpan di patch lokal. Kode aplikasi aktif memakai baseline. |
| D — perubahan sensitif | Rekomendasi saja. Temuan prioritas tertinggi adalah cakupan redaksi URL request pada log backend. |

## 2. Cakupan dan metode

| Area | Titik masuk dan verifikasi penggunaan |
|---|---|
| Android | Satu modul `:app`. Periksa varian debug/release, manifest termasuk overlay debug, `MainActivity`, `EduVarkNavHost`, binding Hilt/KSP, Retrofit, resource ID/EN, serta `test` dan `androidTest`. Cocokkan kandidat dengan Android Lint dan telusuri pemanggil, referensi deklaratif, akses resource dinamis, dan konsumen pengujian. |
| Backend | Telusuri `backend/src/server.ts`, `createApp`, registrasi middleware/router, layanan, CLI, worker, dan jalur test. Cocokkan ekspor kandidat dengan pencarian simbol TypeScript untuk `src` dan `tests` serta referensi repo pada dokumen, build, dan skrip. Periksa manifest, lockfile, instalasi aktual (`npm ls --depth=0`), imports, tools CLI, dan tipe compiler. |
| PostgreSQL | Periksa migrasi 001–005, runner checksum, seed Admin, preflight ID, transaksi, dan helper tes. Terapkan migrasi secara utuh hanya pada container PostgreSQL 17 uji baru yang terikat ke loopback. |
| Infrastruktur | Baca Dockerfile, tiga Compose, CI GitHub-hosted, deploy manual GitHub Actions dan runner self-hosted, skrip shell, systemd, sudoers, template lingkungan, health/readiness, petunjuk Proxmox LXC 10100, Traefik dan Cloudflare Tunnel. Validasi Compose secara statis dan bangun image lokal. |
| Artefak dan keamanan | Inventaris file tracked dan untracked, tinjau fixture/harness pengujian, jalur backup/restore, bukti audit, dan aturan ignore. Periksa credential-like markers pada file tracked tanpa mencetak isinya, batas debug/release, serta konfigurasi redaksi. Pemeriksaan ini tidak mencakup Git history, log atau konfigurasi server aktif, maupun kredensial yang hanya ada di luar repo. |

Working tree sudah berisi perubahan Owner pada SRS, keputusan, diagram, RTM, dan artefak lokal. Audit memisahkan diff cleanup dari perubahan tersebut. Baseline dan hasil cleanup diperiksa oleh reviewer terpisah untuk Android, backend/database, dan infrastruktur. Review akhir atas diff tidak menemukan penghambat; catatan komentar XML kosong diperbaiki sebelum build final.

## 3. Inventaris percobaan cleanup

A01–A06 adalah hasil percobaan historis yang kini disimpan di patch lokal. A07–A08 tetap aktif dalam mode konservatif.

| ID | Kategori / risiko | Lokasi dan simbol | Bukti, dependency, keputusan |
|---|---|---|---|
| A01 | DEAD CODE / LOW | Android `GetHealthUseCase`, `HealthRepository`, `HealthRepositoryImpl`, `HealthStatus`, `RepositoryModule.bindHealthRepository` | Pencarian seluruh source set dan graph Hilt/KSP menemukan binding tanpa peminta. `MainActivity` memanggil `EduVarkApi.health()` langsung dengan DTO Retrofit. Hapus empat file dan binding saja. |
| A02 | DEAD CODE / LOW | `ui/components/Buttons.kt` (`RowChevron`), `Lists.kt` (`KeyValueRow`, `DotMeta`, `InlineChip`, `LetterBadge`, `ClickableRow`), `Fields.kt` (`MetaRow`), `Surfaces.kt` (`Hairline`) | Masing-masing hanya memiliki deklarasi setelah verifikasi aplikasi, dokumen, preview, dan test source set. Semua komponen yang dipanggil layar tetap ada. |
| A03 | DEAD CODE / LOW | Import pasif pada 24 file Kotlin di `presentation` dan `ui/components` | Hapus import eksplisit yang namanya tidak dirujuk oleh badan file. Import operator delegasi Kotlin dan import yang diperlukan kompilasi tetap digunakan. |
| A04 | DEAD CODE / LOW | `res/values*/strings_{admin,lecturer_a,lecturer_b,p3,p4}.xml` dan `res/values/colors.xml` | Lint memberi 10 kandidat ID yang juga tidak muncul di pemanggil, manifest, XML, tes, atau referensi dinamis yang ditinjau. Hapus sembilan pasangan string ID/EN dan satu warna. Lihat daftar simbol di bawah. |
| A05 | DEAD CODE / LOW | `backend/src/lib/errors.ts` (`badRequest`), `rate-limit.ts` (`resetApiRateLimit`), `login-id.ts` (`ADMIN_LOGIN_ID_MESSAGE`), `types/auth.ts` (`VARK_TYPES`), `modules/materials/object-deletions.service.ts` (`describeJob`) | Ekspor hanya dideklarasikan dan paket backend bersifat privat. Hapus helper internal tanpa mengubah middleware rate limit, validasi login, tipe VARK, kebijakan akses maupun worker objek. |
| A06 | TEST ARTIFACT / LOW | Mock privat di `backend/tests/integration/topics.test.ts` | Hapus anggota `getSignedObjectUrl`, `listObjectKeys`, dan `storageMode` yang tidak dipanggil oleh suite ini. Setup, fixture, asersi, dan implementasi storage yang dipakai suite lain tetap tersedia. |
| A07 | REDUNDANT CONFIG / LOW | `.gitignore` | Hapus tujuh aturan yang sudah dicakup `local.properties` dan `.idea/`. Delapan probe root/nested serta env/build tetap menghasilkan keluaran `git check-ignore` yang sama. |
| A08 | TEST ARTIFACT / LOW | `.gitignore`, direktori `docs/audit/evidence/deploy-sim/sim-output/*/sandbox/` | `run-sim.sh` membentuk ulang direktori kerja ini. Aturan berjangkar menutup 78 file generated yang sebelumnya masih terlihat sebagai untracked. Isi di disk, skrip, laporan, checksum, dan keluaran bukti tracked tetap tersedia. |

ID resource yang dihapus terdiri dari `admin_login_id_placeholder`, `admin_login_helper_admin`, `admin_login_helper_default`, `lec_topic_editor_desc_locked`, `lec_analytics_feedback_note`, `lec_assess_error_score_range`, `p3_login_helper_digits`, `p3_login_submit_hint`, `p4_analytics_origin_categories`, dan `color/black`.

**Inventaris percobaan awal:** 47 file repo sebelum dokumen ini, terdiri dari 43 modifikasi dan empat penghapusan file sumber. Seluruh file yang dihapus pada percobaan awal berada di paket health Android pada A01. Dalam mode konservatif, 46 perubahan Android/backend termasuk mock disimpan di patch lokal dan seluruh file terkait kembali sesuai baseline.

## 4. Komponen yang dipertahankan dan utang teknis tersisa

| ID | Kategori / risiko | Lokasi dan bukti | Dampak dan rekomendasi |
|---|---|---|---|
| R01 | REQUIRED CODE | `EduVarkNavHost.kt`, overlay debug, probe health, `app/src/androidTest`, `backend/tests` | Pertahankan route ketiga role dan sumber asli fixture, mocks, unit/integration serta UI tests. Harness `CaptureScreensTest` dan `ReleaseMatrixTest` dirujuk bukti penerimaan dan kompatibilitas. URL emulator adalah konfigurasi debug. Build release memakai HTTPS dan menonaktifkan override server. |
| R02 | REQUIRED CODE | `backend/src/db/migrate.ts`, `db/migrations/001–005`, `seed-admin*.ts`, `preflight-admin-ids.ts`, repositori serta worker materi | Riwayat migrasi divalidasi dengan checksum. Seed dan preflight memiliki pemakai operasi dan tes. Tidak ada tabel atau kolom yang terbukti layak dihapus. Metadata soft delete, riwayat akademik, dan antrean R2 memiliki pemakai. |
| R03 | REQUIRED CODE | `.github/workflows/{ci,deploy-homelab}.yml`, Dockerfile/Compose, `deploy/systemd`, `deploy/sudoers`, skrip backup/runner/deploy | CI dan deploy manual memiliki target, runner, serta izin berbeda. Compose produksi eksternal dan Compose homelab bukan konfigurasi yang ekuivalen. Pertahankan seluruhnya. |
| R04 | REQUIRED CODE | `backend/package.json`, `app/build.gradle.kts`, `gradle/libs.versions.toml`, DTO Analytics | Dependensi runtime, tools, tipe, Hilt/KSP dan Compose test memiliki pemakai langsung atau kebutuhan build. Field kompatibilitas Analytics masih bagian kontrak API dan DTO Android meski total tertentu tidak dirender. |
| D01 | DEBUG CODE / HIGH | `backend/src/lib/logger.ts`, `backend/src/app.ts`, serializer Pino terpasang, `material-files.routes.ts` | Redaksi `req.query` tidak mencakup query yang sudah tergabung di `req.url`. Risiko penyimpanan signature atau token di log jika request membawa kredensial pada query. Temuan statis, belum ada bukti insiden. Minta persetujuan untuk sanitasi URL sebelum pencatatan dan tes dengan penanda sintetis. |
| D02 | TEST ARTIFACT / MEDIUM | `ReleaseMatrixTest.kt`, `CaptureScreensTest.kt`, fixture backend dan job CI | Harness memiliki fallback akun atau kata sandi uji. Fixture CI terpisah dan terpakai. Verifikasi apakah nilai fallback harness pernah dibagikan dengan shared demo, lalu pertimbangkan argumen wajib atau akun per run. Jangan menyalin nilainya ke laporan. |
| D03 | DEBUG CODE / MEDIUM | `app/.../data/remote/SafeApiCall.kt` | Logging debug menyertakan pesan exception. Tinjau data yang bisa terbawa dalam pesan sebelum beralih ke log bertipe tanpa payload. |
| D04 | LEGACY CODE / HIGH | `app.ts` registrasi `materialFilesRouter`, `material-files.routes.ts`, `storage.ts`, `material-access.test.ts` | Jalur signed URL lokal masih terdaftar dan presigner memiliki pemakai tes kompatibilitas. Tahan penghapusan route, presigner, TTL, dan konfigurasi terkait sampai jendela transisi dan konsumen lama diverifikasi. |
| D05 | REDUNDANT CONFIG / MEDIUM | `backend/src/config/env.ts`, template env, middleware/sesi autentikasi, deploy preflight | `REFRESH_TOKEN_TTL` terlihat hanya diparse dan didokumentasikan, sedangkan umur sesi memakai pengaturan sesi absolut. `JWT_REFRESH_SECRET` masih bagian validasi produksi, CI dan preflight. Perubahan memerlukan review autentikasi/deployment. |
| D06 | UNUSED DEPENDENCY / MEDIUM | Pin `espresso-core` di Gradle | `dependencyInsight` memperlihatkan pin 3.7.0 mengalahkan permintaan transitif Compose 3.5.0 pada `debugAndroidTestRuntimeClasspath`. Penghapusan dapat mengubah runtime. Evaluasi sebagai perubahan terpisah dengan tes perangkat. |
| D07 | LEGACY CODE / MEDIUM | `DEPLOY-PROXMOX.md`, `docs/audit/evidence/ROLLOUT-RUNBOOK.md` | Ada petunjuk lama untuk signed URL dan trigger `workflow_run`. Kode sekarang memakai unduhan terotorisasi dan workflow `workflow_dispatch`. Pisahkan instruksi aktif dari catatan historis setelah review runbook. |
| D08 | LEGACY CODE / HIGH | `backend/src/scripts/backlog-object-cleanup.ts`, `verifyAndDeleteCandidate` dan CLI | Penghapusan berdasarkan key mengandalkan namespace yang tidak dipakai ulang, sedangkan lock baris tidak mencakup penulis eksternal. Kegagalan parsial bisa menghasilkan exit sukses dan laporan opsional. Sebelum purge produksi, verifikasi penulis objek/referensi, wajibkan bukti hasil, dan pertimbangkan exit nonzero untuk kegagalan parsial. |
| D09 | UNCERTAIN / MEDIUM | `file-replacements.service.ts`, `storage.ts`, worker dan lease upload | Interleaving PUT yang selesai setelah lease berakhir belum punya bukti pada R2 nyata. Uji di bucket nonproduksi sebelum mengubah mekanisme rekonsiliasi atau lease. |
| D10 | UNCERTAIN / UNKNOWN | `StatusPill`, dua resource `p4_analytics_*_count` | `StatusPill` muncul di dokumen redesign. Dua ID Analytics memiliki relasi historis dengan tes yang mengecek token larangan render. Pertahankan sampai status adopsi desain dan aturan tes diputuskan. |
| D11 | UNCERTAIN / UNKNOWN | `scripts/homelab-readonly-audit.sh`, salinan SRS/diagram, APK dan artefak rollback/audit | Pemakai manual tidak dapat ditentukan hanya lewat pencarian teks. Restore dump dirujuk runbook. Pertahankan artefak dan skrip sampai Owner menetapkan retensi dan lokasi arsip. |
| D12 | DEAD CODE / MEDIUM | `backend/src/middleware/auth.ts`, parameter `res` | Opsi tambahan TypeScript `--noUnusedLocals --noUnusedParameters` mendeteksi parameter yang sama pada source baseline. Gate resmi tidak menggunakan opsi ini. Tinjau dalam perubahan auth terpisah. |

## 5. Verifikasi percobaan awal dan kompatibilitas SRS

| Pemeriksaan | Baseline | Hasil cleanup |
|---|---|---|
| Backend `npm run db:migrate` | PASS, migrasi 001–005 pada PostgreSQL 17 uji baru | Skema identik. Suite juga menjalankan skenario migrasi dan constraint terkait. |
| Backend `npm run typecheck`, `npm run lint`, `npm test`, `npm run build` | PASS, 30 file / 257 tes | PASS, 30 file / 257 tes, tanpa skip. |
| Docker `docker build -t eduvark-cleanup-audit:verified ./backend` | PASS, image baseline lokal | PASS, image final lokal. `docker image inspect` mengonfirmasi user `node`. |
| Android `./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:assembleRelease :app:assembleDebugAndroidTest` | Tugas CI setara PASS, hasil unit dapat dipakai ulang Gradle | PASS, 30 file / 147 unit test. Debug, release, dan APK instrumentation berhasil dibuat. Android Lint lulus dengan peringatan tersisa. |
| Compose, shell, ignore | Konfigurasi ditelusuri dari checkout | PASS untuk tiga Compose (`config -q --no-interpolate --no-env-resolution`), empat skrip (`bash -n`), dan probe aturan ignore. |
| TypeScript dengan dua flag unused tambahan | Parameter middleware auth ada pada baseline | Temuan D12. Gate resmi tetap PASS. |

Regresi backend mencakup login, JWT dan refresh, izin role dan kelas, topik, materi, lifecycle storage dan rekonsiliasi Ganti File, feedback, Kinesthetic, penilaian serta Analytics. Verifikasi storage memakai adapter lokal atau fake sesuai suite. SRS, navigasi, validasi API publik, dan aturan bisnis tetap pada baseline checkout. App probe tetap terhubung langsung ke endpoint health.

### Pengujian perangkat dan batas hasil

- **PASS — Android instrumentation lokal.** AndroidJUnitRunner menjalankan 14 tes pada AVD Pixel_10_Pro_XL, API 37, dengan APK debug dan APK tes hasil build akhir. Cakupan adalah `LoginScreenTest`, `P5ValidationInstrumentedTest`, `LecturerAnalyticsTotalsNotRenderedTest`, `AndroidMaterialFileStoreTest`, dan `ExampleInstrumentedTest`. Run memakai pemilihan class melalui `adb shell am instrument -w -r -e class`.
- **NOT RUN — harness manual.** `CaptureScreensTest` dan `ReleaseMatrixTest` ikut dikompilasi. Keduanya bergantung pada backend dan fixture yang disiapkan terpisah dari tes instrumentation lokal.
- **NOT RUN — provider dan infrastruktur live.** Tidak ada akses Cloudflare R2 nyata, API publik, database produksi, konfigurasi host, atau deploy. Uji backend storage lokal dan pemeriksaan Compose tidak membuktikan perilaku live atau kompatibilitas host Proxmox secara langsung.
- **Bukti perintah lokal.** Log pengujian tersedia pada direktori sementara `/tmp/eduvark-cleanup.IvBEYd`. Laporan menyimpan hasil yang relevan agar bukti ringkas tetap dapat ditinjau ketika direktori sementara hilang.

## 6. Kesiapan perubahan

**READY FOR PR — housekeeping mode konservatif.** Paket aktif terdiri dari `.gitignore` dan laporan ini. Hasil build dan regresi di §5 merupakan bukti percobaan awal. Untuk paket aktif, verifikasi difokuskan pada kesetaraan sumber produksi dengan baseline dan cakupan aturan ignore. Working tree juga memuat perubahan Owner yang terpisah. Review terpisah diperlukan untuk D01–D12 sesuai risiko dan status bukti. Untuk prioritas berikutnya, selesaikan review redaksi log D01 sebelum perubahan yang berkaitan dengan URL lama, dan verifikasi guard operasional D08 sebelum tindakan purge produksi. Dokumentasi atau kode pada paket tersebut belum diubah.

Tidak ada commit, push, merge, deployment, perubahan konfigurasi live, atau penghapusan database maupun volume aplikasi. Pengujian menggunakan container PostgreSQL sementara dan AVD read-only. Docker image dibuat hanya di runtime lokal.

## 7. Verifikasi mode konservatif

- `git diff --exit-code HEAD -- app backend build.gradle.kts gradle gradle.properties settings.gradle.kts gradlew gradlew.bat .github deploy scripts docker-compose.yml docker-compose.homelab.yml docker-compose.production.yml .env.homelab.example` menghasilkan exit 0. Tidak ada file source Android/backend baru yang terlihat sebagai untracked.
- `git check-ignore` tetap mencakup pengaturan lokal IDE dan sandbox simulasi. Probe laporan di luar sandbox tidak ikut diabaikan. File generated yang sudah ada tetap di disk.
- Hash diff tracked di luar cakupan ini cocok sebelum dan sesudah operasi. Perubahan Owner tetap tersedia. Tidak ada build baru, instalasi APK, restart server, commit, push, ataupun deploy dalam mode konservatif.
- Patch lokal yang diabaikan Git ada di `.omc/artifacts/eduvark-cleanup-source-4b91f6a.patch`. SHA-256 `063220f6e29dc66d2c016cb6af482a6cea5e6e52652aacf9d1fcca9e8fb393f8`. `git apply --check` terhadap baseline berhasil. Patch hanya disimpan untuk review dan tidak diterapkan pada paket aktif.

## Lampiran — file dalam percobaan cleanup awal

Inventaris historis ini mengecualikan perubahan Owner dan memasukkan laporan audit sebagai file baru. Dalam mode konservatif, hanya `.gitignore` dan laporan audit yang tetap menjadi bagian cleanup aktif. `M` berarti dimodifikasi, `D` dihapus, dan `A` ditambahkan.

```text
M	.gitignore
M	app/src/main/java/com/eduvark/app/core/di/RepositoryModule.kt
D	app/src/main/java/com/eduvark/app/data/repository/HealthRepositoryImpl.kt
D	app/src/main/java/com/eduvark/app/domain/model/HealthStatus.kt
D	app/src/main/java/com/eduvark/app/domain/repository/HealthRepository.kt
D	app/src/main/java/com/eduvark/app/domain/usecase/GetHealthUseCase.kt
M	app/src/main/java/com/eduvark/app/presentation/account/AccountScreen.kt
M	app/src/main/java/com/eduvark/app/presentation/admin/users/AdminCreateUserScreen.kt
M	app/src/main/java/com/eduvark/app/presentation/admin/users/AdminUserDetailScreen.kt
M	app/src/main/java/com/eduvark/app/presentation/admin/users/AdminUsersScreen.kt
M	app/src/main/java/com/eduvark/app/presentation/auth/ChangePasswordScreen.kt
M	app/src/main/java/com/eduvark/app/presentation/auth/LoginScreen.kt
M	app/src/main/java/com/eduvark/app/presentation/auth/ServerSettingsScreen.kt
M	app/src/main/java/com/eduvark/app/presentation/home/HomeScreen.kt
M	app/src/main/java/com/eduvark/app/presentation/lecturer/analytics/LecturerAnalyticsScreen.kt
M	app/src/main/java/com/eduvark/app/presentation/lecturer/assessments/LecturerGradeScreen.kt
M	app/src/main/java/com/eduvark/app/presentation/lecturer/assessments/LecturerSubmissionsScreen.kt
M	app/src/main/java/com/eduvark/app/presentation/lecturer/cases/LecturerCaseScreen.kt
M	app/src/main/java/com/eduvark/app/presentation/lecturer/hub/LecturerHubScreen.kt
M	app/src/main/java/com/eduvark/app/presentation/lecturer/materials/LecturerMaterialsScreen.kt
M	app/src/main/java/com/eduvark/app/presentation/lecturer/topics/LecturerTopicsScreen.kt
M	app/src/main/java/com/eduvark/app/presentation/student/cases/StudentCaseScreen.kt
M	app/src/main/java/com/eduvark/app/presentation/student/materials/StudentMaterialsScreen.kt
M	app/src/main/java/com/eduvark/app/presentation/student/topics/StudentTopicsScreen.kt
M	app/src/main/java/com/eduvark/app/presentation/vark/VarkSelectionScreen.kt
M	app/src/main/java/com/eduvark/app/ui/components/Buttons.kt
M	app/src/main/java/com/eduvark/app/ui/components/Chrome.kt
M	app/src/main/java/com/eduvark/app/ui/components/Fields.kt
M	app/src/main/java/com/eduvark/app/ui/components/Lists.kt
M	app/src/main/java/com/eduvark/app/ui/components/Surfaces.kt
M	app/src/main/res/values-en/strings_admin.xml
M	app/src/main/res/values-en/strings_lecturer_a.xml
M	app/src/main/res/values-en/strings_lecturer_b.xml
M	app/src/main/res/values-en/strings_p3.xml
M	app/src/main/res/values-en/strings_p4.xml
M	app/src/main/res/values/colors.xml
M	app/src/main/res/values/strings_admin.xml
M	app/src/main/res/values/strings_lecturer_a.xml
M	app/src/main/res/values/strings_lecturer_b.xml
M	app/src/main/res/values/strings_p3.xml
M	app/src/main/res/values/strings_p4.xml
M	backend/src/lib/errors.ts
M	backend/src/lib/login-id.ts
M	backend/src/lib/rate-limit.ts
M	backend/src/modules/materials/object-deletions.service.ts
M	backend/src/types/auth.ts
M	backend/tests/integration/topics.test.ts
A	docs/audit/CODEBASE-CLEANUP-AUDIT.md
```
