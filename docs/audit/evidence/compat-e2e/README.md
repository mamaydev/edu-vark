# Bukti eksekusi matriks kompatibilitas lintas versi

Dijalankan Lead pada 24 Nov 2026, emulator `Pixel_10_Pro_XL` (API 37).

- `new-backend-material-list-old-client.json` — respons backend baru
  (`GET /api/v1/topics/:id/materials`) yang diterima klien lama: `signedUrl` dan
  `url` bernilai `null`, hanya `contentUrl` terisi.
- `new-backend-analytics-vark-null.json` — respons backend baru
  (`GET /api/v1/lecturer/analytics/topics/:id`): `students[].vark = null`,
  sedangkan DTO Android lama mendeklarasikan `vark: String` (non-null).
- `new-backend-legacy-admin-login-401.json` — `loginId: "admin"` ditolak backend baru.
- `new-backend-numeric-admin-login-200.json` — `loginId: "01401400001"` diterima
  backend baru (membuktikan migrasi ID Admin berhasil).
- `old-backend-content-endpoint-404.json` — backend lama tidak memiliki rute
  `GET /materials/:id/content`; aplikasi baru memetakannya menjadi pesan
  "Materi tidak dapat dibuka" (controlled failure).

Setelan eksekusi:

- APK lama dibangun dari `b7b6cd6` di worktree terpisah
  (`git worktree add /tmp/eduvark-oldapp b7b6cd6`).
- Backend baru: `node dist/server.js` dengan DB `eduvark_restore_test`
  dan `LOCAL_STORAGE_DIR=/tmp/eduvark-restore-storage`, port 3001.
- Backend lama: worktree `b7b6cd6`, DB `eduvark_oldcompat` (hanya migrasi 001),
  port 3002.
- Emulator menjangkau host lewat `adb reverse tcp:3001 tcp:3001` dan
  `adb reverse tcp:3002 tcp:3002`, lalu base URL debug diarahkan ke
  `http://127.0.0.1:<port>/api/v1/`.

Hasil ringkas ada di `docs/audit/evidence/P2.md` §9.5.1.
