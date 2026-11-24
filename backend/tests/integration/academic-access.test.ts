import { randomUUID } from 'node:crypto';
import request from 'supertest';
import { afterAll, beforeEach, describe, expect, it } from 'vitest';
import { createApp } from '../../src/app';
import { getPool } from '../../src/db/client';
import { resetLoginThrottle } from '../../src/lib/login-throttle';
import { resetPasswordChangeThrottle } from '../../src/lib/password-change-throttle';
import {
  closeDb,
  createAdmin,
  createClass,
  createUser,
  resetDb,
  TEST_ADMIN_LOGIN_ID,
} from '../helpers/db';
import {
  createEnrollment,
  createMaterialLink,
  createTeachingAssignment,
  createTopic,
} from '../helpers/fixtures';
import { bearer, loginAs } from '../helpers/api';

const app = createApp();
const describeDb = process.env.DATABASE_URL ? describe : describe.skip;

const STUDENT_PASSWORD = 'StudentPass123';
const LECTURER_PASSWORD = 'LecturerPass123';
const ADMIN_PASSWORD = 'AdminPass1234';

let classId: string;
let otherClassId: string;
let lecturerId: string;
let adminId: string;
let studentId: string;
let otherStudentId: string;
let topicId: string;

async function loginStudent(loginId: string, vark = 'VISUAL') {
  const session = await loginAs(app, loginId, STUDENT_PASSWORD);
  const selected = await request(app)
    .post('/api/v1/auth/vark')
    .set('Authorization', bearer(session.accessToken))
    .send({ vark })
    .expect(200);
  expect(selected.body.data.activeVark).toBe(vark);
  return session;
}

async function createCaseOnly(): Promise<string> {
  const result = await getPool().query<{ id: string }>(
    `
      INSERT INTO case_studies (topic_id, created_by, instructions, deadline_at)
      VALUES ($1, $2, 'Kerjakan studi kasus', now() + interval '1 day')
      RETURNING id
    `,
    [topicId, lecturerId],
  );
  return result.rows[0]!.id;
}

async function submitCase(accessToken: string): Promise<string> {
  const response = await request(app)
    .post('/api/v1/topics/' + topicId + '/case/submission')
    .set('Authorization', bearer(accessToken))
    .send({ answerText: 'Jawaban studi kasus untuk pengujian akses.' })
    .expect(201);
  return response.body.data.submissionId as string;
}

async function sessionRow(userId: string) {
  const result = await getPool().query<{
    id: string;
    expires_at: Date;
    created_at: Date;
    last_seen_at: Date;
    is_active: boolean;
  }>(
    `
      SELECT id, expires_at, created_at, last_seen_at, is_active
      FROM auth_sessions
      WHERE user_id = $1 AND is_active = true
      ORDER BY created_at DESC
      LIMIT 1
    `,
    [userId],
  );
  return result.rows[0] ?? null;
}

async function activeSessionCount(userId: string): Promise<number> {
  const result = await getPool().query<{ count: string }>(
    'SELECT count(*)::text AS count FROM auth_sessions WHERE user_id = $1 AND is_active = true',
    [userId],
  );
  return Number(result.rows[0]?.count ?? '0');
}

function expectPasswordGuard(body: { error?: { code?: string } }) {
  expect(body.error?.code).toBe('PASSWORD_CHANGE_REQUIRED');
}

describeDb('academic access and session integration', () => {
  beforeEach(async () => {
    await resetDb();
    resetLoginThrottle();
    resetPasswordChangeThrottle();

    classId = await createClass();
    // Only one ACTIVE class is allowed in the MVP schema; a second class is
    // created inactive purely to place a cross-class student.
    otherClassId = (
      await getPool().query<{ id: string }>(
        `
          INSERT INTO classes (code, name, semester, is_active)
          VALUES ('IBD-02', 'Kelas Lain', '2026-1', false)
          RETURNING id
        `,
      )
    ).rows[0]!.id;

    lecturerId = await createUser({
      loginId: '1990010001',
      password: LECTURER_PASSWORD,
      role: 'LECTURER',
    });
    await createUser({
      loginId: '1990010002',
      password: LECTURER_PASSWORD,
      role: 'LECTURER',
    });
    adminId = await createAdmin();
    studentId = await createUser({
      loginId: '2025000001',
      password: STUDENT_PASSWORD,
      role: 'STUDENT',
    });
    otherStudentId = await createUser({
      loginId: '2025000002',
      password: STUDENT_PASSWORD,
      role: 'STUDENT',
    });

    await createTeachingAssignment(classId, lecturerId);
    await createEnrollment(classId, studentId);
    await createEnrollment(classId, otherStudentId);
    await createEnrollment(otherClassId, otherStudentId);

    topicId = await createTopic({
      classId,
      createdBy: lecturerId,
      title: 'Topik Akses Akademik',
      status: 'PUBLISHED',
    });
    await createMaterialLink({
      topicId,
      uploadedBy: lecturerId,
      vark: 'VISUAL',
      title: 'Materi Visual',
      url: 'https://example.test/materi-visual',
    });
  });

  afterAll(async () => {
    await closeDb();
  });

  it('holds student academic access while a password change is required and still allows change/logout/refresh', async () => {
    await createUser({
      loginId: '2025000099',
      password: 'TempPass123',
      role: 'STUDENT',
      mustChangePassword: true,
    });
    await createEnrollment(classId, (
      await getPool().query<{ id: string }>(
        "SELECT id FROM users WHERE login_id = '2025000099'",
      )
    ).rows[0]!.id);

    const session = await loginAs(app, '2025000099', 'TempPass123');
    expect(session.user.mustChangePassword).toBe(true);

    const guarded = [
      { method: 'get' as const, path: '/api/v1/topics' },
      { method: 'get' as const, path: '/api/v1/topics/' + topicId },
      { method: 'get' as const, path: '/api/v1/topics/' + topicId + '/materials' },
      { method: 'get' as const, path: '/api/v1/student/dashboard' },
      { method: 'get' as const, path: '/api/v1/topics/' + topicId + '/case' },
      { method: 'get' as const, path: '/api/v1/topics/' + topicId + '/feedback' },
      { method: 'put' as const, path: '/api/v1/topics/' + topicId + '/feedback' },
      { method: 'get' as const, path: '/api/v1/submissions/' + randomUUID() },
    ];
    for (const route of guarded) {
      const response = await (request(app))[route.method](route.path)
        .set('Authorization', bearer(session.accessToken));
      expect(response.status).toBe(403);
      expectPasswordGuard(response.body);
    }

    // Session maintenance stays available while the flag is active.
    const refreshed = await request(app)
      .post('/api/v1/auth/refresh')
      .send({ refreshToken: session.refreshToken })
      .expect(200);
    expect(refreshed.body.data.accessToken).toBeTruthy();

    const changed = await request(app)
      .post('/api/v1/auth/change-password')
      .set('Authorization', bearer(session.accessToken))
      .send({
        currentPassword: 'TempPass123',
        newPassword: 'StudentPass456',
        confirmPassword: 'StudentPass456',
      })
      .expect(204);
    expect(changed.body).toEqual({});

    // The successful change revokes the old session; login is required again.
    const stale = await request(app)
      .get('/api/v1/topics')
      .set('Authorization', bearer(session.accessToken))
      .expect(401);
    expect(['SESSION_REVOKED', 'SESSION_INVALID']).toContain(stale.body.error.code);

    const relogin = await loginAs(app, '2025000099', 'StudentPass456');
    expect(relogin.user.mustChangePassword).toBe(false);
    const beforeVark = await request(app)
      .get('/api/v1/topics')
      .set('Authorization', bearer(relogin.accessToken))
      .expect(428);
    expect(beforeVark.body.error.code).toBe('VARK_REQUIRED');
    await request(app)
      .post('/api/v1/auth/vark')
      .set('Authorization', bearer(relogin.accessToken))
      .send({ vark: 'VISUAL' })
      .expect(200);
    await request(app)
      .get('/api/v1/topics')
      .set('Authorization', bearer(relogin.accessToken))
      .expect(200);
  });

  it('holds lecturer and admin academic access while a password change is required and still allows logout', async () => {
    await createUser({
      loginId: '1990010099',
      password: 'TempPass123',
      role: 'LECTURER',
      mustChangePassword: true,
    });
    await createUser({
      loginId: '01401400002',
      password: 'AdminPass1234',
      role: 'ADMIN',
      mustChangePassword: true,
    });
    const caseId = await createCaseOnly();

    const tempLecturer = await loginAs(app, '1990010099', 'TempPass123');
    expect(tempLecturer.user.mustChangePassword).toBe(true);
    const lecturerRoutes = [
      { method: 'get' as const, path: '/api/v1/lecturer/topics' },
      { method: 'post' as const, path: '/api/v1/lecturer/topics' },
      {
        method: 'get' as const,
        path: '/api/v1/lecturer/cases/' + caseId + '/submissions',
      },
      {
        method: 'get' as const,
        path: '/api/v1/lecturer/analytics/topics/' + topicId,
      },
      {
        method: 'post' as const,
        path: '/api/v1/submissions/' + randomUUID() + '/assessment',
      },
    ];
    for (const route of lecturerRoutes) {
      const response = await (request(app))[route.method](route.path)
        .set('Authorization', bearer(tempLecturer.accessToken));
      expect(response.status).toBe(403);
      expectPasswordGuard(response.body);
    }

    // Logout remains reachable while the password flag is active.
    await request(app)
      .post('/api/v1/auth/logout')
      .set('Authorization', bearer(tempLecturer.accessToken))
      .expect(204);
    await request(app)
      .get('/api/v1/lecturer/topics')
      .set('Authorization', bearer(tempLecturer.accessToken))
      .expect(401);

    const tempAdmin = await loginAs(app, '01401400002', 'AdminPass1234');
    expect(tempAdmin.user.mustChangePassword).toBe(true);
    const adminGuarded = await request(app)
      .get('/api/v1/admin/users')
      .set('Authorization', bearer(tempAdmin.accessToken))
      .expect(403);
    expectPasswordGuard(adminGuarded.body);

    await request(app)
      .post('/api/v1/auth/change-password')
      .set('Authorization', bearer(tempAdmin.accessToken))
      .send({
        currentPassword: 'AdminPass1234',
        newPassword: 'AdminPass5678',
        confirmPassword: 'AdminPass5678',
      })
      .expect(204);

    const adminRelogin = await loginAs(app, '01401400002', 'AdminPass5678');
    await request(app)
      .get('/api/v1/admin/users')
      .set('Authorization', bearer(adminRelogin.accessToken))
      .expect(200);
  });

  it('rejects VARK outside the four choices and switching it inside one active session', async () => {
    const session = await loginAs(app, '2025000001', STUDENT_PASSWORD);

    const invalid = await request(app)
      .post('/api/v1/auth/vark')
      .set('Authorization', bearer(session.accessToken))
      .send({ vark: 'OLFACTORY' })
      .expect(422);
    expect(invalid.body.error.code).toBe('VALIDATION_ERROR');

    await request(app)
      .post('/api/v1/auth/vark')
      .set('Authorization', bearer(session.accessToken))
      .send({ vark: 'VISUAL' })
      .expect(200);

    const switched = await request(app)
      .post('/api/v1/auth/vark')
      .set('Authorization', bearer(session.accessToken))
      .send({ vark: 'AURAL' })
      .expect(409);
    expect(switched.body.error.code).toBe('VARK_ALREADY_SET');
  });

  it('closes a graded student answer when the enrollment is LEFT and restores it on reactivation', async () => {
    await createCaseOnly();
    const student = await loginStudent('2025000001', 'KINESTHETIC');
    const submissionId = await submitCase(student.accessToken);

    // AT-20 diperluas: jawaban sudah dinilai final, sehingga pencabutan LEFT
    // harus menutup jawaban DAN nilai, bukan hanya teks jawaban.
    const lecturer = await loginAs(app, '1990010001', LECTURER_PASSWORD);
    const graded = await request(app)
      .post('/api/v1/submissions/' + submissionId + '/assessment')
      .set('Authorization', bearer(lecturer.accessToken))
      .send({ score: 88, comment: 'Analisis memadai.' })
      .expect(201);
    expect(graded.body.data.score).toBe(88);

    const owned = await request(app)
      .get('/api/v1/submissions/' + submissionId)
      .set('Authorization', bearer(student.accessToken))
      .expect(200);
    expect(owned.body.data.answerText).toContain('pengujian akses');
    expect(owned.body.data.score).toBe(88);
    expect(owned.body.data.comment).toBe('Analisis memadai.');
    expect(owned.body.data.status).toBe('SUDAH_DINILAI');

    await getPool().query(
      "UPDATE enrollments SET status = 'LEFT', left_at = now() WHERE class_id = $1 AND student_id = $2",
      [classId, studentId],
    );
    const account = await getPool().query<{ status: string }>(
      'SELECT status FROM users WHERE id = $1',
      [studentId],
    );
    expect(account.rows[0]?.status).toBe('ACTIVE');

    const blocked = await request(app)
      .get('/api/v1/submissions/' + submissionId)
      .set('Authorization', bearer(student.accessToken))
      .expect(403);
    expect(blocked.body.error.code).toBe('ENROLLMENT_INACTIVE');
    expect(blocked.body.data).toBeUndefined();
    expect(blocked.body.error.answerText).toBeUndefined();
    expect(blocked.body.error.score).toBeUndefined();

    // The authorised lecturer keeps reading the stored history while LEFT.
    const lecturerRead = await request(app)
      .get('/api/v1/submissions/' + submissionId)
      .set('Authorization', bearer(lecturer.accessToken))
      .expect(200);
    expect(lecturerRead.body.data.answerText).toContain('pengujian akses');
    expect(lecturerRead.body.data.score).toBe(88);
    expect(lecturerRead.body.data.comment).toBe('Analisis memadai.');

    await getPool().query(
      "UPDATE enrollments SET status = 'ACTIVE', left_at = NULL WHERE class_id = $1 AND student_id = $2",
      [classId, studentId],
    );
    const restored = await request(app)
      .get('/api/v1/submissions/' + submissionId)
      .set('Authorization', bearer(student.accessToken))
      .expect(200);
    expect(restored.body.data.answerText).toContain('pengujian akses');
    expect(restored.body.data.score).toBe(88);
    expect(restored.body.data.comment).toBe('Analisis memadai.');
  });

  it('rejects answer detail for other students, another class, an unassigned lecturer, and admin', async () => {
    await createCaseOnly();
    const student = await loginStudent('2025000001', 'KINESTHETIC');
    const submissionId = await submitCase(student.accessToken);

    const otherSameClass = await loginStudent('2025000002', 'KINESTHETIC');
    const sameClassRead = await request(app)
      .get('/api/v1/submissions/' + submissionId)
      .set('Authorization', bearer(otherSameClass.accessToken))
      .expect(403);
    expect(sameClassRead.body.error.code).toBe('SUBMISSION_FORBIDDEN');
    expect(sameClassRead.body.data).toBeUndefined();

    // A cross-class student is not the owner either: data stays hidden.
    await getPool().query(
      "UPDATE enrollments SET status = 'LEFT', left_at = now() WHERE class_id = $1 AND student_id = $2",
      [classId, otherStudentId],
    );
    const crossClassRead = await request(app)
      .get('/api/v1/submissions/' + submissionId)
      .set('Authorization', bearer(otherSameClass.accessToken))
      .expect(403);
    expect(crossClassRead.body.error.code).toBe('SUBMISSION_FORBIDDEN');

    const outsideLecturer = await loginAs(app, '1990010002', LECTURER_PASSWORD);
    const lecturerRead = await request(app)
      .get('/api/v1/submissions/' + submissionId)
      .set('Authorization', bearer(outsideLecturer.accessToken))
      .expect(403);
    expect(lecturerRead.body.error.code).toBe('CLASS_FORBIDDEN');

    const admin = await loginAs(app, TEST_ADMIN_LOGIN_ID, ADMIN_PASSWORD);
    const adminRead = await request(app)
      .get('/api/v1/submissions/' + submissionId)
      .set('Authorization', bearer(admin.accessToken))
      .expect(403);
    expect(adminRead.body.error.code).toBe('FORBIDDEN');
    expect(adminRead.body.data).toBeUndefined();

    const adminAssessment = await request(app)
      .post('/api/v1/submissions/' + submissionId + '/assessment')
      .set('Authorization', bearer(admin.accessToken))
      .send({ score: 90 })
      .expect(403);
    expect(adminAssessment.body.error.code).toBe('FORBIDDEN');
    expect(adminId).toBeTruthy();
  });

  it('rejects the access token after logout and rotates refresh tokens on demand', async () => {
    const session = await loginAs(app, '1990010001', LECTURER_PASSWORD);
    await request(app)
      .get('/api/v1/lecturer/topics')
      .set('Authorization', bearer(session.accessToken))
      .expect(200);

    await request(app)
      .post('/api/v1/auth/logout')
      .set('Authorization', bearer(session.accessToken))
      .expect(204);

    const afterLogout = await request(app)
      .get('/api/v1/lecturer/topics')
      .set('Authorization', bearer(session.accessToken))
      .expect(401);
    expect(['SESSION_REVOKED', 'SESSION_INVALID']).toContain(
      afterLogout.body.error.code,
    );

    const refreshAfterLogout = await request(app)
      .post('/api/v1/auth/refresh')
      .send({ refreshToken: session.refreshToken })
      .expect(401);
    expect(refreshAfterLogout.body.error.code).toBe('REFRESH_INVALID');
  });

  it('enforces idle and absolute limits without extending the absolute limit on refresh', async () => {
    const first = await loginAs(app, '1990010001', LECTURER_PASSWORD);
    const initial = await sessionRow(lecturerId);
    expect(initial).not.toBeNull();
    const absoluteMs =
      new Date(initial!.expires_at).getTime() - new Date(initial!.created_at).getTime();
    // Default SESSION_ABSOLUTE_TTL is 12h; refresh must not push it further.
    expect(absoluteMs).toBeGreaterThan(11.5 * 3600 * 1000);
    expect(absoluteMs).toBeLessThan(12.5 * 3600 * 1000);

    const refreshed = await request(app)
      .post('/api/v1/auth/refresh')
      .send({ refreshToken: first.refreshToken })
      .expect(200);
    const afterRefresh = await sessionRow(lecturerId);
    expect(new Date(afterRefresh!.expires_at).getTime()).toBe(
      new Date(initial!.expires_at).getTime(),
    );
    await request(app)
      .get('/api/v1/lecturer/topics')
      .set('Authorization', bearer(refreshed.body.data.accessToken))
      .expect(200);

    // Idle limit: last_seen_at older than SESSION_IDLE_TTL (default 8h).
    await getPool().query(
      "UPDATE auth_sessions SET last_seen_at = now() - interval '9 hours' WHERE user_id = $1 AND is_active = true",
      [lecturerId],
    );
    const idle = await request(app)
      .get('/api/v1/lecturer/topics')
      .set('Authorization', bearer(refreshed.body.data.accessToken))
      .expect(401);
    expect(idle.body.error.code).toBe('SESSION_EXPIRED');
    expect(await activeSessionCount(lecturerId)).toBe(0);

    // Absolute limit: expired expires_at is rejected and the session is revoked.
    // created_at is moved as well because ck_auth_expiry requires
    // expires_at > created_at.
    const third = await loginAs(app, '1990010001', LECTURER_PASSWORD);
    await getPool().query(
      `UPDATE auth_sessions
          SET created_at = now() - interval '13 hours',
              expires_at = now() - interval '1 second'
        WHERE user_id = $1 AND is_active = true`,
      [lecturerId],
    );
    const expiredAccess = await request(app)
      .get('/api/v1/lecturer/topics')
      .set('Authorization', bearer(third.accessToken))
      .expect(401);
    expect(expiredAccess.body.error.code).toBe('SESSION_EXPIRED');
    const expiredRefresh = await request(app)
      .post('/api/v1/auth/refresh')
      .send({ refreshToken: third.refreshToken })
      .expect(401);
    expect(['REFRESH_INVALID', 'SESSION_EXPIRED']).toContain(
      expiredRefresh.body.error.code,
    );
  });

  it('does not resurrect a live session when refresh races with revocation', async () => {
    for (let attempt = 0; attempt < 3; attempt += 1) {
      await getPool().query('DELETE FROM auth_sessions WHERE user_id = $1', [lecturerId]);
      const session = await loginAs(app, '1990010001', LECTURER_PASSWORD);

      const [refreshResult, logoutResult] = await Promise.all([
        request(app)
          .post('/api/v1/auth/refresh')
          .send({ refreshToken: session.refreshToken }),
        request(app)
          .post('/api/v1/auth/logout')
          .set('Authorization', bearer(session.accessToken)),
      ]);

      expect(logoutResult.status).toBe(204);
      expect([200, 401]).toContain(refreshResult.status);
      expect(await activeSessionCount(lecturerId)).toBe(0);

      const stale = await request(app)
        .get('/api/v1/lecturer/topics')
        .set('Authorization', bearer(session.accessToken))
        .expect(401);
      expect(['SESSION_REVOKED', 'SESSION_INVALID']).toContain(stale.body.error.code);

      if (refreshResult.status === 200) {
        const rotatedAccess = refreshResult.body.data.accessToken as string;
        const rotatedRefresh = refreshResult.body.data.refreshToken as string;
        await request(app)
          .get('/api/v1/lecturer/topics')
          .set('Authorization', bearer(rotatedAccess))
          .expect(401);
        await request(app)
          .post('/api/v1/auth/refresh')
          .send({ refreshToken: rotatedRefresh })
          .expect(401);
      }
    }
  });

  it('applies the password policy: current verified, 6-200 chars, confirmation, no reuse, sessions revoked', async () => {
    await loginAs(app, '2025000001', STUDENT_PASSWORD);

    const wrongCurrent = await request(app)
      .post('/api/v1/auth/change-password')
      .set('Authorization', bearer((await loginAs(app, '2025000001', STUDENT_PASSWORD)).accessToken))
      .send({
        currentPassword: 'WrongPass123',
        newPassword: 'StudentPass456',
        confirmPassword: 'StudentPass456',
      })
      .expect(422);
    expect(wrongCurrent.body.error.code).toBe('INVALID_PASSWORD');

    // Every failed attempt below consumes separate sessions, so acquire one and
    // reuse it for the remaining validation assertions.
    const session = await loginAs(app, '2025000001', STUDENT_PASSWORD);
    const tooShort = await request(app)
      .post('/api/v1/auth/change-password')
      .set('Authorization', bearer(session.accessToken))
      .send({
        currentPassword: STUDENT_PASSWORD,
        newPassword: 'Abc12',
        confirmPassword: 'Abc12',
      })
      .expect(422);
    expect(tooShort.body.error.code).toBe('VALIDATION_ERROR');

    const tooLong = await request(app)
      .post('/api/v1/auth/change-password')
      .set('Authorization', bearer(session.accessToken))
      .send({
        currentPassword: STUDENT_PASSWORD,
        newPassword: 'a'.repeat(201),
        confirmPassword: 'a'.repeat(201),
      })
      .expect(422);
    expect(tooLong.body.error.code).toBe('VALIDATION_ERROR');

    const mismatch = await request(app)
      .post('/api/v1/auth/change-password')
      .set('Authorization', bearer(session.accessToken))
      .send({
        currentPassword: STUDENT_PASSWORD,
        newPassword: 'StudentPass456',
        confirmPassword: 'StudentPass457',
      })
      .expect(422);
    expect(mismatch.body.error.code).toBe('VALIDATION_ERROR');

    const reused = await request(app)
      .post('/api/v1/auth/change-password')
      .set('Authorization', bearer(session.accessToken))
      .send({
        currentPassword: STUDENT_PASSWORD,
        newPassword: STUDENT_PASSWORD,
        confirmPassword: STUDENT_PASSWORD,
      })
      .expect(422);
    expect(reused.body.error.code).toBe('PASSWORD_REUSED');

    // Minimum length accepted (6 characters).
    await request(app)
      .post('/api/v1/auth/change-password')
      .set('Authorization', bearer(session.accessToken))
      .send({
        currentPassword: STUDENT_PASSWORD,
        newPassword: 'Abc123',
        confirmPassword: 'Abc123',
      })
      .expect(204);

    const revoked = await request(app)
      .get('/api/v1/topics')
      .set('Authorization', bearer(session.accessToken))
      .expect(401);
    expect(['SESSION_REVOKED', 'SESSION_INVALID']).toContain(revoked.body.error.code);
    const oldRefresh = await request(app)
      .post('/api/v1/auth/refresh')
      .send({ refreshToken: session.refreshToken })
      .expect(401);
    expect(oldRefresh.body.error.code).toBe('REFRESH_INVALID');
    const oldLogin = await request(app)
      .post('/api/v1/auth/login')
      .send({ loginId: '2025000001', password: STUDENT_PASSWORD })
      .expect(401);
    expect(oldLogin.body.error.code).toBe('INVALID_CREDENTIALS');

    // Maximum length accepted (200 characters).
    const six = await loginAs(app, '2025000001', 'Abc123');
    await request(app)
      .post('/api/v1/auth/change-password')
      .set('Authorization', bearer(six.accessToken))
      .send({
        currentPassword: 'Abc123',
        newPassword: 'z'.repeat(200),
        confirmPassword: 'z'.repeat(200),
      })
      .expect(204);
    const twoHundred = await loginAs(app, '2025000001', 'z'.repeat(200));
    expect(twoHundred.user.mustChangePassword).toBe(false);
  });
});
