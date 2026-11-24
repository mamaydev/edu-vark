import request from 'supertest';
import { afterAll, beforeEach, describe, expect, it } from 'vitest';
import { createApp } from '../../src/app';
import { resetLoginThrottle } from '../../src/lib/login-throttle';
import {
  createAdmin,
  closeDb,
  createClass,
  resetDb,
  TEST_ADMIN_LOGIN_ID,
  TEST_ADMIN_PASSWORD,
} from '../helpers/db';

const app = createApp();
const describeDb = process.env.DATABASE_URL ? describe : describe.skip;

async function login(loginId: string, password: string) {
  const response = await request(app)
    .post('/api/v1/auth/login')
    .send({ loginId, password })
    .expect(200);
  return response.body.data as {
    accessToken: string;
    refreshToken: string;
    user: { id: string; role: string };
  };
}

function bearer(token: string): string {
  return 'Bearer ' + token;
}

async function adminLogin(): Promise<{ token: string; userId: string }> {
  const userId = await createAdmin();
  const data = await login(TEST_ADMIN_LOGIN_ID, TEST_ADMIN_PASSWORD);
  return { token: data.accessToken, userId };
}

async function createStudentViaApi(
  token: string,
  loginId: string,
): Promise<{
  id: string;
  loginId: string;
  enrollmentId: string | null;
  enrollmentStatus: 'ACTIVE' | 'LEFT' | null;
  classCode: string | null;
  className: string | null;
}> {
  const response = await request(app)
    .post('/api/v1/admin/users')
    .set('Authorization', bearer(token))
    .send({
      loginId,
      fullName: 'Student ' + loginId,
      role: 'STUDENT',
      temporaryPassword: 'StudentPass123',
    })
    .expect(201);
  return response.body.data;
}

describeDb('admin integration', () => {
  beforeEach(async () => {
    await resetDb();
    await createClass();
    resetLoginThrottle();
  });

  afterAll(async () => {
    await closeDb();
  });

  it('creates a student with enrollment and lists users', async () => {
    const admin = await adminLogin();
    const student = await createStudentViaApi(admin.token, '2025000101');

    expect(student.enrollmentId).toBeTruthy();
    expect(student.enrollmentStatus).toBe('ACTIVE');
    expect(student.classCode).toBe('IBD-01');
    expect(student.className).toBe('Implementasi Basis Data');

    const list = await request(app)
      .get('/api/v1/admin/users')
      .set('Authorization', bearer(admin.token))
      .expect(200);

    expect(list.body.meta.total).toBe(2);
    expect(list.body.data.some((item: { loginId: string }) => item.loginId === '2025000101')).toBe(true);

    const studentLogin = await login('2025000101', 'StudentPass123');
    expect(studentLogin.user.role).toBe('STUDENT');
  });

  it('rejects duplicate login IDs', async () => {
    const admin = await adminLogin();
    await createStudentViaApi(admin.token, '2025000102');

    const response = await request(app)
      .post('/api/v1/admin/users')
      .set('Authorization', bearer(admin.token))
      .send({
        loginId: '2025000102',
        fullName: 'Duplicate',
        role: 'STUDENT',
        temporaryPassword: 'StudentPass123',
      })
      .expect(409);

    expect(response.body.error.code).toBe('LOGIN_ID_TAKEN');
  });

  it('requires 10–11 digit academic identifiers for new accounts', async () => {
    const admin = await adminLogin();

    for (const loginId of ['202500020', '202500020012', 'student-x']) {
      const response = await request(app)
        .post('/api/v1/admin/users')
        .set('Authorization', bearer(admin.token))
        .send({
          loginId,
          fullName: 'Invalid ID',
          role: 'STUDENT',
          temporaryPassword: 'StudentPass123',
        })
        .expect(422);
      expect(response.body.error.code).toBe('VALIDATION_ERROR');
    }

    const valid = await createStudentViaApi(admin.token, '20250002001');
    expect(valid.loginId).toBe('20250002001');
  });

  it('updates a role, revokes sessions, and assigns the new role', async () => {
    const admin = await adminLogin();
    const student = await createStudentViaApi(admin.token, '2025000103');
    const studentLogin = await login('2025000103', 'StudentPass123');

    const updated = await request(app)
      .patch('/api/v1/admin/users/' + student.id)
      .set('Authorization', bearer(admin.token))
      .send({ role: 'LECTURER' })
      .expect(200);

    expect(updated.body.data.role).toBe('LECTURER');
    expect(updated.body.data.teachingAssignmentId).toBeTruthy();
    expect(updated.body.data.enrollmentId).toBeNull();

    await request(app)
      .get('/api/v1/auth/me')
      .set('Authorization', bearer(studentLogin.accessToken))
      .expect(401);

    const lecturerLogin = await login('2025000103', 'StudentPass123');
    expect(lecturerLogin.user.role).toBe('LECTURER');
  });

  it('resets a password and invalidates existing sessions', async () => {
    const admin = await adminLogin();
    const student = await createStudentViaApi(admin.token, '2025000104');
    const studentLogin = await login('2025000104', 'StudentPass123');

    await request(app)
      .post('/api/v1/admin/users/' + student.id + '/reset-password')
      .set('Authorization', bearer(admin.token))
      .send({ temporaryPassword: 'TemporaryPass123' })
      .expect(204);

    await request(app)
      .get('/api/v1/auth/me')
      .set('Authorization', bearer(studentLogin.accessToken))
      .expect(401);

    const newLogin = await login('2025000104', 'TemporaryPass123');
    expect(newLogin.user.role).toBe('STUDENT');
  });

  it('deactivates an account and blocks login', async () => {
    const admin = await adminLogin();
    const student = await createStudentViaApi(admin.token, '2025000105');

    await request(app)
      .post('/api/v1/admin/users/' + student.id + '/deactivate')
      .set('Authorization', bearer(admin.token))
      .expect(204);

    await request(app)
      .post('/api/v1/auth/login')
      .send({ loginId: '2025000105', password: 'StudentPass123' })
      .expect(403);
  });

  it('protects the last active admin', async () => {
    const admin = await adminLogin();

    const response = await request(app)
      .post('/api/v1/admin/users/' + admin.userId + '/deactivate')
      .set('Authorization', bearer(admin.token))
      .expect(409);

    expect(response.body.error.code).toBe('LAST_ADMIN_PROTECTED');
  });

  it('blocks students from admin endpoints', async () => {
    const admin = await adminLogin();
    await createStudentViaApi(admin.token, '2025000106');
    const studentLogin = await login('2025000106', 'StudentPass123');

    const response = await request(app)
      .get('/api/v1/admin/users')
      .set('Authorization', bearer(studentLogin.accessToken))
      .expect(403);

    expect(response.body.error.code).toBe('FORBIDDEN');
  });

  it('marks an enrollment as LEFT and can reactivate it', async () => {
    const admin = await adminLogin();
    const student = await createStudentViaApi(admin.token, '2025000107');

    const left = await request(app)
      .patch('/api/v1/admin/enrollments/' + student.enrollmentId)
      .set('Authorization', bearer(admin.token))
      .send({ status: 'LEFT' })
      .expect(200);

    expect(left.body.data.status).toBe('LEFT');
    expect(left.body.data.leftAt).toBeTruthy();

    const leftUser = await request(app)
      .get('/api/v1/admin/users/' + student.id)
      .set('Authorization', bearer(admin.token))
      .expect(200);
    expect(leftUser.body.data.enrollmentId).toBe(student.enrollmentId);
    expect(leftUser.body.data.enrollmentStatus).toBe('LEFT');

    const active = await request(app)
      .patch('/api/v1/admin/enrollments/' + student.enrollmentId)
      .set('Authorization', bearer(admin.token))
      .send({ status: 'ACTIVE' })
      .expect(200);

    expect(active.body.data.status).toBe('ACTIVE');
    expect(active.body.data.leftAt).toBeNull();

    const activeUser = await request(app)
      .get('/api/v1/admin/users/' + student.id)
      .set('Authorization', bearer(admin.token))
      .expect(200);
    expect(activeUser.body.data.enrollmentStatus).toBe('ACTIVE');
  });

  it('refuses to create a student without an active class', async () => {
    await resetDb();
    const adminId = await createAdmin();
    expect(adminId).toBeTruthy();

    const admin = await login(TEST_ADMIN_LOGIN_ID, TEST_ADMIN_PASSWORD);

    const response = await request(app)
      .post('/api/v1/admin/users')
      .set('Authorization', bearer(admin.accessToken))
      .send({
        loginId: '2025000199',
        fullName: 'No Class',
        role: 'STUDENT',
        temporaryPassword: 'StudentPass123',
      })
      .expect(409);

    expect(response.body.error.code).toBe('NO_ACTIVE_CLASS');
  });
});
