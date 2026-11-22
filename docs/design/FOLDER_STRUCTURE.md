# Folder Structure — EduVARK v0.7

**Status:** Desain struktur; belum diimplementasikan.  
**Keputusan terkait:** D-01 (Android native, monorepo), D-02 (Supabase/PostgreSQL), D-15 (Supabase hanya Postgres), D-18 (login/logout semua role).

## 1. Struktur repositori

```text
try-pam/
├── SRS_EduVARK_v0.7.docx          # sumber asli, tidak diubah
├── docs/                          # analisis + desain (dokumen ini)
├── app/                           # Android (Kotlin + Compose)
├── backend/                       # Express + TypeScript (belum dibuat)
├── gradle/
├── settings.gradle.kts
├── build.gradle.kts
└── gradle.properties
```

> Saat implementasi, proyek/package Android di-rename menjadi **EduVARK** / `com.eduvark.app` (D-01). Rename dilakukan sebelum fondasi agar tidak menimbulkan utang teknis.

## 2. Android — Clean Architecture

### 2.1 Prinsip

- Satu module `:app` untuk MVP; batas layer dijaga lewat package.
- Dependency rule: `presentation → domain ← data`.
- `domain` adalah Kotlin murni: tanpa Android, Retrofit, Room, atau Hilt.
- Composable tidak memanggil HTTP/database; ViewModel memanggil UseCase.
- Seluruh UI memakai Compose; XML hanya untuk konfigurasi Android.

### 2.2 Struktur package

```text
app/src/main/java/com/eduvark/app/
├── EduVarkApplication.kt
├── MainActivity.kt
├── core/
│   ├── common/
│   │   ├── Result.kt
│   │   ├── UiState.kt
│   │   ├── UiError.kt
│   │   └── Constants.kt
│   ├── network/
│   │   ├── ApiService.kt
│   │   ├── AuthInterceptor.kt
│   │   ├── TokenAuthenticator.kt
│   │   └── NetworkModule.kt
│   ├── datastore/
│   │   └── SessionStore.kt
│   ├── di/
│   │   ├── NetworkModule.kt
│   │   ├── RepositoryModule.kt
│   │   └── UseCaseModule.kt
│   └── ui/
│       ├── theme/
│       ├── components/
│       └── error/
├── data/
│   ├── remote/
│   │   ├── dto/
│   │   └── mapper/
│   ├── local/
│   └── repository/
│       ├── AuthRepositoryImpl.kt
│       ├── TopicRepositoryImpl.kt
│       ├── MaterialRepositoryImpl.kt
│       ├── FeedbackRepositoryImpl.kt
│       ├── CaseRepositoryImpl.kt
│       ├── AssessmentRepositoryImpl.kt
│       └── AnalyticsRepositoryImpl.kt
├── domain/
│   ├── model/
│   │   ├── User.kt
│   │   ├── Session.kt
│   │   ├── VarkType.kt
│   │   ├── Topic.kt
│   │   ├── Material.kt
│   │   ├── Feedback.kt
│   │   ├── CaseStudy.kt
│   │   ├── Submission.kt
│   │   ├── Assessment.kt
│   │   └── Analytics.kt
│   ├── repository/
│   │   └── ... (interface)
│   └── usecase/
│       ├── auth/
│       ├── vark/
│       ├── student/
│       ├── lecturer/
│       └── admin/
└── presentation/
    ├── navigation/
    │   ├── EduVarkNavHost.kt
    │   ├── Routes.kt
    │   └── RoleGraph.kt
    ├── auth/
    │   ├── LoginScreen.kt
    │   ├── LoginViewModel.kt
    │   └── ChangePasswordScreen.kt
    ├── vark/
    │   ├── VarkSelectionScreen.kt
    │   └── VarkSelectionViewModel.kt
    ├── student/
    │   ├── topics/
    │   ├── material/
    │   ├── feedback/
    │   ├── case/
    │   └── result/
    ├── lecturer/
    │   ├── topics/
    │   ├── materials/
    │   ├── cases/
    │   ├── grading/
    │   └── analytics/
    ├── admin/
    │   └── accounts/
    └── common/
        ├── components/
        └── state/
```

### 2.3 Layer responsibilities

| Layer | Isi | Tidak boleh |
|---|---|---|
| Presentation | Screen, reusable component, ViewModel, UI state/event, navigation | HTTP/database langsung; business rule final |
| Domain | Model, repository interface, use case, aturan domain | Import Android/Retrofit/Room |
| Data | API service, DTO, mapper, repository impl, DataStore | Menentukan aturan bisnis final |
| Core | Network, DI, theme, common UI, error mapping | Menyimpan state layar |

### 2.4 ViewModel dan UI state

- Setiap layar memiliki `UiState` tersegel: `Loading`, `Success`, `Empty`, `Error`.
- Event sekali jalan (navigasi, snackbar) memakai `Channel`/ `SharedFlow`.
- ViewModel tidak menyimpan `Context`; gunakan `SavedStateHandle`/use case.
- `401` → logout lokal; `403` → pesan izin; `409` → pesan konflik; `422` → validasi field.

### 2.5 Dependency yang direncanakan

- Compose BOM, Material 3, Navigation Compose.
- Hilt + KSP.
- Retrofit + OkHttp + Kotlinx Serialization.
- Coroutines/Flow.
- DataStore (token/session).
- Coil (gambar).
- Media3 ExoPlayer (audio/video).
- PdfRenderer/AndroidView untuk PDF.
- JUnit5/MockK/Turbine, MockWebServer, Compose UI Test.

> Tidak menambahkan Room untuk MVP karena tidak ada requirement offline. Ini menghindari dependency yang tidak perlu.

### 2.6 Test folders

```text
app/src/test/java/com/eduvark/app/
├── domain/usecase/
├── presentation/
├── data/repository/
└── testutil/

app/src/androidTest/java/com/eduvark/app/
├── ui/
└── navigation/
```

## 3. Backend — Modular Monolith

### 3.1 Prinsip

- Route tipis; controller parse request; service memegang business rule; repository akses data.
- Validasi berlapis: Zod di route, aturan bisnis di service, constraint di database.
- Transaksi untuk submission/assessment/login.
- Error envelope konsisten; tidak membocorkan stack trace.
- Tidak ada business logic di route/controller.

### 3.2 Struktur

```text
backend/
├── src/
│   ├── config/
│   │   ├── env.ts
│   │   └── constants.ts
│   ├── db/
│   │   ├── client.ts
│   │   ├── migrations/
│   │   └── seeds/
│   ├── middleware/
│   │   ├── auth.ts
│   │   ├── rbac.ts
│   │   ├── validate.ts
│   │   ├── error-handler.ts
│   │   ├── request-id.ts
│   │   ├── rate-limit.ts
│   │   └── upload.ts
│   ├── modules/
│   │   ├── auth/
│   │   │   ├── auth.routes.ts
│   │   │   ├── auth.controller.ts
│   │   │   ├── auth.service.ts
│   │   │   ├── auth.repository.ts
│   │   │   └── auth.schema.ts
│   │   ├── users/
│   │   ├── classes/
│   │   ├── topics/
│   │   ├── materials/
│   │   ├── feedback/
│   │   ├── cases/
│   │   ├── submissions/
│   │   ├── assessments/
│   │   ├── analytics/
│   │   └── audit/
│   ├── lib/
│   │   ├── errors.ts
│   │   ├── logger.ts
│   │   ├── storage.ts
│   │   ├── password.ts
│   │   ├── tokens.ts
│   │   └── time.ts
│   ├── app.ts
│   └── server.ts
├── tests/
│   ├── unit/
│   ├── integration/
│   └── fixtures/
├── package.json
├── tsconfig.json
└── .env.example
```

### 3.3 Module contract

Setiap module memiliki:

- `*.routes.ts` — endpoint + middleware.
- `*.controller.ts` — parse request/response.
- `*.service.ts` — aturan bisnis + transaksi.
- `*.repository.ts` — query database.
- `*.schema.ts` — Zod request/response.

### 3.4 Environment

```text
NODE_ENV
PORT
DATABASE_URL
JWT_ACCESS_SECRET
JWT_REFRESH_SECRET
ACCESS_TOKEN_TTL=15m
REFRESH_TOKEN_TTL=12h
SESSION_IDLE_TTL=8h
SESSION_ABSOLUTE_TTL=12h
R2_ENDPOINT
R2_ACCESS_KEY_ID
R2_SECRET_ACCESS_KEY
R2_BUCKET
R2_SIGNED_URL_TTL_SECONDS=300
MAX_UPLOAD_BYTES=104857600
LOG_LEVEL
```

> Tidak ada kredensial hardcoded. `.env` tidak di-commit.

## 4. Dokumentasi

```text
docs/
├── README.md
├── DECISIONS.md
├── srs/
├── use-cases/
├── diagrams/
└── design/          # API contract, DDL, folder, implementation plan
```

## 5. Konvensi penamaan

| Area | Konvensi |
|---|---|
| Kotlin package | `com.eduvark.app` |
| Compose screen | `XxxScreen` |
| ViewModel | `XxxViewModel` |
| Use case | `VerbNounUseCase` |
| DTO | `XxxDto` |
| Domain model | `Xxx` (tanpa suffix) |
| Backend module | `kebab-case` folder, `camelCase` file |
| Tabel | `snake_case` plural |
| Constraint | `ux_`, `ix_`, `ck_`, `fk_` |
| Error code | `UPPER_SNAKE_CASE` |
