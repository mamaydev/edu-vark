# UI Redesign — Implementasi Desain `docs/Desain`

**Status:** diterapkan pada seluruh layar Android (`app/src/main/java/.../presentation`).
**Acuan:** 40 mockup JPG di [`docs/Desain`](../Desain).

## Design system

| Token | Nilai | Dipakai untuk |
|---|---|---|
| `EduColors.Page` | `#F7F8FC` | latar halaman |
| `EduColors.Surface` | `#FFFFFF` | kartu, top bar, bottom bar |
| `EduColors.Primary` | `#2557DE` | hero card, tombol utama, angka besar |
| `EduColors.PrimaryTint` | `#EAF0FF` | kartu terpilih, chip, notice netral |
| `EduColors.Ink` | `#162947` | judul & teks utama |
| `EduColors.Muted` | `#64748B` | teks sekunder |
| `EduColors.Teal` / `TealTint` | `#087F79` / `#E4F5EF` | eyebrow, status selesai/tersedia, metrik objektif |
| `EduColors.Amber` / `AmberTint` | `#995F0B` / `#FFF4DE` | peringatan, deadline, status menunggu |
| `EduColors.Danger` / `DangerTint` | `#B93240` / `#FFF1F1` | error, aksi destruktif, status ditolak |
| `EduColors.Border` | `#DFE8F1` | garis pemisah & border input |
| `EduColors.MediaNavy` | `#162E54` | panel video |
| `EduColors.Purple` | `#7354B2` | bar Read/Write di analitik |

Tipografi: **Plus Jakarta Sans** (Regular/Medium/SemiBold/Bold/ExtraBold, `res/font`, lisensi OFL di
`assets/fonts/OFL.txt`) dengan skala di `EduText`: `Display` 30/37 EB → `Headline` 22 → `Section` 19 →
`CardTitle` 17 → `Body` 15/23 → `BodySmall` 13 → `Eyebrow` 11 caps → `Button` 15 bold.

Komponen bersama (`ui/components`):

- Chrome: `EduScreen` (Scaffold + top bar + status/nav bar inset), `EduTopBar`, `ScrollColumn`,
  `StudentBottomBar`, `LecturerBottomBar`, `AdminBottomBar`.
- Permukaan: `EduCard`, `HeroCard`, `Eyebrow`, `TintBar`, `StatusPill`, `NoticeCard`, `TonePanel`,
  `SectionHeader`, `StatCard`, `SuccessPanel`, `EmptyBlock`, `LoadingBlock`, `ErrorBlock`.
- Aksi: `PrimaryButton` (pill biru), `SecondaryButton` (pill putih), `DangerButton`, `LightButton`
  (di dalam hero), `TextLink`.
- Form: `FieldLabel`, `EduField`, `EduDropdown`, `EduStepper`, `EduRadioRow`, `FieldHelper`.
- Daftar: `ListCard`, `CardEyebrow`, `ArrowTitle`, `TopicCard`, `UserCard`, `MetricBar`, `ContentCard`.
- Ikon: `EduIcons` (set outline 1.8dp, termasuk home/book/person/people/checklist/chart/chevron/
  arrow/plus/check/search/play/expand).

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

Tab **Penilaian** dan **Analitik** pada bottom bar dosen membuka hub pemilihan topik
(`presentation/lecturer/hub/LecturerHubScreen.kt`) sebelum masuk ke layar detail, karena detail
tersebut membutuhkan `topicId`/`caseId`.

## Navigasi, sesi, dan logout

- **Beranda semua role** punya dua aksi di top bar: ikon orang → `Akun & sesi`, dan ikon keluar →
  dialog konfirmasi "Keluar dari akun?" yang mengakhiri sesi lalu kembali ke halaman masuk.
- Bottom bar: mahasiswa `Beranda · Topik · Akun`; dosen `Beranda · Topik · Penilaian · Analitik ·
  Akun`; admin `Beranda · Akun · Profil`. Tab **Akun/Profil** membuka `Akun & sesi` yang memuat
  tombol "Keluar dari akun".
- Setiap layar yang bukan root memiliki tombol kembali. Pada layar dengan state internal, tombol
  kembali mengembalikan ke state sebelumnya lebih dulu: konfirmasi kirim jawaban, konfirmasi nilai
  final, konfirmasi nonaktifkan akun, editor topik, form unggah materi, daftar feedback analitik,
  dan state "feedback tersimpan".
- Tab **Penilaian** membuka hub topik; item dengan studi kasus langsung membuka **daftar
  pengumpulan** (`LecturerSubmissionsScreen`), bukan editor studi kasus. Item tanpa studi kasus
  ditandai "Belum ada studi kasus" dan tidak dapat ditekan.
- Tab **Analitik** membuka hub topik yang sama menuju layar analitik per topik.

## Catatan implementasi

- Konfirmasi desain 14/24/27/36 dirender sebagai state di dalam layar (bukan `AlertDialog`), sesuai
  mockup.
- Data yang tidak tersedia di API tidak ditampilkan: e-mail akun, durasi video/audio, nama penilai,
  dan judul topik pada layar analitik. Angka statistik diturunkan dari data nyata (`topics`,
  `submissions`, `admin users`).
- `topic_title` pada layar studi kasus diambil dari `GetTopicUseCase`; `feedback_updated_at`
  ditambahkan ke state materi agar layar 10 dapat menampilkan waktu.
- Bottom bar disediakan oleh nav graph (`EduVarkNavHost`) supaya perilaku tab konsisten.
- Aplikasi dirender **light-only** karena mockup hanya mendefinisikan tampilan terang.
