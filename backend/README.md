# EduVARK Backend

Express + TypeScript foundation untuk EduVARK. Fase 1 mencakup health check, konfigurasi environment, koneksi PostgreSQL, migration runner, dan seed admin.

## Prasyarat

- Node.js 24+
- PostgreSQL 17 (atau via Docker Compose di root repo)
- npm

## Setup

```bash
cd backend
npm install
cp .env.example .env
# edit .env bila perlu
npm run db:migrate
npm run db:seed-admin
npm run dev
```

Health check:

```bash
curl http://localhost:3000/health
curl http://localhost:3000/api/v1/health
curl http://localhost:3000/health/ready
```

## Scripts

| Script | Fungsi |
|---|---|
| `npm run dev` | Jalankan server dengan tsx watch |
| `npm run build` | Compile TypeScript + copy migrations |
| `npm start` | Jalankan hasil build |
| `npm run typecheck` | `tsc --noEmit` |
| `npm run lint` | ESLint |
| `npm test` | Vitest |
| `npm run db:migrate` | Jalankan migration yang belum applied |
| `npm run db:seed-admin` | Buat admin pertama dari env |

## Catatan

- Jangan commit `.env`.
- Migration `001_init.sql` berasal dari DDL yang sudah divalidasi di PostgreSQL disposable.
- `npm run db:seed-admin` juga membuat kelas aktif `IBD-01` bila belum ada.
- Fase 2 sudah menyediakan endpoint auth, VARK, RBAC, dan admin; endpoint topik/materi ada di Fase 3.
- Error envelope konsisten: `{ error: { code, message, details, requestId } }`.
