import request from 'supertest';
import { afterAll, beforeEach, describe, expect, it } from 'vitest';
import { createApp } from '../../src/app';
import { getPool } from '../../src/db/client';
import { resetLoginThrottle } from '../../src/lib/login-throttle';
import {
  closeDb,
  createClass,
  createUser,
  resetDb,
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

let classId: string;
let lecturerId: string;
let topicId: string;

async function loginStudent(loginId: string, vark: string) {
  const session = await loginAs(app, loginId, 'StudentPass123');
  await request(app)
    .post('/api/v1/auth/vark')
    .set('Authorization', bearer(session.accessToken))
    .send({ vark })
    .expect(200);
  return session;
}

async function createStudent(loginId: string) {
  const id = await createUser({
    loginId,
    password: 'StudentPass123',
    role: 'STUDENT',
  });
  await createEnrollment(classId, id);
  return id;
}

async function feedbackCount(): Promise<number> {
  const result = await getPool().query<{ count: string }>(
    'SELECT count(*)::text AS count FROM feedback',
  );
  return Number(result.rows[0]?.count ?? '0');
}

describeDb('feedback integration', () => {
  beforeEach(async () => {
    await resetDb();
    resetLoginThrottle();
    classId = await createClass();
    lecturerId = await createUser({
      loginId: '1990010001',
      password: 'LecturerPass123',
      role: 'LECTURER',
    });
    await createTeachingAssignment(classId, lecturerId);
    await createStudent('2025000001');
    await createStudent('2025000002');
    await createStudent('2025000003');

    topicId = await createTopic({
      classId,
      createdBy: lecturerId,
      title: 'Topik Feedback',
      status: 'PUBLISHED',
    });
    await createMaterialLink({
      topicId,
      uploadedBy: lecturerId,
      vark: 'VISUAL',
      title: 'Visual',
      url: 'https://example.com/visual',
    });
    await createMaterialLink({
      topicId,
      uploadedBy: lecturerId,
      vark: 'AURAL',
      title: 'Audio',
      url: 'https://example.com/audio',
    });
  });

  afterAll(async () => {
    await closeDb();
  });

  it('starts with null feedback, then saves and updates without history', async () => {
    const student = await loginStudent('2025000001', 'VISUAL');

    const initial = await request(app)
      .get('/api/v1/topics/' + topicId + '/feedback')
      .set('Authorization', bearer(student.accessToken))
      .expect(200);
    expect(initial.body.data.value).toBeNull();

    const first = await request(app)
      .put('/api/v1/topics/' + topicId + '/feedback')
      .set('Authorization', bearer(student.accessToken))
      .send({ value: 40 })
      .expect(200);
    expect(first.body.data.value).toBe(40);

    const updated = await request(app)
      .put('/api/v1/topics/' + topicId + '/feedback')
      .set('Authorization', bearer(student.accessToken))
      .send({ value: 75 })
      .expect(200);
    expect(updated.body.data.value).toBe(75);

    expect(await feedbackCount()).toBe(1);

    const current = await request(app)
      .get('/api/v1/topics/' + topicId + '/feedback')
      .set('Authorization', bearer(student.accessToken))
      .expect(200);
    expect(current.body.data.value).toBe(75);
  });

  it('keeps feedback from different VARK categories separate', async () => {
    const visual = await loginStudent('2025000001', 'VISUAL');
    const aural = await loginStudent('2025000002', 'AURAL');

    await request(app)
      .put('/api/v1/topics/' + topicId + '/feedback')
      .set('Authorization', bearer(visual.accessToken))
      .send({ value: 75 })
      .expect(200);

    await request(app)
      .put('/api/v1/topics/' + topicId + '/feedback')
      .set('Authorization', bearer(aural.accessToken))
      .send({ value: 60 })
      .expect(200);

    expect(await feedbackCount()).toBe(2);

    const visualCurrent = await request(app)
      .get('/api/v1/topics/' + topicId + '/feedback')
      .set('Authorization', bearer(visual.accessToken))
      .expect(200);
    expect(visualCurrent.body.data.value).toBe(75);

    const auralCurrent = await request(app)
      .get('/api/v1/topics/' + topicId + '/feedback')
      .set('Authorization', bearer(aural.accessToken))
      .expect(200);
    expect(auralCurrent.body.data.value).toBe(60);
  });

  it('rejects feedback from a Kinesthetic session', async () => {
    const student = await loginStudent('2025000003', 'KINESTHETIC');

    const response = await request(app)
      .put('/api/v1/topics/' + topicId + '/feedback')
      .set('Authorization', bearer(student.accessToken))
      .send({ value: 50 })
      .expect(403);

    expect(response.body.error.code).toBe('FEEDBACK_NOT_APPLICABLE');
  });

  it('rejects values outside 0-100', async () => {
    const student = await loginStudent('2025000001', 'VISUAL');

    await request(app)
      .put('/api/v1/topics/' + topicId + '/feedback')
      .set('Authorization', bearer(student.accessToken))
      .send({ value: -1 })
      .expect(422);

    await request(app)
      .put('/api/v1/topics/' + topicId + '/feedback')
      .set('Authorization', bearer(student.accessToken))
      .send({ value: 101 })
      .expect(422);
  });

  it('returns the exact message when the category has no material', async () => {
    const emptyTopic = await createTopic({
      classId,
      createdBy: lecturerId,
      title: 'Topik Tanpa Visual',
      status: 'PUBLISHED',
      sortOrder: 1,
    });
    await createMaterialLink({
      topicId: emptyTopic,
      uploadedBy: lecturerId,
      vark: 'AURAL',
      title: 'Hanya Audio',
      url: 'https://example.com/audio-only',
    });

    const student = await loginStudent('2025000001', 'VISUAL');
    const response = await request(app)
      .get('/api/v1/topics/' + emptyTopic + '/feedback')
      .set('Authorization', bearer(student.accessToken))
      .expect(404);

    expect(response.body.error.code).toBe('MATERIAL_NOT_AVAILABLE');
    expect(response.body.error.message).toBe(
      'materi belum tersedia, harap hubungi dosen terlebih dahulu',
    );
  });

  it('requires VARK and student role', async () => {
    const noVark = await loginAs(app, '2025000001', 'StudentPass123');
    const gate = await request(app)
      .get('/api/v1/topics/' + topicId + '/feedback')
      .set('Authorization', bearer(noVark.accessToken))
      .expect(428);
    expect(gate.body.error.code).toBe('VARK_REQUIRED');

    const lecturer = await loginAs(app, '1990010001', 'LecturerPass123');
    const forbidden = await request(app)
      .get('/api/v1/topics/' + topicId + '/feedback')
      .set('Authorization', bearer(lecturer.accessToken))
      .expect(403);
    expect(forbidden.body.error.code).toBe('FORBIDDEN');
  });
});
