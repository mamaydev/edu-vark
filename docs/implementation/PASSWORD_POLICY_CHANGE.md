# Password Policy Change — Minimal 6 Karakter

**Tanggal:** 22 November 2026  
**Status:** Selesai dan terverifikasi.  
**Dasar:** permintaan pengguna; D-19 menggantikan aturan panjang/kompleksitas D-16.

## 1. Aturan baru

- Password **hanya** wajib minimal **6 karakter**.
- Tidak ada aturan huruf besar/kecil, angka, simbol, atau kombinasi.
- Tidak ada blokir password umum.
- Tidak ada batas maksimum password di schema validasi.
- Password baru **boleh sama** dengan password lama.
- Konfirmasi password tetap dicek di UI (bukan aturan komposisi).
- Hashing tetap Argon2id; kebijakan token/sesi tidak berubah.

## 2. File yang diubah

### Backend

- `src/lib/password.ts`: hanya cek panjang < 6; daftar password umum dihapus.
- `src/modules/auth/auth.schema.ts`: login/change-password tanpa max; password baru min 6.
- `src/modules/admin/admin.schema.ts`: password sementara min 6, tanpa max.
- `src/db/seed-admin.ts`: password seed min 6.
- `src/modules/auth/auth.service.ts`: aturan “password baru harus berbeda” dihapus.

### Android

- `ChangePasswordViewModel`: min 6; aturan password baru berbeda dari password lama dihapus.
- `AdminCreateUserViewModel`, `AdminUserDetailViewModel`, layar admin: min 6.
- `strings.xml`: pesan minimal 6 karakter.

## 3. Bukti test

- Backend: **76 test lulus** (unit password + integrasi auth 6 karakter dan password sama).
- Android: **26 unit test lulus**.
- `assembleDebug` dan `lintDebug`: lulus.

Test backend mencakup:

- `12345` ditolak;
- `aaaaaa`, `123456`, `password` diterima;
- ganti password dari `abc123` ke `xyz789`;
- ganti `xyz789` ke `xyz789` (sama) tetap berhasil.

## 4. Catatan keamanan

Password 6 karakter lebih lemah dari kebijakan sebelumnya. Ini adalah keputusan eksplisit pengguna; Argon2id tetap dipakai, rate limit login tetap 5 kegagalan/15 menit, dan token/sesi tidak berubah.
