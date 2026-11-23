# EduVARK — Issues v2 Fixes (Dosen & Sistem)

- Branch: `fix/eduvark-functional-audit-2026`
- Source: `docs/issues/issues-v2.md`
- Scope: Express API (`backend/`), Android client (`app/`)
- Status: implementation and local verification complete; no production deployment

## Summary

| Issue | Area | Root cause | Resolution |
| --- | --- | --- | --- |
| Dosen 1 | Lecturer assessment | The submission detail list (`GET /lecturer/cases/:id/submissions`) resolved students through an **inner join** on `enrollments`/`users`/`classes` with `status = ACTIVE`/`is_active = true`. Any stored answer whose student, enrollment or class stopped matching those joins disappeared from the list, while the grading tab count (`countSubmissions` on the case payload) kept counting the raw `submissions` rows. The hub therefore reported “2 pengumpulan” and the detail screen could still show nothing. | Candidate students are now derived as **`enrollments` ∪ `submissions`** for the case. A row with `submission_id IS NOT NULL` is always returned regardless of the current enrollment, account or class state, so the detail list can never disagree with the count shown on the grading tab. Kinesthetic students without an answer keep the existing session/enrollment guard. |
| Sistem 1 | Login field labels | Login identity field still labelled the full `NIM / NIP / NIDN atau admin` set and carried a helper line under the input. | Label/placeholder are now `NIM/NIDN` only and the helper line under the field is removed. The hint below the submit button now reads `NIM/NIDN` and keeps the separate admin explanation. |

## Detailed changes

### Dosen 1 — Pengumpulan tidak muncul di daftar penilaian

**Problem.** The grading hub counts submissions with a raw aggregate
(`cases.repository.ts#countSubmissions`, `SELECT count(*) FROM submissions WHERE case_study_id = $1`),
but the detail list was built from the class roster:

```sql
FROM case_studies cs
JOIN topics t ...
JOIN classes c ON c.id = t.class_id AND c.is_active = true
JOIN enrollments e ON e.class_id = t.class_id AND e.status = 'ACTIVE'
JOIN users u ON u.id = e.student_id AND u.status = 'ACTIVE'
LEFT JOIN submissions s ON s.case_study_id = cs.id AND s.student_id = u.id
```

The answer was stored, but if the enrollment was later deactivated (`LEFT`), the student
account deactivated, or the class archived, the row was filtered out by the inner joins.
The aggregate still counted it, producing the reported “2 pengumpulan” on the hub and an
empty/partial list on the detail screen.

**Fix** (`backend/src/modules/assessments/assessments.repository.ts#listCaseStudents`):

- `target` CTE resolves the case → class → deadline (`before_deadline`).
- `candidates` CTE is `UNION` of two sources:
  - every enrollment of the target class (active or not — status is evaluated later), and
  - every student with a stored submission for this case.
- The final query joins `users`, a `LEFT JOIN enrollments` and a `LEFT JOIN submissions`,
  and keeps a row when either:
  - `s.id IS NOT NULL` — an answer exists, **always listed**, or
  - the enrollment/account/class are active **and** the student currently has an active
    Kinesthetic session (the previous roster semantics for students who have not answered).
- Ordering by `full_name` is unchanged.

Because the list now starts from the stored submissions, the aggregate count used by the
grading tab and the number of listed answers are consistent by construction.

**Verification.**
- New backend integration test `keeps submitted answers listed after the enrollment and account are disabled`:
  two Kinesthetic students submit; the first student's enrollment is set to `LEFT` and the
  account to `INACTIVE`; the lecturer list still returns both `submissionId`s and the case
  payload `submissionCount` equals the number of listed answers.
- Existing tests still cover relogin visibility, derived statuses, grading, and deadline rows.
- Device verification: seeded lecturer (`1990010001`), class `IBD-01`, topic
  “Normalisasi basis data”, case study, and 2 Kinesthetic submissions. Instrumented test
  `CaptureScreensTest#test20_verifyGradingSubmissionsV2` drives the real screens:
  - grading hub: status `2 pengumpulan`;
  - detail list: `2/2`, “Terkumpul • 2 menunggu penilaian”, both students listed;
  - grading form: the submitted answer text is shown for the selected student.

### Sistem 1 — Label login `NIM/NIDN`

**Problem.** The identity field read `Identitas login` with placeholder
`NIM / NIP / NIDN atau admin` plus a helper line about the 10–11 digit rule.

**Fix.**
- `auth_login_id_label` → `NIM/NIDN` (ID and `values-en`).
- `auth_login_id_placeholder` → `NIM / NIDN` (ID and `values-en`).
- `auth_login_id_helper` removed from both locales; `FieldHelper` call and import removed
  from `LoginScreen.kt`.
- `auth_login_hint` (below the submit button) now reads
  `Mahasiswa dan dosen masuk dengan NIM/NIDN. Admin masuk dengan username admin.`
- Login validation is unchanged: non-admin identities must still be 10–11 digits, admin
  still logs in with `admin`.

**Verification.**
- `LoginScreenTest` reads the placeholder from resources instead of hardcoding the old text.
- Device screenshot `qa_v2_login` (OCR): `NIM/NIDN`, `NIM / NIDN`, no helper line under the
  field.

## Test evidence

Backend (`backend/`, Node 24):

```text
npm run typecheck  -> pass
npm run lint       -> pass
DATABASE_URL=postgresql://eduvark:eduvark@localhost:5432/eduvark_test npx vitest run
  Test Files 15 passed (15)
  Tests      94 passed (94)
```

Android:

```text
./gradlew testDebugUnitTest        -> BUILD SUCCESSFUL
./gradlew lintDebug                -> BUILD SUCCESSFUL
./gradlew assembleDebug            -> BUILD SUCCESSFUL
./gradlew assembleDebugAndroidTest -> BUILD SUCCESSFUL
```

Instrumented on the connected Xiaomi `2312FPCA6G` (`com.eduvark.app.debug`), backend on
`adb reverse tcp:3000 tcp:3000`:

```text
CaptureScreensTest#test20_verifyGradingSubmissionsV2  -> OK
CaptureScreensTest#test21_verifyLoginLabelsV2         -> OK
```

## Deployment note

The production backend at `https://pam.sequein.my.id` only receives these changes after the
branch is merged and deployed from `main`. Until then, local/QA builds must run the API from
this branch so the grading list fix is active.
