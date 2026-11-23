# EduVARK — Issues v1 Fixes (Dosen & Sistem)

- Branch: `fix/eduvark-functional-audit-2026`
- Source: `docs/issues/issues-v1.md`
- Scope: Android client (`app/`), Express API (`backend/`), existing schema
- Status: implementation and local verification complete; no production deployment

## Summary

| Issue | Area | Root cause | Resolution |
| --- | --- | --- | --- |
| Dosen 1 | Lecturer assessment | The submission list required an **active session with `vark_type = KINESTHETIC`**. Logging in again revokes the old session and the new session has no VARK, so submitted answers disappeared even though the aggregate still counted them. | The list now includes every student with a stored submission **or** an active Kinesthetic session. Submission rows are joined independently, so answers remain visible after re-login or session revocation and no duplicate rows are produced. |
| Dosen 2 | Lecturer materials | The lecturer material/case screens loaded the topic through the student/public `GET /topics/:id`, which is protected by `requireVark`. | Added owner-scoped `GET /lecturer/topics/:id`, exposed it in the Android API/repository/use case, and switched both lecturer ViewModels to it. Lecturers can open draft and published topics without selecting a VARK. The public route also no longer registers the student dashboard handler by mistake. |
| Sistem 1 | Identity inputs | No shared length rule; the login form and admin create-user form accepted arbitrary lengths. | Student NIM and lecturer NIP/NIDN are now **10–11 digits only** on Android and on the backend. Admin accounts keep a username. Invalid input is rejected locally with an inline message and by the API with `422 VALIDATION_ERROR`. |
| Sistem 2 | Login identity | Login accepted any username for every role. | Non-admin logins must use a 10–11 digit NIM/NIP/NIDN. Admin logs in with username `admin` (other admin usernames remain supported for compatibility). Login labels/placeholders were updated to `NIM / NIP / NIDN` with an explicit admin hint. |

## Detailed changes

### Dosen 1 — Submitted Kinesthetic answer not visible

**Problem.** `assessments.repository.ts#listCaseStudents` only listed a student when their current auth session carried `vark_type = 'KINESTHETIC'`. Every successful login revokes older sessions and creates a new session with `vark_type = NULL`. After the student logged in again, the lecturer saw “1 pengumpulan” but the student row had no submission/answer.

**Fix.**
- Removed the mandatory `JOIN auth_sessions ... vark_type = 'KINESTHETIC'`.
- The student row is included when a submission exists **or** an active Kinesthetic session exists:
  - `LEFT JOIN submissions`
  - `AND (s.id IS NOT NULL OR EXISTS (active Kinesthetic session))`
- Submission join is independent, so it survives session revocation/relogin.
- The query keeps one row per enrolled student and does not duplicate rows when both conditions hold.

**Verification.**
- New integration test: submit a Kinesthetic answer, log the same student in again (new non-VARK session), then assert the lecturer list still contains the submission ID/status, and the answer endpoint returns the submitted text.
- Existing status tests still cover `MENUNGGU_PENILAIAN`, `TIDAK_MENGUMPULKAN`, and graded rows.

### Dosen 2 — Lecturer blocked by “pilih VARK terlebih dahulu”

**Problem.** `LecturerMaterialsViewModel` and `LecturerCaseViewModel` called `GetTopicUseCase`, which uses the student/public topic endpoint guarded by `requireVark`. Lecturers do not have a VARK, so draft and published topics failed to load while managing materials.

**Fix.**
- Backend: new owner-scoped route `GET /api/v1/lecturer/topics/:id` using the lecturer topic handler. It requires the lecturer role and verifies class ownership; it has no VARK guard.
- Removed the accidental `getStudentDashboardHandler` registration from public `GET /topics/:id`.
- Android: added `getLecturerTopic` to `EduVarkApi`, `TopicRepository`, `TopicRepositoryImpl`, `GetLecturerTopicUseCase`, and injected the new use case into both lecturer ViewModels.
- Lecturer screens can now open materials for `DRAFT` and `PUBLISHED` topics.

**Verification.**
- New integration test reads a draft topic through the lecturer endpoint, publishes it, and reads it again — both succeed without a VARK selection.
- Android tests construct both lecturer ViewModels with `GetLecturerTopicUseCase`; the material ViewModel test makes the student endpoint fail to prove the lecturer path is used.

### Sistem 1 & 2 — NIM/NIP/NIDN length and login identity

**Rule.**
- `STUDENT` and `LECTURER` identifiers: exactly 10 or 11 digits (`/^[0-9]{10,11}$/`).
- `ADMIN`: username; default login is `admin`.
- The rule applies to login and to admin account creation (the only in-app identity input).

**Backend.**
- `src/lib/login-id.ts`: shared pattern, message, and helpers.
- `admin.schema.ts`: `createUserSchema` rejects non-admin IDs outside 10–11 digits with `VALIDATION_ERROR`.
- `auth.service.ts`: after valid credentials, rejects non-admin accounts whose stored identifier is outside the rule; message is `NIM/NIP/NIDN atau password tidak valid.` Admin login is exempt.
- Duplicate-account error now reads `NIM/NIP/NIDN sudah digunakan.`

**Android.**
- `LoginIdRules`: `isAcademicId` and `isValidLoginId` (academic ID or `admin`).
- Login ViewModel rejects short/long/non-numeric IDs before the request; empty-field message updated.
- Admin create-user ViewModel validates 10–11 digits for `STUDENT`/`LECTURER` and keeps the username path for `ADMIN`.
- Login and create-user screens use `NIM / NIP / NIDN` placeholders plus helper text explaining the 10–11 digit rule and the admin exception.
- String resources updated.

**Verification.**
- Backend integration tests cover: 9/12-digit and non-numeric create attempts rejected (`422`), 11-digit accepted; non-admin short login rejected; admin username accepted.
- Backend unit tests cover the identifier pattern and admin username helper.
- Android unit tests cover invalid login IDs, admin login, invalid create-user IDs (9 and 12 digits), and admin username creation.
- UI smoke test (`LoginScreenTest`) asserts the new placeholder.

## Test evidence

Backend (`backend/`, Node 24):

```text
npm run typecheck  -> pass
npm run lint       -> pass
DATABASE_URL=postgresql://eduvark:eduvark@localhost:5432/eduvark_test npm test
  Test Files 15 passed (15)
  Tests      93 passed (93)
npm run build      -> pass
```

Android:

```text
./gradlew testDebugUnitTest          -> BUILD SUCCESSFUL (38 tests)
./gradlew assembleDebug              -> BUILD SUCCESSFUL
./gradlew compileDebugAndroidTestKotlin -> BUILD SUCCESSFUL
./gradlew lintDebug                  -> BUILD SUCCESSFUL
```

Debug APK installed on the connected Xiaomi `2312FPCA6G` (`com.eduvark.app.debug`) and launched. The login screen shows the new `NIM / NIP / NIDN atau admin` field, helper text, and admin guidance.

## Deployment note

The installed debug build is pointed at a local server through the debug-only `api_base_url` override for verification. The production backend at `https://pam.sequein.my.id` will only contain these fixes after the branch is merged and deployed from `main`.
