import request from 'supertest';
import { afterAll, beforeEach, describe, expect, it } from 'vitest';
import { createApp } from '../../src/app';
import { getPool } from '../../src/db/client';
import { resetLoginThrottle } from '../../src/lib/login-throttle';
import * as casesRepo from '../../src/modules/cases/cases.repository';
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

let classId: string;
let lecturerToken: string;
let topicId: string;
let caseId: string;
let studentOneId: string;
let studentTwoId: string;
let studentOneToken: string;

async function createStudent(loginId: string) {
  const id = await createUser({
    loginId,
    password: 'StudentPass123',
    role: 'STUDENT',
  });
  await createEnrollment(classId, id);
  const session = await loginAs(app, loginId, 'StudentPass123');
  await request(app)
    .post('/api/v1/auth/vark')
    .set('Authorization', bearer(session.accessToken))
    .send({ vark: 'KINESTHETIC' })
    .expect(200);
  return { id, token: session.accessToken };
}

async function submitAnswer(token: string) {
  return request(app)
    .post('/api/v1/topics/' + topicId + '/case/submission')
    .set('Authorization', bearer(token))
    .send({ answerText: 'Jawaban final.' });
}

async function submissionCount(): Promise<number> {
  const result = await getPool().query<{ count: string }>(
    'SELECT count(*)::text AS count FROM submissions',
  );
  return Number(result.rows[0]?.count ?? '0');
}

async function setDeadline(value: 'past' | 'future') {
  await getPool().query(
    value === 'past'
      ? "UPDATE case_studies SET deadline_at = now() - interval '1 second' WHERE id = $1"
      : "UPDATE case_studies SET deadline_at = now() + interval '1 day' WHERE id = $1",
    [caseId],
  );
}

describeDb('kinesthetic submission boundaries and finality', () => {
  beforeEach(async () => {
    await resetDb();
    resetLoginThrottle();
    classId = await createClass();
    const lecturerId = await createUser({
      loginId: LECTURER_LOGIN_ID,
      password: LECTURER_PASSWORD,
      role: 'LECTURER',
    });
    await createTeachingAssignment(classId, lecturerId);
    const studentOne = await createStudent('2025000001');
    studentOneId = studentOne.id;
    studentOneToken = studentOne.token;
    studentTwoId = (await createStudent('2025000002')).id;

    topicId = await createTopic({
      classId,
      createdBy: lecturerId,
      title: 'Topik Boundary',
      status: 'PUBLISHED',
    });

    const lecturer = await loginAs(app, LECTURER_LOGIN_ID, LECTURER_PASSWORD);
    lecturerToken = lecturer.accessToken;
    const created = await request(app)
      .put('/api/v1/lecturer/topics/' + topicId + '/case')
      .set('Authorization', bearer(lecturerToken))
      .send({
        theory: 'Teori',
        instructions: 'Instruksi',
        deadlineAt: new Date(Date.now() + 86_400_000).toISOString(),
      })
      .expect(200);
    caseId = created.body.data.id;
  });

  afterAll(async () => {
    await closeDb();
  });

  it('accepts exactly at the database transaction start time and rejects one millisecond later', async () => {
    const client = await getPool().connect();
    try {
      await client.query('BEGIN');
      // `now()` adalah waktu mulai transaksi dan konstan di dalamnya, sehingga
      // batas dapat diuji secara deterministik tanpa race dengan jam dinding.
      // Nilai deadline diset langsung dari SQL agar presisi mikrodetik
      // PostgreSQL tidak terpotong pembulatan Date JavaScript.
      await client.query('UPDATE case_studies SET deadline_at = now() WHERE id = $1', [
        caseId,
      ]);
      const exactlyAtDeadline = await casesRepo.lockCaseForSubmission(
        client,
        topicId,
        studentOneId,
      );
      expect(exactlyAtDeadline).not.toBeNull();
      // Deadline inklusif: pengiriman tepat pada deadline masih diterima.
      expect(exactlyAtDeadline?.allowed).toBe(true);

      await client.query(
        "UPDATE case_studies SET deadline_at = now() - interval '1 millisecond' WHERE id = $1",
        [caseId],
      );
      const oneMillisecondLate = await casesRepo.lockCaseForSubmission(
        client,
        topicId,
        studentOneId,
      );
      // Lewat 1 ms dari deadline harus ditolak.
      expect(oneMillisecondLate?.allowed).toBe(false);
    } finally {
      await client.query('ROLLBACK');
      client.release();
    }
  });

  it('reports BELUM_MENGUMPULKAN before the deadline without a submission', async () => {
    const response = await request(app)
      .get('/api/v1/topics/' + topicId + '/case')
      .set('Authorization', bearer(studentOneToken))
      .expect(200);
    expect(response.body.data.status).toBe('BELUM_MENGUMPULKAN');
    expect(response.body.data.submittedAt).toBeNull();
  });

  it('reports TIDAK_MENGUMPULKAN after the deadline without a submission', async () => {
    await setDeadline('past');
    const response = await request(app)
      .get('/api/v1/topics/' + topicId + '/case')
      .set('Authorization', bearer(studentOneToken))
      .expect(200);
    expect(response.body.data.status).toBe('TIDAK_MENGUMPULKAN');
  });

  it('rejects a submission after the deadline without creating a row', async () => {
    await setDeadline('past');
    const response = await submitAnswer(studentOneToken);
    expect(response.status).toBe(409);
    expect(response.body.error.code).toBe('DEADLINE_PASSED');
    expect(await submissionCount()).toBe(0);
  });

  it('locks the deadline after the first submission and blocks a second one', async () => {
    const first = await submitAnswer(studentOneToken);
    expect(first.status).toBe(201);

    // Trigger trg_deadline_lock menolak perubahan deadline setelah ada
    // submission: deadline terkunci permanen sejak pengiriman pertama.
    await expect(
      getPool().query(
        "UPDATE case_studies SET deadline_at = now() - interval '1 day' WHERE id = $1",
        [caseId],
      ),
    ).rejects.toThrow(/DEADLINE_LOCKED/);

    const second = await submitAnswer(studentOneToken);
    expect(second.status).toBe(409);
    expect(second.body.error.code).toBe('ALREADY_SUBMITTED');
    expect(await submissionCount()).toBe(1);

    const seen = await request(app)
      .get('/api/v1/topics/' + topicId + '/case')
      .set('Authorization', bearer(studentOneToken))
      .expect(200);
    expect(seen.body.data.status).toBe('MENUNGGU_PENILAIAN');
  });

  it('rejects a second sequential submission as already submitted', async () => {
    expect((await submitAnswer(studentOneToken)).status).toBe(201);

    const second = await submitAnswer(studentOneToken);
    expect(second.status).toBe(409);
    expect(second.body.error.code).toBe('ALREADY_SUBMITTED');
    expect(await submissionCount()).toBe(1);
  });

  it('creates exactly one row for two concurrent submissions', async () => {
    // Dua pengiriman bersamaan untuk mahasiswa yang sama: unique constraint
    // (case_study_id, student_id) memastikan hanya satu baris tersimpan.
    const [first, second] = await Promise.all([
      submitAnswer(studentOneToken),
      submitAnswer(studentOneToken),
    ]);

    const statuses = [first.status, second.status].sort((a, b) => a - b);
    expect(statuses).toEqual([201, 409]);
    const conflict = first.status === 409 ? first : second;
    expect(conflict.body.error.code).toBe('ALREADY_SUBMITTED');
    expect(await submissionCount()).toBe(1);

    const rows = await getPool().query<{ student_id: string }>(
      'SELECT student_id FROM submissions',
    );
    expect(rows.rows).toHaveLength(1);
    expect([studentOneId, studentTwoId]).toContain(rows.rows[0]?.student_id);
  });

  it('keeps the final submission immutable after the deadline lock is applied', async () => {
    const first = await submitAnswer(studentOneToken);
    expect(first.status).toBe(201);
    const submittedAt = first.body.data.submittedAt as string;

    await expect(
      getPool().query(
        "UPDATE case_studies SET deadline_at = now() - interval '1 day' WHERE id = $1",
        [caseId],
      ),
    ).rejects.toThrow(/DEADLINE_LOCKED/);

    await submitAnswer(studentOneToken);
    await submitAnswer(studentOneToken);

    const stored = await getPool().query<{ count: string; submitted_at: Date }>(
      'SELECT count(*)::text AS count, max(submitted_at) AS submitted_at FROM submissions',
    );
    expect(Number(stored.rows[0]?.count)).toBe(1);
    expect(stored.rows[0]?.submitted_at.toISOString()).toBe(submittedAt);
  });
});
