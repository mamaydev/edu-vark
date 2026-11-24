import request from 'supertest';
import { afterAll, beforeEach, describe, expect, it, vi } from 'vitest';
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

vi.mock('../../src/lib/audit', () => ({
  writeAudit: vi.fn(async (_client: unknown, entry: { action: string }) => {
    if (entry.action === 'ASSESSMENT_CREATED') {
      throw new Error('AUDIT_FAILURE');
    }
  }),
}));

const app = createApp();
const describeDb = process.env.DATABASE_URL ? describe : describe.skip;

describeDb('assessment transaction rollback', () => {
  beforeEach(async () => {
    await resetDb();
    resetLoginThrottle();
  });

  afterAll(async () => {
    await closeDb();
  });

  it('rolls back assessment when audit fails and keeps submission text unchanged', async () => {
    const classId = await createClass();
    const lecturerId = await createUser({
      loginId: '1990010001',
      password: 'LecturerPass123',
      role: 'LECTURER',
    });
    await createTeachingAssignment(classId, lecturerId);
    const studentId = await createUser({
      loginId: '2025000001',
      password: 'StudentPass123',
      role: 'STUDENT',
    });
    await createEnrollment(classId, studentId);

    const topicId = await createTopic({
      classId,
      createdBy: lecturerId,
      title: 'Topik Rollback',
      status: 'PUBLISHED',
    });

    const lecturer = await loginAs(app, '1990010001', 'LecturerPass123');
    await request(app)
      .put('/api/v1/lecturer/topics/' + topicId + '/case')
      .set('Authorization', bearer(lecturer.accessToken))
      .send({
        theory: 'Teori',
        instructions: 'Instruksi',
        deadlineAt: new Date(Date.now() + 86400000).toISOString(),
      })
      .expect(200);

    const student = await loginAs(app, '2025000001', 'StudentPass123');
    await request(app)
      .post('/api/v1/auth/vark')
      .set('Authorization', bearer(student.accessToken))
      .send({ vark: 'KINESTHETIC' })
      .expect(200);

    const submitted = await request(app)
      .post('/api/v1/topics/' + topicId + '/case/submission')
      .set('Authorization', bearer(student.accessToken))
      .send({ answerText: 'Teks final tidak boleh berubah.' })
      .expect(201);

    const response = await request(app)
      .post('/api/v1/submissions/' + submitted.body.data.submissionId + '/assessment')
      .set('Authorization', bearer(lecturer.accessToken))
      .send({ score: 85, comment: 'Baik.' })
      .expect(500);
    expect(response.body.error.code).toBe('INTERNAL_ERROR');

    const assessments = await getPool().query<{ count: string }>(
      'SELECT count(*)::text AS count FROM assessments',
    );
    expect(Number(assessments.rows[0]?.count)).toBe(0);

    const submission = await getPool().query<{
      answer_text: string;
      submitted_at: Date;
    }>(
      'SELECT answer_text, submitted_at FROM submissions WHERE id = $1',
      [submitted.body.data.submissionId],
    );
    expect(submission.rows[0]?.answer_text).toBe('Teks final tidak boleh berubah.');
    // Rollback harus mengembalikan timestamp pengiriman awal, bukan timestamp
    // percobaan penilaian yang gagal.
    expect(submission.rows[0]?.submitted_at.toISOString()).toBe(
      submitted.body.data.submittedAt,
    );
  });
});
