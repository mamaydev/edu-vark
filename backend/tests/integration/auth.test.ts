import express from 'express';
import request from 'supertest';
import { afterAll, beforeEach, describe, expect, it } from 'vitest';
import { createApp } from '../../src/app';
import { errorHandler } from '../../src/middleware/error-handler';
import { authenticate, requireVark } from '../../src/middleware/auth';
import { resetLoginThrottle } from '../../src/lib/login-throttle';
import { resetPasswordChangeThrottle } from '../../src/lib/password-change-throttle';
import {
  activeSessionCount,
  closeDb,
  createAdmin,
  createClass,
  createUser,
  resetDb,
  TEST_ADMIN_LOGIN_ID,
  TEST_ADMIN_PASSWORD,
} from '../helpers/db';

const app = createApp();

const gateApp = express();
gateApp.get('/protected', authenticate, requireVark, (_req, res) => {
  res.status(200).json({ data: { ok: true } });
});
gateApp.use(errorHandler);

const describeDb = process.env.DATABASE_URL ? describe : describe.skip;

function login(loginId: string, password: string) {
  return request(app).post('/api/v1/auth/login').send({ loginId, password });
}

async function loginOk(loginId: string, password: string) {
  const response = await login(loginId, password).expect(200);
  return response.body.data as {
    accessToken: string;
    refreshToken: string;
    user: { id: string; role: string; mustChangePassword: boolean };
    varkRequired: boolean;
    activeVark: string | null;
  };
}

function bearer(token: string): string {
  return 'Bearer ' + token;
}

describeDb('auth integration', () => {
  beforeEach(async () => {
    await resetDb();
    await createClass();
    resetLoginThrottle();
  });

  afterAll(async () => {
    await closeDb();
  });

  it('logs in an active student and creates one session', async () => {
    const userId = await createUser({
      loginId: '2025000001',
      password: 'StudentPass123',
      role: 'STUDENT',
    });

    const data = await loginOk('2025000001', 'StudentPass123');

    expect(data.varkRequired).toBe(true);
    expect(data.activeVark).toBeNull();
    expect(data.user.role).toBe('STUDENT');
    expect(await activeSessionCount(userId)).toBe(1);
  });

  it('rejects invalid credentials and inactive accounts', async () => {
    await createUser({
      loginId: '1990010010',
      password: 'InactivePass123',
      role: 'STUDENT',
      status: 'INACTIVE',
    });

    await login('9999999999', 'WhateverPass123').expect(401);
    const inactive = await login('1990010010', 'InactivePass123').expect(403);
    expect(inactive.body.error.code).toBe('ACCOUNT_INACTIVE');
  });

  it('rejects a non-academic identifier for non-admin accounts', async () => {
    await createUser({
      loginId: 'legacy1',
      password: 'StudentPass123',
      role: 'STUDENT',
    });

    const response = await login('legacy1', 'StudentPass123').expect(401);
    expect(response.body.error.code).toBe('INVALID_CREDENTIALS');
  });

  it('supports staff login/logout and revokes staff sessions', async () => {
    await createUser({
      loginId: '1990010001',
      password: 'LecturerPass123',
      role: 'LECTURER',
    });
    await createAdmin();

    const lecturer = await loginOk('1990010001', 'LecturerPass123');
    const admin = await loginOk(TEST_ADMIN_LOGIN_ID, TEST_ADMIN_PASSWORD);
    expect(lecturer.varkRequired).toBe(false);
    expect(admin.varkRequired).toBe(false);

    await request(app)
      .post('/api/v1/auth/logout')
      .set('Authorization', bearer(lecturer.accessToken))
      .expect(204);
    await request(app)
      .get('/api/v1/auth/me')
      .set('Authorization', bearer(lecturer.accessToken))
      .expect(401);

    const lecturerAgain = await loginOk('1990010001', 'LecturerPass123');
    await request(app)
      .get('/api/v1/auth/me')
      .set('Authorization', bearer(lecturer.accessToken))
      .expect(401);
    await request(app)
      .get('/api/v1/auth/me')
      .set('Authorization', bearer(lecturerAgain.accessToken))
      .expect(200);

    await request(app)
      .post('/api/v1/auth/logout')
      .set('Authorization', bearer(admin.accessToken))
      .expect(204);
    await request(app)
      .get('/api/v1/auth/me')
      .set('Authorization', bearer(admin.accessToken))
      .expect(401);
  });

  it('revokes the previous session when a new login succeeds', async () => {
    await createUser({
      loginId: '2025000002',
      password: 'StudentPass123',
      role: 'STUDENT',
    });

    const first = await loginOk('2025000002', 'StudentPass123');
    const second = await loginOk('2025000002', 'StudentPass123');

    await request(app)
      .get('/api/v1/auth/me')
      .set('Authorization', bearer(first.accessToken))
      .expect(401);

    await request(app)
      .get('/api/v1/auth/me')
      .set('Authorization', bearer(second.accessToken))
      .expect(200);
  });

  it('rotates refresh tokens and rejects the old refresh token', async () => {
    await createUser({
      loginId: '2025000003',
      password: 'StudentPass123',
      role: 'STUDENT',
    });

    const first = await loginOk('2025000003', 'StudentPass123');
    const refreshed = await request(app)
      .post('/api/v1/auth/refresh')
      .send({ refreshToken: first.refreshToken })
      .expect(200);

    expect(refreshed.body.data.refreshToken).not.toBe(first.refreshToken);

    await request(app)
      .post('/api/v1/auth/refresh')
      .send({ refreshToken: first.refreshToken })
      .expect(401);

    await request(app)
      .get('/api/v1/auth/me')
      .set('Authorization', bearer(refreshed.body.data.accessToken))
      .expect(200);
  });

  it('logs out and invalidates the current session', async () => {
    await createUser({
      loginId: '2025000004',
      password: 'StudentPass123',
      role: 'STUDENT',
    });

    const data = await loginOk('2025000004', 'StudentPass123');

    await request(app)
      .post('/api/v1/auth/logout')
      .set('Authorization', bearer(data.accessToken))
      .expect(204);

    await request(app)
      .get('/api/v1/auth/me')
      .set('Authorization', bearer(data.accessToken))
      .expect(401);
  });

  it('locks VARK after the first selection and exposes it in /me', async () => {
    await createUser({
      loginId: '2025000005',
      password: 'StudentPass123',
      role: 'STUDENT',
    });

    const data = await loginOk('2025000005', 'StudentPass123');

    await request(app)
      .post('/api/v1/auth/vark')
      .set('Authorization', bearer(data.accessToken))
      .send({ vark: 'VISUAL' })
      .expect(200);

    const duplicate = await request(app)
      .post('/api/v1/auth/vark')
      .set('Authorization', bearer(data.accessToken))
      .send({ vark: 'AURAL' })
      .expect(409);
    expect(duplicate.body.error.code).toBe('VARK_ALREADY_SET');

    const me = await request(app)
      .get('/api/v1/auth/me')
      .set('Authorization', bearer(data.accessToken))
      .expect(200);
    expect(me.body.data.activeVark).toBe('VISUAL');
  });

  it('rejects VARK selection for staff', async () => {
    await createAdmin();

    const data = await loginOk(TEST_ADMIN_LOGIN_ID, TEST_ADMIN_PASSWORD);

    const response = await request(app)
      .post('/api/v1/auth/vark')
      .set('Authorization', bearer(data.accessToken))
      .send({ vark: 'VISUAL' })
      .expect(403);
    expect(response.body.error.code).toBe('FORBIDDEN');
  });

  it('blocks academic endpoints until VARK is selected', async () => {
    await createUser({
      loginId: '2025000006',
      password: 'StudentPass123',
      role: 'STUDENT',
    });

    const data = await loginOk('2025000006', 'StudentPass123');

    const blocked = await request(gateApp)
      .get('/protected')
      .set('Authorization', bearer(data.accessToken))
      .expect(428);
    expect(blocked.body.error.code).toBe('VARK_REQUIRED');

    await request(app)
      .post('/api/v1/auth/vark')
      .set('Authorization', bearer(data.accessToken))
      .send({ vark: 'READ_WRITE' })
      .expect(200);

    await request(gateApp)
      .get('/protected')
      .set('Authorization', bearer(data.accessToken))
      .expect(200);
  });

  it('blocks VARK selection until the temporary password is changed', async () => {
    await createUser({
      loginId: '2025000007',
      password: 'StudentPass123',
      role: 'STUDENT',
      mustChangePassword: true,
    });

    const data = await loginOk('2025000007', 'StudentPass123');

    const response = await request(app)
      .post('/api/v1/auth/vark')
      .set('Authorization', bearer(data.accessToken))
      .send({ vark: 'VISUAL' })
      .expect(403);
    expect(response.body.error.code).toBe('PASSWORD_CHANGE_REQUIRED');

    await request(app)
      .get('/api/v1/auth/me')
      .set('Authorization', bearer(data.accessToken))
      .expect(200);
  });

  it('accepts a 6-character password but rejects reusing the current password', async () => {
    await createUser({
      loginId: '2025000006',
      password: 'abc123',
      role: 'STUDENT',
    });

    const first = await loginOk('2025000006', 'abc123');
    await request(app)
      .post('/api/v1/auth/change-password')
      .set('Authorization', bearer(first.accessToken))
      .send({
        currentPassword: 'abc123',
        newPassword: 'xyz789',
        confirmPassword: 'xyz789',
      })
      .expect(204);

    const second = await loginOk('2025000006', 'xyz789');
    const reused = await request(app)
      .post('/api/v1/auth/change-password')
      .set('Authorization', bearer(second.accessToken))
      .send({
        currentPassword: 'xyz789',
        newPassword: 'xyz789',
        confirmPassword: 'xyz789',
      })
      .expect(422);
    expect(reused.body.error.code).toBe('PASSWORD_REUSED');

    await loginOk('2025000006', 'xyz789');
  });

  it('rejects mismatched confirmation and throttles repeated wrong passwords', async () => {
    resetPasswordChangeThrottle();
    await createUser({
      loginId: '2025000009',
      password: 'StudentPass123',
      role: 'STUDENT',
    });
    const session = await loginOk('2025000009', 'StudentPass123');

    const mismatch = await request(app)
      .post('/api/v1/auth/change-password')
      .set('Authorization', bearer(session.accessToken))
      .send({
        currentPassword: 'StudentPass123',
        newPassword: 'StudentPass456',
        confirmPassword: 'StudentPass789',
      })
      .expect(422);
    expect(mismatch.body.error.code).toBe('VALIDATION_ERROR');

    for (let attempt = 0; attempt < 5; attempt += 1) {
      const wrong = await request(app)
        .post('/api/v1/auth/change-password')
        .set('Authorization', bearer(session.accessToken))
        .send({
          currentPassword: 'WrongPass123',
          newPassword: 'StudentPass456',
          confirmPassword: 'StudentPass456',
        })
        .expect(422);
      expect(wrong.body.error.code).toBe('INVALID_PASSWORD');
    }

    const throttled = await request(app)
      .post('/api/v1/auth/change-password')
      .set('Authorization', bearer(session.accessToken))
      .send({
        currentPassword: 'StudentPass123',
        newPassword: 'StudentPass456',
        confirmPassword: 'StudentPass456',
      })
      .expect(429);
    expect(throttled.body.error.code).toBe('TOO_MANY_ATTEMPTS');
  });

  it('changes password, revokes sessions, and accepts only the new password', async () => {
    await createUser({
      loginId: '2025000008',
      password: 'StudentPass123',
      role: 'STUDENT',
    });

    const data = await loginOk('2025000008', 'StudentPass123');

    await request(app)
      .post('/api/v1/auth/change-password')
      .set('Authorization', bearer(data.accessToken))
      .send({
        currentPassword: 'StudentPass123',
        newPassword: 'StudentPass456',
        confirmPassword: 'StudentPass456',
      })
      .expect(204);

    await request(app)
      .get('/api/v1/auth/me')
      .set('Authorization', bearer(data.accessToken))
      .expect(401);

    await login('2025000008', 'StudentPass123').expect(401);
    await login('2025000008', 'StudentPass456').expect(200);
  });
});
