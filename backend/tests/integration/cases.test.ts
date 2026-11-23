import request from 'supertest';
import { afterAll, beforeEach, describe, expect, it } from 'vitest';
import { createApp } from '../../src/app';
import { getPool } from '../../src/db/client';
import { resetLoginThrottle } from '../../src/lib/login-throttle';
import { closeDb, createClass, createUser, resetDb } from '../helpers/db';
import {
  createEnrollment,
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

async function lecturerToken() {
  return loginAs(app, '1990010001', 'LecturerPass123');
}

async function upsertCase(
  token: string,
  deadlineAt: string,
  instructions = 'Kerjakan studi kasus',
) {
  return request(app)
    .put('/api/v1/lecturer/topics/' + topicId + '/case')
    .set('Authorization', bearer(token))
    .send({ theory: 'Teori IBD', instructions, deadlineAt })
    .expect(200);
}

async function submissionCount(): Promise<number> {
  const result = await getPool().query<{ count: string }>(
    'SELECT count(*)::text AS count FROM submissions',
  );
  return Number(result.rows[0]?.count ?? '0');
}

describeDb('Kinesthetic case integration', () => {
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
    topicId = await createTopic({
      classId,
      createdBy: lecturerId,
      title: 'Topik Kinesthetic',
      status: 'PUBLISHED',
    });
  });

  afterAll(async () => {
    await closeDb();
  });

  it('creates a 1:1 case and exposes deadline in WIB', async () => {
    const lecturer = await lecturerToken();
    const deadline = new Date(Date.now() + 24 * 60 * 60 * 1000).toISOString();

    const created = await upsertCase(lecturer.accessToken, deadline);
    expect(created.body.data.topicId).toBe(topicId);
    expect(created.body.data.instructions).toBe('Kerjakan studi kasus');
    expect(created.body.data.deadlineWib).toContain('WIB');

    const again = await upsertCase(
      lecturer.accessToken,
      deadline,
      'Instruksi diperbarui',
    );
    expect(again.body.data.id).toBe(created.body.data.id);
    expect(again.body.data.instructions).toBe('Instruksi diperbarui');

    const audit = await getPool().query<{ action: string }>(
      "SELECT action FROM audit_logs WHERE entity = 'CASE_STUDY' ORDER BY created_at",
    );
    const actions = audit.rows.map((row) => row.action);
    expect(actions).toContain('CASE_CREATED');
    expect(actions).toContain('CASE_UPDATED');
  });

  it('returns the exact missing-case message', async () => {
    const student = await loginStudent('2025000001', 'KINESTHETIC');
    const response = await request(app)
      .get('/api/v1/topics/' + topicId + '/case')
      .set('Authorization', bearer(student.accessToken))
      .expect(404);

    expect(response.body.error.code).toBe('CASE_NOT_FOUND');
    expect(response.body.error.message).toBe('studi kasus belum tersedia');
  });

  it('rejects case access from a non-Kinesthetic session', async () => {
    const lecturer = await lecturerToken();
    await upsertCase(lecturer.accessToken, new Date(Date.now() + 86400000).toISOString());

    const visual = await loginStudent('2025000002', 'VISUAL');
    const response = await request(app)
      .get('/api/v1/topics/' + topicId + '/case')
      .set('Authorization', bearer(visual.accessToken))
      .expect(403);

    expect(response.body.error.code).toBe('VARK_MISMATCH');
  });

  it('accepts one submission and rejects the second', async () => {
    const lecturer = await lecturerToken();
    await upsertCase(lecturer.accessToken, new Date(Date.now() + 86400000).toISOString());
    const student = await loginStudent('2025000001', 'KINESTHETIC');

    const before = await request(app)
      .get('/api/v1/topics/' + topicId + '/case')
      .set('Authorization', bearer(student.accessToken))
      .expect(200);
    expect(before.body.data.status).toBe('BELUM_MENGUMPULKAN');

    const first = await request(app)
      .post('/api/v1/topics/' + topicId + '/case/submission')
      .set('Authorization', bearer(student.accessToken))
      .send({ answerText: 'Jawaban final saya.' })
      .expect(201);
    expect(first.body.data.status).toBe('MENUNGGU_PENILAIAN');

    const second = await request(app)
      .post('/api/v1/topics/' + topicId + '/case/submission')
      .set('Authorization', bearer(student.accessToken))
      .send({ answerText: 'Jawaban kedua.' })
      .expect(409);
    expect(second.body.error.code).toBe('ALREADY_SUBMITTED');

    expect(await submissionCount()).toBe(1);

    const after = await request(app)
      .get('/api/v1/topics/' + topicId + '/case')
      .set('Authorization', bearer(student.accessToken))
      .expect(200);
    expect(after.body.data.status).toBe('MENUNGGU_PENILAIAN');
  });

  it('rejects empty, whitespace-only, and overlong answers', async () => {
    const lecturer = await lecturerToken();
    await upsertCase(lecturer.accessToken, new Date(Date.now() + 86400000).toISOString());
    const student = await loginStudent('2025000001', 'KINESTHETIC');

    await request(app)
      .post('/api/v1/topics/' + topicId + '/case/submission')
      .set('Authorization', bearer(student.accessToken))
      .send({ answerText: '   ' })
      .expect(422);

    await request(app)
      .post('/api/v1/topics/' + topicId + '/case/submission')
      .set('Authorization', bearer(student.accessToken))
      .send({ answerText: 'x'.repeat(10001) })
      .expect(422);

    expect(await submissionCount()).toBe(0);
  });

  it('rejects submission after the deadline without creating a row', async () => {
    const lecturer = await lecturerToken();
    await upsertCase(lecturer.accessToken, new Date(Date.now() - 60_000).toISOString());
    const student = await loginStudent('2025000001', 'KINESTHETIC');

    const status = await request(app)
      .get('/api/v1/topics/' + topicId + '/case')
      .set('Authorization', bearer(student.accessToken))
      .expect(200);
    expect(status.body.data.status).toBe('TIDAK_MENGUMPULKAN');

    const response = await request(app)
      .post('/api/v1/topics/' + topicId + '/case/submission')
      .set('Authorization', bearer(student.accessToken))
      .send({ answerText: 'Terlambat.' })
      .expect(409);
    expect(response.body.error.code).toBe('DEADLINE_PASSED');
    expect(await submissionCount()).toBe(0);
  });

  it('locks the deadline after the first submission', async () => {
    const lecturer = await lecturerToken();
    const deadline = new Date(Date.now() + 86400000).toISOString();
    await upsertCase(lecturer.accessToken, deadline);
    const student = await loginStudent('2025000001', 'KINESTHETIC');

    await request(app)
      .post('/api/v1/topics/' + topicId + '/case/submission')
      .set('Authorization', bearer(student.accessToken))
      .send({ answerText: 'Jawaban.' })
      .expect(201);

    const response = await request(app)
      .put('/api/v1/lecturer/topics/' + topicId + '/case')
      .set('Authorization', bearer(lecturer.accessToken))
      .send({
        theory: 'Teori',
        instructions: 'Instruksi',
        deadlineAt: new Date(Date.now() + 172800000).toISOString(),
      })
      .expect(409);

    expect(response.body.error.code).toBe('DEADLINE_LOCKED');
  });

  it('enforces role and VARK gates', async () => {
    const lecturer = await lecturerToken();
    await upsertCase(lecturer.accessToken, new Date(Date.now() + 86400000).toISOString());

    const noVark = await loginAs(app, '2025000001', 'StudentPass123');
    const varkGate = await request(app)
      .get('/api/v1/topics/' + topicId + '/case')
      .set('Authorization', bearer(noVark.accessToken))
      .expect(428);
    expect(varkGate.body.error.code).toBe('VARK_REQUIRED');

    const lecturerSubmit = await request(app)
      .post('/api/v1/topics/' + topicId + '/case/submission')
      .set('Authorization', bearer(lecturer.accessToken))
      .send({ answerText: 'Bukan mahasiswa.' })
      .expect(403);
    expect(lecturerSubmit.body.error.code).toBe('FORBIDDEN');
  });
});
