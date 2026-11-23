# UI Redesign — Tema Gelap `docs/Desain/redesain`

**Status:** ✅ selesai diterapkan ke seluruh layar Compose (`app/src/main/java/.../presentation`) dan
diverifikasi lewat screenshot perangkat vs mockup (lihat "Hasil QA akhir" di balik dokumen ini).
**Acuan:** 40 mockup di [`docs/Desain/redesain`](../Desain/redesain) (`Tema gelap · 01…40`).
**Sifat redesign:** mockup baru memakai **layout yang identik** dengan versi terang lama; yang berubah
adalah **palet, permukaan, dan warna peran komponen**. Jadi pekerjaan per layar = mengganti token &
warna teks sesuai aturan di bawah, **bukan** menyusun ulang layout.

## Design system (dark-only)

| Token | Nilai | Dipakai untuk |
|---|---|---|
| `EduColors.Page` | `#181818` | latar semua halaman (top bar juga transparan = Page) |
| `EduColors.Surface` | `#2D2D2D` | kartu, sheet, bottom bar mengambang |
| `EduColors.Lavender` | `#DCC3FA` | **permukaan brand**: hero card, kartu terpilih, panel besar |
| `EduColors.LavenderDim` | `#BCA4D6` | link inline, outline tombol sekunder, teks brand yang diredam |
| `EduColors.Lime` | `#F6F576` | **aksi utama** (isi tombol primary), eyebrow, status aktif/sukses |
| `EduColors.OnBrand` | `#181818` | SEMUA teks/ikon di atas `Lavender` atau `Lime` |
| `EduColors.Ink` | `#F6F7F2` | judul & teks utama di atas Page/Surface |
| `EduColors.Muted` | `#A3A3A1` | teks sekunder, meta, caption |
| `EduColors.MutedSoft` | `#878785` | ikon nav non-aktif, hint |
| `EduColors.PurpleTint` | `#2D2636` | panel info / terpilih (isi: `Lavender`) |
| `EduColors.SuccessTint` | `#2E3023` | panel sukses/tercapai (isi: `Lime`) |
| `EduColors.AmberTint` | `#372F24` | panel peringatan/menunggu (isi: `Amber`) |
| `EduColors.DangerTint` | `#3A2522` | panel error/destruktif (isi: `DangerMuted`) |
| `EduColors.Success` | `#F6F576` | teks status selesai/tersedia (= Lime) |
| `EduColors.Amber` | `#EECC8B` | teks peringatan / menunggu |
| `EduColors.Danger` | `#F07049` | isi tombol destruktif, aksen error, bar Read/Write |
| `EduColors.DangerMuted` | `#FFA58B` | teks error di atas `DangerTint` |
| `EduColors.Purple` | `#9F8CB7` | bar/metrik keempat di analitik (Kinesthetic) |
| `EduColors.Border` | `#474745` | hairline, divider, track bar metrik |
| `EduColors.FieldBorder` | `#656660` | outline input/dropdown |
| `EduColors.MediaNavy` | `#312B39` | panel pemutar video/audio |

Tipografi tidak berubah (Plus Jakarta Sans, skala `EduText`). Default warna di `Type.kt` sudah
diperbarui: `HeroTitle`, `NumeralLarge`, `Button` = `OnBrand`; `Eyebrow` = `Lime`; `Link` =
`LavenderDim`; `Ink`/`Muted` otomatis gelap-terang.

Komponen bersama (`ui/components`) sudah di-restyle:

- `EduTopBar` — transparan (latar Page), judul `Ink`, tanpa hairline, ikon kembali `Ink`.
- Bottom bar — **bar mengambang** (hasil ukur mockup 05): tinggi 72dp, `Surface`, radius 24dp,
  margin 24dp kiri/kanan dan 24dp dari bawah, tab aktif `Lime`, non-aktif `MutedSoft`, tanpa
  hairline. (Ditangani oleh komponen, layar tidak perlu diubah.)
- `EduCard` — `Surface`, radius 20dp, **tanpa border**.
- `HeroCard` — `Lavender` (isi wajib `OnBrand`/`EduText.HeroTitle`).
- `Eyebrow` — default `Lime`.
- `TintBar`/`StatusPill`/`NoticeCard`/`TonePanel` — memakai `tint()`/`content()` baru (lihat token).
  `TintBar` punya parameter opsional `background`/`contentColor` untuk kombinasi khusus mockup
  (contoh mockup 04: panel `PurpleTint` + teks `Lime`).
- `PrimaryButton` — pill `Lime`, teks/arrow/spinner `OnBrand`, disabled = Lime alpha 0.4.
- `SecondaryButton` — latar transparan + outline 1dp `LavenderDim`, teks `Ink`.
- `DangerButton` — isi `Danger`, teks **putih**.
- `LightButton` — dipakai di dalam hero: sekarang transparan + teks/arrow `OnBrand` (tanpa isi).
- `TextLink` — default `LavenderDim` + arrow.
- `EduField`/`EduDropdown` — latar transparan, outline 1dp `FieldBorder`, radius 12dp, kursor `Lime`.
- `ListCard`, `TopicCard`, `UserCard`, `StatCard`, `MetricBar`, `SuccessPanel`, `EmptyBlock`,
  `LoadingBlock`, `ErrorBlock` — sudah menyesuaikan token baru.

## Aturan migrasi per layar (WAJIB)

Alias lama (`EduColors.Primary`, `PrimaryTint`, `Teal`, `TealTint`) masih ada agar kompilasi jalan,
tetapi **jangan dipakai di kode baru**; ganti ke nama semantik dan periksa peran tiap pemakaian:

1. `EduColors.Surface`/`Color.White` yang dipakai sebagai **teks/ikon di atas container `Lavender`
   (HeroCard), `Lime`, atau panel brand** → `EduColors.OnBrand` (alpha boleh dipertahankan untuk
   teks sekunder: `OnBrand.copy(alpha = 0.72f)`). Ini kelas perubahan terpenting — lihat daftar di
   bagian "Titik rawan".
2. `Color.White` sebagai teks **di atas `Danger`** (tombol destruktif) tetap putih.
3. `EduColors.Primary` sebagai isi container (hero/panel) → `EduColors.Lavender`; bila container
   itu **kartu terpilih** (mis. VARK mockup 03) → `EduColors.Lime` dengan isi `OnBrand`;
   sebagai angka besar/ikon aksen → `EduColors.Lavender` (di atas Surface) atau `Lime` bila
   mockup menampilkannya kuning;
   sebagai **link inline/outline** → `EduColors.LavenderDim`.
4. `EduColors.Teal`/`TealTint` → `Lime`/`SuccessTint` (teks status, eyebrow, angka sukses).
5. `EduColors.Amber`/`AmberTint` → `Amber`/`AmberTint`; `Danger`/`DangerTint` → `Danger`/`DangerTint`
   (teks di dalam tint pakai `DangerMuted`).
6. `Color.White`/`EduColors.Surface` di atas **Page gelap** (bukan di atas brand) → `Ink`.
7. Spinner, kursor, switch, progress, radio terpilih → `Lime`. Khusus slider (mockup 07/08/09):
   thumb `Lime`, **track aktif `Lavender`** (bukan lime), track inaktif `Border`.
8. Bar chart analitik: Visual `Lavender`, Aural `Lime`, Read/Write `Danger`, Kinesthetic `Purple`;
   track `Border`.
9. Panel video/audio (`MediaNavy`) tetap `EduColors.MediaNavy`; teks & kontrol di dalamnya putih
   (`Color.White`/`Ink`) — jangan diubah jadi OnBrand.
10. Jangan mengubah layout, urutan elemen, teks/string, atau logika state. Hanya warna/permukaan.

## Titik rawan (cari dengan grep, perbaiki manual)

`Color.White` / `EduColors.Surface` sebagai warna teks pada: `HomeScreen` (hero), `LoginScreen`
(panel brand), `StudentMaterialsScreen` (kartu pemutar/panel media), `StudentCaseScreen`,
`AccountScreen` (kartu header), `LecturerSubmissionsScreen` (kartu skor), `LecturerGradeScreen`
(kartu skor). Semua teks itu harus dibaca ulang: apakah berada di atas `Lavender`/`Lime`
(→ `OnBrand`) atau di atas `MediaNavy` (→ tetap putih).

## Pemetaan layar → mockup

| Mockup | Layar Compose |
|---|---|
| 01–02 Masuk / ditolak | `presentation/auth/LoginScreen.kt` |
| 03 Pilih VARK | `presentation/vark/VarkSelectionScreen.kt` |
| 04 Akun & sesi | `presentation/account/AccountScreen.kt` |
| 05/12/18/32 Beranda per role | `presentation/home/HomeScreen.kt` |
| 06/39 Topik mahasiswa | `presentation/student/topics/StudentTopicsScreen.kt` |
| 07/08/09/10/11 Materi + feedback | `presentation/student/materials/StudentMaterialsScreen.kt` |
| 13–17 Studi kasus & status | `presentation/student/cases/StudentCaseScreen.kt` |
| 19/20/24 Kelola topik | `presentation/lecturer/topics/LecturerTopicsScreen.kt` |
| 21/22 Materi per kategori | `presentation/lecturer/materials/LecturerMaterialsScreen.kt` |
| 23 Atur studi kasus | `presentation/lecturer/cases/LecturerCaseScreen.kt` |
| 25 Daftar pengumpulan | `presentation/lecturer/assessments/LecturerSubmissionsScreen.kt` |
| 26–28 Beri nilai / konfirmasi / terkunci | `presentation/lecturer/assessments/LecturerGradeScreen.kt` |
| 29–31 Analitik | `presentation/lecturer/analytics/LecturerAnalyticsScreen.kt` |
| 33 Daftar akun | `presentation/admin/users/AdminUsersScreen.kt` |
| 34/37 Buat akun | `presentation/admin/users/AdminCreateUserScreen.kt` |
| 35/36/38 Detail akun | `presentation/admin/users/AdminUserDetailScreen.kt` |

Layar pelengkap (tanpa mockup khusus, warisi sistem baru): `ChangePasswordScreen`,
`ServerSettingsScreen`, `LecturerHubScreen`, `presentation/navigation/*`.

## Cara memeriksa mockup tanpa melihat gambar

Mockup tidak bisa dibuka sebagai gambar di sesi ini. Gunakan pipeline yang sudah disiapkan di
`/tmp/eduvark-design`:

```bash
PY=/Users/macbookpro/.freepi-desktop/dsh-runtimes/dsh-primary-runtime/dependencies/python/bin/python3
cd /tmp/eduvark-design
# 1) teks + posisi (%) hasil OCR Vision
$PY -c "import json,os;obs=json.load(open('new_ocr.json'));fs=sorted(f for f in os.listdir('new') if f.endswith('.jpg'));i=fs.index('05.jpg');[print(round((1-o['y']-o['h'])*100,1),round(o['x']*100,1),o['text'][:40]) for o in obs[i]]"
# 2) peta warna ASCII (karakter = cluster warna, legend di akhir)
$PY ascii3.py new/05.jpg 240 500 0 390 2   # y0 y1 x0 x1 skala
# 3) warna dominan per area
$PY -c "from PIL import Image;from collections import Counter;import numpy as np;a=np.asarray(Image.open('new/05.jpg').convert('RGB'));print([('#%02X%02X%02X'%p,n) for p,n in Counter(map(tuple,a[250:470].reshape(-1,3)//6*6)).most_common(6)])"
# 4) warna teks: ambil piksel paling menyimpang dari latar di kotak teks OCR
```

`new/NN.jpg` = mockup ke-NN (390px lebar, tinggi bervariasi; koordinat relatif, bukan dp).
`/tmp/eduvark-design/rle.py`, `kmeans.py`, `picktext.py`, `linecolor.py` juga bisa dipakai.

## Verifikasi

- Build: `./gradlew :app:assembleDebug`
- Unit test: `./gradlew :app:testDebugUnitTest`
- Screenshot perangkat (adb `2312FPCA6G`, app `com.eduvark.app.debug`, backend port 3000 +
  `adb reverse tcp:3000 tcp:3000`): `adb exec-out screencap -p > /tmp/shot.png`
- Bandingkan palet screenshot vs mockup: `Page #181818`, `Surface #2D2D2D`, `Lavender #DCC3FA`,
  `Lime #F6F576`, tidak boleh ada sisa `#F7F8FC`/`#FFFFFF` sebagai latar, tidak boleh ada teks putih
  di atas lavender, tidak boleh ada teks gelap di atas page.

## Catatan implementasi

- Aplikasi dirender **dark-only** (`EduVarkTheme` memaksa `darkColorScheme`, ikon status bar terang).
- Konfirmasi desain 14/24/27/36 tetap dirender sebagai state di dalam layar (bukan `AlertDialog`).
- Data yang tidak tersedia di API tidak ditampilkan (e-mail akun, durasi video/audio, nama penilai,
  judul topik pada layar analitik).
- Tab **Penilaian**/**Analitik** dosen membuka hub topik sebelum layar detail.
- Bottom bar disediakan oleh nav graph (`EduVarkNavHost`) supaya perilaku tab konsisten.


---

# Hasil QA akhir (39 dari 40 mockup)

Metode: setiap layar dipotret lewat instrumented test (`CaptureScreensTest`, app
`com.eduvark.app.debug`), lalu dibandingkan otomatis dengan mockup memakai
`/tmp/eduvark-design/qc.py` (palet 8-bin app% vs mock%, toleransi 3 poin; OCR Vision untuk
kesesuaian teks/urutan). Kolom "Δ" = jumlah bin warna yang selisihnya > 3 poin.

| Mockup | Layar | Δ | Catatan |
|---|---|---|---|
| 01 | Login | 0 | cocok penuh |
| 02 | Login ditolak (validasi) | 0 | cocok penuh |
| 03 / 03b | Pilih VARK / terpilih | 1–4 | selisih sisa = CTA nonaktif `#70703D` (mock `#707038`, beda 1 kanal) & rasio layar |
| 04 | Akun | 1 | hanya scrim system nav |
| 05 | Beranda visual | 2 | kartu lebih tinggi (rasio layar 2.22 vs 2.66) |
| 06 / 06b | Daftar topik V/A/R | 2 | idem |
| 07 / 08 / 09 | Materi V/A/R | 3–4 | CTA lime di mock sebagian di bawah lipatan perangkat |
| 10 | Feedback tersimpan | 3 | idem |
| 11 | Materi belum tersedia | 2 | `MATERIAL_NOT_AVAILABLE` kini memetakan ke empty state (bukan error) |
| 12 | Beranda kinesthetic | 5 | hero `IMPLEMENTASI BASIS DATA`, tanpa CTA/statistik, kartu studi kasus berstatus |
| 13 | Studi kasus (editor) | 5 | TintBar & eyebrow "TOPIK NN • KINESTHETIC", deadline AmberTint, notice PurpleTint |
| 14 | Konfirmasi kirim | 3 | |
| 15 | Menunggu penilaian | 3 | tambah "Jawaban final • N karakter" + tautan "Lihat jawaban terkirim →" |
| 16 | Sudah dinilai | 2 | nama dosen, "Dinilai <tanggal>", tautan "Lihat jawaban final →" |
| 17 | Tenggat berakhir | 2 | |
| 18–31 | Layar dosen | 1–5 | lihat catatan desain di bawah |
| 32–37 | Layar admin | 2–3 | |
| 38 | Login ditolak (akun nonaktif) | 0 | kini layar status tersendiri (`Status akun`, TintBar "LOGIN DITOLAK", kartu bahaya, CTA lime) |
| 39 | Daftar topik kosong | 1 | |
| 40 | Akses ditolak | — | **tidak ada di aplikasi** (lihat di bawah) |

Selisih yang tersisa konsisten berasal dari tiga hal non-desain: (1) rasio layar perangkat
2.22 vs mockup ~2.66 sehingga blok 30–40% lebih tinggi dan sebagian CTA masuk bawah lipatan,
(2) scrim system navigation bar `#101010` (3,4%) yang bukan bagian UI aplikasi,
(3) angka/kopi data nyata (jumlah akun, judul topik, nama dosen) yang memang berbeda dari mockup.

## Mockup 40 — akses ditolak (keputusan desain)

Aplikasi tidak punya layar "akses ditolak": otorisasi dilakukan di backend (401/403) dan setiap
layar menampilkan `ErrorBlock`/`NoticeCard` dengan pesan yang sesuai, tanpa membuat halaman
khusus. Mockup 40 karena itu diperlakukan sebagai *design-only reference* dan tidak
diimplementasikan sebagai route terpisah.

## Perubahan fungsional yang menyertai redesign

Supaya mockup 12/16 bisa tampil sesuai desain, ada dua penambahan kecil (bukan kosmetik):

1. **API kasus** (`backend/src/modules/cases`): `GET /api/v1/topics/:id/case` sekarang juga
   mengembalikan `answer`, `gradedAt`, dan `gradedBy` (nama dosen penilai) dari join
   `submissions` + `assessments` + `users`. Field baru bersifat opsional sehingga klien lama tetap
   kompatibel.
2. **Beranda kinesthetic** (`HomeViewModel`): mengambil status studi kasus untuk dua topik teratas
   agar kartu menampilkan "Belum mengumpulkan"/"Menunggu penilaian"/"Sudah dinilai • NN/100"
   beserta tautan aksi seperti mockup 12.

## Mockup 24/27/36 — konfirmasi

Tetap dirender sebagai state di dalam layar (bukan `AlertDialog`), sesuai catatan implementasi di
atas; hanya `HomeScreen` (logout) dan `LecturerMaterialsScreen` (hapus materi) yang memakai dialog
sistem.

## Verifikasi ulang

```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
./gradlew :app:assembleDebug :app:assembleDebugAndroidTest :app:testDebugUnitTest
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb shell am instrument -w \
  -e class 'com.eduvark.app.CaptureScreensTest#test00_captureStudentVisualFlow' \
  com.eduvark.app.debug.test/androidx.test.runner.AndroidJUnitRunner
```

Catatan environment untuk dua tangkapan khusus: mockup **38** memerlukan akun `20240140022`
berstatus `INACTIVE`, dan mockup **39** memerlukan semua topik `DRAFT`; keduanya harus
dikembalikan (`ACTIVE`/`PUBLISHED`) sesudah dipotret.

## Internasionalisasi & resource (tanpa hardcode)

Semua aset visual kini berasal dari resource Android, bukan literal Kotlin:

| Kategori | Sumber | Akses di Compose |
| --- | --- | --- |
| Teks statis UI | `res/values/strings_<modul>.xml` + `res/values-en/strings_<modul>.xml` | `stringResource(R.string.key)` |
| Warna token | `res/values/colors.xml` (21 token `eduvark_*`) | `EduTheme.colors.<token>` |
| Ukuran (dp/sp/radius/border) | `res/values/dimens.xml` (~116 entri) | `dimen(R.dimen.x)` / `dimenSp(R.dimen.x)` |
| Tipografi (size/line-height/tracking) | `dimens.xml` (`text_size_*`, `text_line_*`, `text_tracking_*`) | `EduTheme.type.<gaya>` |
| Bentuk komponen | `ui/theme/Theme.kt` (`eduShapes`) | `MaterialTheme.shapes` |

Berkas string dipisah per modul agar tidak bentrok: `strings_common.xml` (komponen bersama,
auth/akun/VARK), `strings_home.xml` (home + topik mahasiswa), `strings_student.xml` (materi +
studi kasus), `strings_lecturer_a.xml` (topik/materi/kasus/hub dosen), `strings_lecturer_b.xml`
(analitik + penilaian), `strings_admin.xml` (manajemen pengguna). Total 686 kunci dengan paritas
penuh `values/` ↔ `values-en/` (placeholder `%1$s`/`%1$d` identik). `values/strings.xml` hanya
menyimpan `app_name`.

Tetap berbentuk data/Kotlin (bukan salinan UI): nilai wire API (`VISUAL`, `PUBLISHED`, …),
label enum domain (`UserRole`, `VarkType`, `TopicStatus`, …), pesan error di ViewModel/business
layer, pola `SimpleDateFormat`, serta unit `MB`/`KB`. Formatter tanggal/ukuran memakai
`Locale.getDefault()` sehingga build `values-en` menampilkan nama bulan Inggris, sementara locale
`id` tetap seperti mockup.
