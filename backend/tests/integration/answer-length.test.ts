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

const STUDENT_LOGIN_ID = '2025000001';
const STUDENT_PASSWORD = 'StudentPass123';
const LECTURER_LOGIN_ID = '1990010001';
const LECTURER_PASSWORD = 'LecturerPass123';
const INVALID_MESSAGE = 'Jawaban harus 1–10.000 karakter.';

let topicId: string;
let studentToken: string;

async function submitAnswer(answerText: unknown) {
  return request(app)
    .post('/api/v1/topics/' + topicId + '/case/submission')
    .set('Authorization', bearer(studentToken))
    .send({ answerText });
}

async function storedSubmission(): Promise<{ count: number; answerText: string | null; length: number }> {
  const result = await getPool().query<{
    count: string;
    answer_text: string | null;
    length: string | null;
  }>(
    `
      SELECT count(*)::text AS count,
             max(answer_text) AS answer_text,
             max(char_length(answer_text))::text AS length
      FROM submissions
    `,
  );
  const row = result.rows[0];
  return {
    count: Number(row?.count ?? '0'),
    answerText: row?.answer_text ?? null,
    length: Number(row?.length ?? '0'),
  };
}

describeDb('kinesthetic answer length in Unicode code points', () => {
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
    topicId = await createTopic({
      classId,
      createdBy: lecturerId,
      title: 'Topik Answer Length',
      status: 'PUBLISHED',
    });

    const lecturer = await loginAs(app, LECTURER_LOGIN_ID, LECTURER_PASSWORD);
    await request(app)
      .put('/api/v1/lecturer/topics/' + topicId + '/case')
      .set('Authorization', bearer(lecturer.accessToken))
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
  });

  afterAll(async () => {
    await closeDb();
  });

  it('rejects an empty answer with the SRS message', async () => {
    const response = await submitAnswer('');
    expect(response.status).toBe(422);
    expect(response.body.error.code).toBe('ANSWER_LENGTH_INVALID');
    expect(response.body.error.message).toBe(INVALID_MESSAGE);

    const stored = await storedSubmission();
    expect(stored.count).toBe(0);
  });

  it('rejects a whitespace-only answer after trim', async () => {
    const response = await submitAnswer('   \n\t  ');
    expect(response.status).toBe(422);
    expect(response.body.error.code).toBe('ANSWER_LENGTH_INVALID');
    expect(response.body.error.message).toBe(INVALID_MESSAGE);

    const stored = await storedSubmission();
    expect(stored.count).toBe(0);
  });

  it('rejects a non-string answer payload', async () => {
    const response = await submitAnswer(123);
    expect(response.status).toBe(422);
    expect(response.body.error.code).toBe('VALIDATION_ERROR');
  });

  it('accepts exactly one code point', async () => {
    const response = await submitAnswer('\uD83D\uDE00');
    expect(response.status).toBe(201);
    expect(response.body.data.status).toBe('MENUNGGU_PENILAIAN');

    const stored = await storedSubmission();
    expect(stored.count).toBe(1);
    expect(stored.length).toBe(1);
    expect(stored.answerText).toBe('\uD83D\uDE00');
  });

  it('accepts exactly ten thousand BMP code points', async () => {
    const answer = 'a'.repeat(10_000);
    const response = await submitAnswer(answer);
    expect(response.status).toBe(201);

    const stored = await storedSubmission();
    expect(stored.length).toBe(10_000);
    expect(stored.answerText).toBe(answer);
  });

  it('rejects ten thousand and one BMP code points', async () => {
    const response = await submitAnswer('a'.repeat(10_001));
    expect(response.status).toBe(422);
    expect(response.body.error.code).toBe('ANSWER_LENGTH_INVALID');
    expect(response.body.error.message).toBe(INVALID_MESSAGE);

    expect((await storedSubmission()).count).toBe(0);
  });

  it('accepts ten thousand astral code points (emoji) even though they are 20.000 UTF-16 units', async () => {
    const answer = '\uD83D\uDE00'.repeat(10_000);
    expect(answer.length).toBe(20_000);

    const response = await submitAnswer(answer);
    expect(response.status).toBe(201);

    const stored = await storedSubmission();
    expect(stored.length).toBe(10_000);
    expect(stored.answerText).toBe(answer);
  });

  it('rejects ten thousand and one astral code points', async () => {
    const response = await submitAnswer('\uD83D\uDE00'.repeat(10_001));
    expect(response.status).toBe(422);
    expect(response.body.error.code).toBe('ANSWER_LENGTH_INVALID');
    expect(response.body.error.message).toBe(INVALID_MESSAGE);

    expect((await storedSubmission()).count).toBe(0);
  });

  it('counts combining marks as separate code points', async () => {
    const accepted = 'e\u0301'.repeat(5_000);
    const response = await submitAnswer(accepted);
    expect(response.status).toBe(201);

    const stored = await storedSubmission();
    expect(stored.length).toBe(10_000);
  });

  it('rejects combining marks beyond the boundary', async () => {
    const response = await submitAnswer('e\u0301'.repeat(5_001));
    expect(response.status).toBe(422);
    expect(response.body.error.code).toBe('ANSWER_LENGTH_INVALID');
  });

  it('trims surrounding whitespace before counting code points', async () => {
    const core = '\uD83D\uDE00'.repeat(10_000);
    const answer = '  \n\t' + core + ' \r\n ';

    const response = await submitAnswer(answer);
    expect(response.status).toBe(201);

    const stored = await storedSubmission();
    expect(stored.length).toBe(10_000);
    expect(stored.answerText).toBe(core);
  });

  it('accepts a mixed BMP and astral answer summing to ten thousand code points', async () => {
    const answer = 'a'.repeat(9_999) + '\uD83D\uDE00';
    const response = await submitAnswer(answer);
    expect(response.status).toBe(201);

    const stored = await storedSubmission();
    expect(stored.length).toBe(10_000);
  });
});
