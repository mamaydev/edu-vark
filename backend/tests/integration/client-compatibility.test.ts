import request from 'supertest';
import { afterAll, beforeEach, describe, expect, it } from 'vitest';
import { createApp } from '../../src/app';
import { CLIENT_VERSION_HEADER } from '../../src/middleware/client-compatibility';
import {
  closeDb,
  createAdmin,
  resetDb,
  TEST_ADMIN_LOGIN_ID,
  TEST_ADMIN_PASSWORD,
} from '../helpers/db';

// Guard ditegakkan eksplisit pada NODE_ENV=test. Aplikasi dibuat setelah
// variabel di-set agar cache env tidak membawa mode longgar.
process.env.CLIENT_COMPATIBILITY_ENFORCE = '1';
process.env.CLIENT_MIN_VERSION = '2';
const app = createApp();

const describeDb = process.env.DATABASE_URL ? describe : describe.skip;
const HEADER = CLIENT_VERSION_HEADER;

function login() {
  return request(app)
    .post('/api/v1/auth/login')
    .send({ loginId: TEST_ADMIN_LOGIN_ID, password: TEST_ADMIN_PASSWORD });
}

function expectUnsupported(response: request.Response): void {
  expect(response.status).toBe(426);
  expect(response.body.error.code).toBe('CLIENT_VERSION_UNSUPPORTED');
  expect(response.body.error.details.minSupportedVersion).toBe(2);
  expect(response.body.data).toBeUndefined();
}

describeDb('client compatibility guard', () => {
  beforeEach(async () => {
    await resetDb();
    await createAdmin();
  });

  afterAll(async () => {
    await closeDb();
  });

  it('rejects requests without the client-version header before authentication', async () => {
    // Tanpa Authorization: bila guard berjalan setelah auth, status akan 401.
    const response = await login();
    expectUnsupported(response);
    expect(response.body.error.message).toContain('tidak didukung');
  });

  it('rejects version 1 (baseline APK) and malformed version values', async () => {
    for (const version of ['1', '0', 'abc', '2.0']) {
      const response = await request(app)
        .post('/api/v1/auth/login')
        .set(HEADER, version)
        .send({ loginId: TEST_ADMIN_LOGIN_ID, password: TEST_ADMIN_PASSWORD });
      expectUnsupported(response);
      expect(response.body.error.details.receivedVersion).toBe(version);
    }
  });

  it('allows version 2 through login and authenticated requests', async () => {
    const loginResponse = await request(app)
      .post('/api/v1/auth/login')
      .set(HEADER, '2')
      .send({ loginId: TEST_ADMIN_LOGIN_ID, password: TEST_ADMIN_PASSWORD })
      .expect(200);
    const accessToken = loginResponse.body.data.accessToken as string;
    expect(accessToken).toBeTruthy();

    await request(app)
      .get('/api/v1/auth/me')
      .set(HEADER, '2')
      .set('Authorization', 'Bearer ' + accessToken)
      .expect(200);
  });

  it('blocks a still-valid pre-switch access token when its request has no version signal', async () => {
    const loginResponse = await request(app)
      .post('/api/v1/auth/login')
      .set(HEADER, '2')
      .send({ loginId: TEST_ADMIN_LOGIN_ID, password: TEST_ADMIN_PASSWORD })
      .expect(200);
    const accessToken = loginResponse.body.data.accessToken as string;

    // APK baseline yang sudah terpasang memegang token ini, tetapi tidak
    // pernah mengirim header; request berikutnya harus ditolak guard 426,
    // bukan diteruskan ke kontrak data baru.
    const blocked = await request(app)
      .get('/api/v1/auth/me')
      .set('Authorization', 'Bearer ' + accessToken);
    expectUnsupported(blocked);

    // Token yang sama tetap valid bila klien kandidat mengirim sinyal versi.
    await request(app)
      .get('/api/v1/auth/me')
      .set(HEADER, '2')
      .set('Authorization', 'Bearer ' + accessToken)
      .expect(200);
  });

  it('blocks refresh without version signal', async () => {
    const loginResponse = await request(app)
      .post('/api/v1/auth/login')
      .set(HEADER, '2')
      .send({ loginId: TEST_ADMIN_LOGIN_ID, password: TEST_ADMIN_PASSWORD })
      .expect(200);
    const refreshToken = loginResponse.body.data.refreshToken as string;

    const blocked = await request(app)
      .post('/api/v1/auth/refresh')
      .send({ refreshToken });
    expectUnsupported(blocked);

    await request(app)
      .post('/api/v1/auth/refresh')
      .set(HEADER, '2')
      .send({ refreshToken })
      .expect(200);
  });

  it('keeps health endpoints reachable without the version header', async () => {
    await request(app).get('/api/v1/health').expect(200);
    await request(app).get('/health').expect(200);
  });
});
