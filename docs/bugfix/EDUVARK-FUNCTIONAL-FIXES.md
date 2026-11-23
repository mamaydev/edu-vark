# EduVARK — Functional Audit and Bug Fixes

- Branch: `fix/eduvark-functional-audit-2026`
- Scope: Android client (`app/`), Express API (`backend/`), existing schema
- Status: implementation complete, verified locally on debug/test builds
- No production deployment, no data migration, no secrets were added

## Executive summary

| ID | Priority | Root cause | Resolution |
| --- | --- | --- | --- |
| BUG-01 | P2 | Material titles were trimmed at the server but the Android upload/link path had no local empty/length rule; the DB check only guarded outer whitespace. | Client rejects blank and >200-character titles before the request, sends a trimmed title, and regression tests cover server 422 / trim / DB check. |
| BUG-02 | P2 | The student home screen counted local topics and exposed lecturer wording (`Topik Terbit`, `Materi Siap`). | New backend dashboard endpoint returns enrollment-scoped, VARK-scoped counters. Student cards now read **Materi Tersedia** and **Materi Belum Dikerjakan**. Lecturer dashboard is unchanged. |
| BUG-03 | P1 | `ScrollColumn` did not consume the IME inset, so the keyboard covered focused fields on forms and editors. | Added `imePadding()` to the shared scroll container; all text forms use it. Dialogs/sheets in this app contain no text input. |
| FEATURE-01 | P1 | Only the forced first-login password change existed. | Self-service change password with current/new/confirmation, authenticated endpoint, Argon2id, reuse rejection, session revocation, throttle, and audit. |
| FEATURE-02 | P2 | Password fields had no visibility control. | Shared `PasswordField` with default hidden state, Eye/EyeOff toggle, state-preserving `rememberSaveable`, and accessibility descriptions. |
| FEATURE-03 | P2 | Some dynamic screens were not refreshable; adding it naively could duplicate requests or hide content on failure. | `EduPullToRefresh` added only to dynamic lists/dashboards, guarded by `requestInFlight`, with refresh errors shown inline while existing content stays visible. Forms/editors remain unwrapped. |
| BUG-04 | P0 | The student Kinesthetic case screen never requested the topic materials, so the uploaded PDF had no entry point. The lecturer material screen also called the student endpoint that requires VARK. | Student case loads and displays supporting materials with signed URLs. Lecturer material/case screens now use the owner-scoped lecturer endpoint. |
| BUG-05 | P1 | The grading hub disabled cards without a case and had no route to create one; the case editor was not connected to Kinesthetic materials. | Cards without a case now open the case editor (`Buat studi kasus`); the editor shows topic context, Kinesthetic material count, and `Kelola materi Kinesthetic`. |
| BUG-06 | P1 | Analytics exposed the current session VARK as the feedback category and rejected cross-category access; Kinesthetic rows were coupled to the active session. | Feedback is selected and labeled by its stored `feedback.vark_type`; the active session is only a fallback. Kinesthetic assessment scores remain a separate block and are never averaged with 0–100 feedback. |

## Detailed changes

### BUG-01 — Material title integrity

**Problem.** The server schema and database check already rejected whitespace-only material titles, but the Android `LecturerMaterialsViewModel` sent the raw title for uploads and only checked blank input for links. Oversized or padded titles could reach the API. The UI also gave no immediate feedback.

**Fix.**
- `LecturerMaterialsViewModel` trims once, validates empty and 200-character maximum before either upload or link, and uses the trimmed value in the API call.
- Both actions return early while a save is already in flight, preventing duplicate material creation.
- Existing server validation (`z.string().trim().min(1).max(200)`) and DB checks (`char_length(btrim(title)) BETWEEN 1 AND 200`) remain the authoritative boundary.
- Added Android tests for blank uploads, trimmed uploads, and oversized link titles. Added backend tests for blank/oversized 422 responses, trimming, and the student dashboard regression path.

**Files:** `app/.../lecturer/materials/LecturerMaterialsViewModel.kt`, `app/src/test/.../LecturerMaterialsViewModelTest.kt`, `backend/tests/integration/topics.test.ts`.

### BUG-02 — Student dashboard counters

**Problem.** The home screen derived “Topik Terbit” and “Materi Siap” from locally cached topics. The labels were lecturer-oriented and the counts ignored the student’s enrolled class and active VARK.

**Fix.**
- `GET /api/v1/student/dashboard` (student role, password changed, VARK selected) returns `{ vark, availableMaterials, notCompletedMaterials }`.
- Available materials: published topics in the student’s active class that have at least one non-deleted material for the active VARK.
- Not completed:
  - Visual/Aural/Read-Write: available topic with no stored feedback for the active VARK.
  - Kinesthetic: available topic with no case submission by the student.
- The Android student home uses these backend values and the exact labels **Materi Tersedia** and **Materi Belum Dikerjakan**. Lecturer cards are untouched.

**Limitation.** EduVARK has no separate “material completed” table for V/A/R, so “belum dikerjakan” is defined as “has not submitted feedback”. This is a topic-level counter, not a per-material progress percentage; it does not invent a completion state the schema cannot support.

**Files:** `backend/src/modules/topics/topics.repository.ts`, `topics.service.ts`, `topics.controller.ts`, `topics.routes.ts`; `app/.../domain/model/StudentDashboard.kt`, `data/remote/dto/TopicDtos.kt`, `core/network/EduVarkApi.kt`, `data/repository/TopicRepositoryImpl.kt`, `domain/usecase/TopicUseCases.kt`, `presentation/home/HomeViewModel.kt`, `HomeScreen.kt`.

### BUG-03 — Keyboard inset

**Problem.** The app draws edge-to-edge. `ScrollColumn` scrolled and padded horizontally but did not add the IME inset, so the soft keyboard covered the focused field near the bottom of forms.

**Fix.** `ScrollColumn` now applies `.imePadding()` before its content padding. Every form, login/change-password screen, Kinesthetic answer editor, topic editor, material form, and grade form uses this shared container. No dialog or bottom sheet in the repository contains a text field, so no overlay-specific padding was needed.

**Files:** `app/src/main/java/com/eduvark/app/ui/components/Chrome.kt` and all form call sites that rely on `ScrollColumn`.

### FEATURE-01 — Self-service password change

**Flow.** Account screen → “Ubah password” → current password, new password, confirmation. This route is separate from the forced first-login flow (`ChangePasswordScreen(forced = false)` versus the forced route).

**Backend contract.** `POST /api/v1/auth/change-password`
```json
{
  "currentPassword": "current secret",
  "newPassword": "new secret",
  "confirmPassword": "new secret"
}
```
- Authenticated; current password is verified before any change.
- Confirmation mismatch and reuse of the current password are rejected.
- Strong-password policy is enforced; hashing stays Argon2id.
- Password update, all-session revocation, and `PASSWORD_CHANGED` audit write happen in one transaction.
- Failed current-password attempts are throttled per user: 5 failures / 15 minutes → HTTP 429 `TOO_MANY_ATTEMPTS`. Successful changes clear the counter.

**Android.** The repository and use case carry the confirmation explicitly; the ViewModel rejects empty/too-short/mismatched/same passwords locally and surfaces server errors. The user returns to login after success because sessions are revoked.

**Files:** `backend/src/modules/auth/*`, `backend/src/lib/password-change-throttle.ts`; Android auth DTO/repository/use case/ViewModel/screen; `Routes.CHANGE_PASSWORD_SELF`, `EduVarkNavHost.kt`, `AccountScreen.kt`.

### FEATURE-02 — Password visibility

A shared `PasswordField` renders every password input with visibility hidden by default. The Eye/EyeOff icon toggles the transformation without clearing the value or moving the cursor; the visibility flag survives recomposition via `rememberSaveable`. Content descriptions are “Tampilkan <label>” / “Sembunyikan <label>”. No password hash is ever rendered.

**Files:** `app/.../ui/components/Fields.kt`, `EduIcons.kt`; login, forced/self-service change password, admin create user, admin reset password.

### FEATURE-03 — Pull-to-refresh

`EduPullToRefresh` (Material 3 `PullToRefreshBox`) is applied only to dynamic screens:
- Student home and student topic list
- Lecturer topic list, grading hub, analytics hub
- Admin user list

Each ViewModel has a `requestInFlight` guard, so a second refresh cannot overlap an in-flight load. Refresh failures are kept separate from initial-load errors: the previously loaded content remains on screen and an inline danger notice explains the failure. Forms and editors are not wrapped, so a downward drag cannot submit or discard input.

**Files:** `Chrome.kt`, the five ViewModels/screens above, and their tests.

### BUG-04 — Kinesthetic PDF end-to-end

**Root cause.** The upload, metadata, signing, and student material endpoint already existed. The student Kinesthetic case screen only loaded the case and answer editor; it never called the material endpoint, so the signed PDF URL was unreachable from the UI. Separately, the lecturer material screen and case editor were calling the student material endpoint (`/topics/:id/materials`), which requires a VARK and is inappropriate for a lecturer.

**Fix.**
- Student case ViewModel loads topic materials as an optional supporting call. The case and submission flow still works if the list is empty or fails.
- The case screen shows a “Materi pendukung” card with PDF/link rows. Tapping opens `signedUrl ?: url` via an external viewer with an `ActivityNotFoundException` guard.
- New lecturer material listing call `GET /api/v1/lecturer/topics/:id/materials`; both lecturer material management and the case editor use it. This endpoint verifies topic ownership and is RBAC-protected.
- Lecturer case editor shows the number of Kinesthetic materials and links to material management.

**Flow now:** lecturer uploads PDF → material metadata stored and signed URL issued → student opens Kinesthetic topic case → supporting PDF opens → student submits answer → lecturer sees submission in the grading tab → lecturer enters 0–100 score/comment → student sees result.

**Files:** `StudentCaseViewModel.kt`, `StudentCaseScreen.kt`; `LecturerMaterialsViewModel.kt`, `LecturerCaseViewModel.kt`, `LecturerCaseScreen.kt`; Android API/repository/use case; existing backend material routes (regression-tested).

### BUG-05 — Grading hub and case creation

**Root cause.** The grading hub treated a topic without a case as disabled and provided no way to create one. The case editor had no topic context or link to the Kinesthetic materials that the case depends on. The grading tab looked like a dead end.

**Fix.**
- A grading card with a case opens submissions; a published topic without a case shows **Buat studi kasus** and opens the case editor for that topic.
- Empty/zero-submission states use distinct wording (“Belum ada pengumpulan”) rather than looking like an error.
- The case editor loads topic title and Kinesthetic material count, shows whether supporting material exists, and offers **Kelola materi Kinesthetic**.
- Existing class ownership checks on case/submission endpoints remain the authorization boundary. Manual 0–100 score/comment validation is unchanged.

**Files:** `LecturerHubScreen.kt`, `LecturerHubViewModel.kt`, `LecturerCaseScreen.kt`, `LecturerCaseViewModel.kt`, `EduVarkNavHost.kt`.

### BUG-06 — Analytics feedback categories

**Root cause.** The analytics query joined the latest active session and used that VARK for display and filtering, so stored feedback could disappear or be mislabeled after a session changed. The service also rejected a category that did not match the current session, blocking legitimate historical feedback.

**Fix.**
- Feedback rows are selected by `feedback.vark_type`; display uses the stored feedback category first (`COALESCE(f.vark_type, sess.vark_type)`), with the active session only for students who have no feedback in the selected category.
- Category filtering uses the stored feedback category. Students who only have feedback in another category are not counted as “not responded” for the selected category.
- Kinesthetic assessment scores are returned in a separate `kinesthetic` block with `assessedCount`, `meanScore`, and `notSubmittedCount`. They are never mixed into the V/A/R mean or histogram.
- The client already presents V/A/R bars and the Kinesthetic card separately; the filter copy explicitly says the filter uses the category at feedback time.

**Files:** `backend/src/modules/analytics/analytics.repository.ts`, `analytics.service.ts`; client analytics screen/ViewModel unchanged in structure.

## API and database impact

| Item | Change |
| --- | --- |
| `POST /api/v1/auth/change-password` | Adds required `confirmPassword`; rejects mismatch/reuse; throttles failed current-password attempts; existing sessions revoked; audit row written. |
| `GET /api/v1/student/dashboard` | New. Student-only, enrollment-scoped, VARK-scoped counters. |
| `GET /api/v1/lecturer/topics/:id/materials` | Existing endpoint, now used by Android lecturer screens; ownership enforced. |
| Database schema | No migration. Existing constraints (`ck_material_title`, feedback uniqueness, session/audit tables) are sufficient. |
| Storage | No bucket policy change; signed URLs remain short-lived. |

## Verification

Commands run locally:

```bash
# Android
JAVA_HOME='/Applications/Android Studio.app/Contents/jbr/Contents/Home' \
  ./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug --offline

# Backend (dedicated eduvark_test database)
cd backend
DATABASE_URL='postgresql://eduvark:eduvark@localhost:5432/eduvark_test' npm run db:migrate
DATABASE_URL='postgresql://eduvark:eduvark@localhost:5432/eduvark_test' npm test
npm run typecheck
npm run lint
```

Results are recorded in the final hand-off report. Tests added or updated:
- Material title validation (Android + backend).
- Student dashboard counters and lecturer RBAC (backend).
- Change-password confirmation, reuse, and throttling (backend + Android).
- Student case supporting materials (Android).
- Lecturer case/material loading (Android).
- Refresh failure preserves loaded content (Android).
- Analytics stored-category behavior and Kinesthetic separation (backend).

## Deployment readiness

**READY WITH LIMITATIONS**

- Backend and Android changes are additive or contract-compatible except the required `confirmPassword` field on change-password; the Android client is updated in the same branch.
- No migration is required.
- Limitations:
  - “Materi Belum Dikerjakan” is a topic-level proxy because the schema has no per-material completion table.
  - The password-change throttle is in-memory per API instance; a multi-instance deployment needs a shared store (Redis/Postgres) for strict global enforcement. Current behavior is safe per instance but not globally consistent.
  - No physical-device instrumentation run was performed in this environment; UI behavior is covered by unit/state tests, compile, lint, and APK assembly.
