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

const LECTURER_LOGIN_ID = '1990010001';
const LECTURER_PASSWORD = 'LecturerPass123';
const STUDENT_LOGIN_ID = '2025000001';
const STUDENT_PASSWORD = 'StudentPass123';

let lecturerToken: string;
let studentToken: string;
let submissionId: string;

async function grade(payload: Record<string, unknown>) {
  return request(app)
    .post('/api/v1/submissions/' + submissionId + '/assessment')
    .set('Authorization', bearer(lecturerToken))
    .send(payload);
}

async function assessmentCount(): Promise<number> {
  const result = await getPool().query<{ count: string }>(
    'SELECT count(*)::text AS count FROM assessments',
  );
  return Number(result.rows[0]?.count ?? '0');
}

async function storedAssessment(): Promise<{ score: number | null; comment: string | null } | null> {
  const result = await getPool().query<{ score: number; comment: string | null }>(
    'SELECT score, comment FROM assessments',
  );
  return result.rows[0] ?? null;
}

async function submissionDetailStatus(): Promise<string> {
  const response = await request(app)
    .get('/api/v1/submissions/' + submissionId)
    .set('Authorization', bearer(lecturerToken))
    .expect(200);
  return response.body.data.status as string;
}

describeDb('manual grade validation and finality', () => {
  beforeEach(async () => {
    await resetDb();
    resetLoginThrottle();
    const classId = await createClass();
    const lecturerId = await createUser({
      loginId: LECTURER_LOGIN_ID,
      password: LECTURER_PASSWORD,
      role: 'LECTURER',
    });
    await createTeachingAssignment(classId, lecturerId);
    const studentId = await createUser({
      loginId: STUDENT_LOGIN_ID,
      password: STUDENT_PASSWORD,
      role: 'STUDENT',
    });
    await createEnrollment(classId, studentId);
    const topicId = await createTopic({
      classId,
      createdBy: lecturerId,
      title: 'Topik Penilaian',
      status: 'PUBLISHED',
    });

    const lecturer = await loginAs(app, LECTURER_LOGIN_ID, LECTURER_PASSWORD);
    lecturerToken = lecturer.accessToken;
    await request(app)
      .put('/api/v1/lecturer/topics/' + topicId + '/case')
      .set('Authorization', bearer(lecturerToken))
      .send({
        theory: 'Teori',
        instructions: 'Instruksi',
        deadlineAt: new Date(Date.now() + 86_400_000).toISOString(),
      })
      .expect(200);

    const student = await loginAs(app, STUDENT_LOGIN_ID, STUDENT_PASSWORD);
    studentToken = student.accessToken;
    await request(app)
      .post('/api/v1/auth/vark')
      .set('Authorization', bearer(studentToken))
      .send({ vark: 'KINESTHETIC' })
      .expect(200);

    const submitted = await request(app)
      .post('/api/v1/topics/' + topicId + '/case/submission')
      .set('Authorization', bearer(studentToken))
      .send({ answerText: 'Jawaban studi kasus.' })
      .expect(201);
    submissionId = submitted.body.data.submissionId;
  });

  afterAll(async () => {
    await closeDb();
  });

  it('rejects a score below zero without mutating anything', async () => {
    const response = await grade({ score: -1 });
    expect(response.status).toBe(422);
    expect(response.body.error.code).toBe('INVALID_SCORE');
    expect(response.body.error.message).toBe('Nilai harus berupa integer 0–100.');
    expect(await assessmentCount()).toBe(0);
    expect(await submissionDetailStatus()).toBe('MENUNGGU_PENILAIAN');
  });

  it('rejects a score above one hundred without mutating anything', async () => {
    const response = await grade({ score: 101 });
    expect(response.status).toBe(422);
    expect(response.body.error.code).toBe('INVALID_SCORE');
    expect(await assessmentCount()).toBe(0);
    expect(await submissionDetailStatus()).toBe('MENUNGGU_PENILAIAN');
  });

  it('rejects a decimal score instead of rounding it', async () => {
    const response = await grade({ score: 85.5 });
    expect(response.status).toBe(422);
    expect(response.body.error.code).toBe('INVALID_SCORE');
    expect(await assessmentCount()).toBe(0);
  });

  it('rejects a string score at schema level', async () => {
    const response = await grade({ score: '85' });
    expect(response.status).toBe(422);
    expect(response.body.error.code).toBe('VALIDATION_ERROR');
    expect(await assessmentCount()).toBe(0);
  });

  it('accepts the boundary scores zero and one hundred', async () => {
    const zero = await grade({ score: 0 });
    expect(zero.status).toBe(201);
    expect(zero.body.data.score).toBe(0);
    expect((await storedAssessment())?.score).toBe(0);

    // Nilai bersifat final: penilaian kedua harus ditolak.
    const final = await grade({ score: 100 });
    expect(final.status).toBe(409);
    expect(final.body.error.code).toBe('ALREADY_ASSESSED');
    expect((await storedAssessment())?.score).toBe(0);
  });

  it('accepts an optional empty comment as null', async () => {
    const response = await grade({ score: 80, comment: '   ' });
    expect(response.status).toBe(201);
    expect(response.body.data.comment).toBeNull();
    expect((await storedAssessment())?.comment).toBeNull();
  });

  it('accepts a comment of exactly one thousand BMP characters', async () => {
    const comment = 'a'.repeat(1_000);
    const response = await grade({ score: 80, comment });
    expect(response.status).toBe(201);
    expect((await storedAssessment())?.comment).toBe(comment);
  });

  it('rejects a comment beyond one thousand BMP characters', async () => {
    const response = await grade({ score: 80, comment: 'a'.repeat(1_001) });
    expect(response.status).toBe(422);
    expect(response.body.error.code).toBe('INVALID_COMMENT');
    expect(response.body.error.message).toBe('Komentar maksimal 1.000 karakter.');
    expect(await assessmentCount()).toBe(0);
  });

  it('accepts one thousand astral code points in the comment', async () => {
    const comment = '\uD83D\uDE00'.repeat(1_000);
    expect(comment.length).toBe(2_000);
    const response = await grade({ score: 95, comment });
    expect(response.status).toBe(201);
    expect((await storedAssessment())?.comment).toBe(comment);
  });

  it('rejects one thousand and one astral code points in the comment', async () => {
    const response = await grade({ score: 95, comment: '\uD83D\uDE00'.repeat(1_001) });
    expect(response.status).toBe(422);
    expect(response.body.error.code).toBe('INVALID_COMMENT');
    expect(await assessmentCount()).toBe(0);
  });

  it('keeps the first grade final when a second grade is attempted', async () => {
    const first = await grade({ score: 88, comment: 'Baik.' });
    expect(first.status).toBe(201);

    const second = await grade({ score: 40, comment: 'Revisi.' });
    expect(second.status).toBe(409);
    expect(second.body.error.code).toBe('ALREADY_ASSESSED');
    expect(second.body.error.message).toBe(
      'Submission sudah dinilai dan bersifat final.',
    );

    const stored = await storedAssessment();
    expect(stored?.score).toBe(88);
    expect(stored?.comment).toBe('Baik.');
    expect(await submissionDetailStatus()).toBe('SUDAH_DINILAI');
  });

  it('does not alter a stored answer after a rejected grade', async () => {
    const before = await getPool().query<{ answer_text: string; submitted_at: Date }>(
      'SELECT answer_text, submitted_at FROM submissions WHERE id = $1',
      [submissionId],
    );

    await grade({ score: 1.5, comment: 'x'.repeat(1_001) });

    const after = await getPool().query<{ answer_text: string; submitted_at: Date }>(
      'SELECT answer_text, submitted_at FROM submissions WHERE id = $1',
      [submissionId],
    );
    expect(after.rows[0]?.answer_text).toBe(before.rows[0]?.answer_text);
    expect(after.rows[0]?.submitted_at.toISOString()).toBe(
      before.rows[0]?.submitted_at.toISOString(),
    );
  });
});
