import request from 'supertest';
import { afterAll, beforeEach, describe, expect, it } from 'vitest';
import { createApp } from '../../src/app';
import { getPool } from '../../src/db/client';
import { resetLoginThrottle } from '../../src/lib/login-throttle';
import {
  TEST_ADMIN_LOGIN_ID,
  TEST_ADMIN_PASSWORD,
  closeDb,
  createAdmin,
  createClass,
  createUser,
  resetDb,
} from '../helpers/db';
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
let caseId: string;

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

async function lecturerToken(loginId = '1990010001', password = 'LecturerPass123') {
  return loginAs(app, loginId, password);
}

async function createCaseForTopic(
  token: string,
  targetTopicId: string,
  deadlineAt: string,
) {
  return request(app)
    .put('/api/v1/lecturer/topics/' + targetTopicId + '/case')
    .set('Authorization', bearer(token))
    .send({
      theory: 'Teori',
      instructions: 'Instruksi',
      deadlineAt,
    })
    .expect(200);
}

async function submit(token: string, answerText = 'Jawaban studi kasus.') {
  return request(app)
    .post('/api/v1/topics/' + topicId + '/case/submission')
    .set('Authorization', bearer(token))
    .send({ answerText })
    .expect(201);
}

describeDb('assessment integration', () => {
  beforeEach(async () => {
    await resetDb();
    resetLoginThrottle();
    classId = await createClass();
    lecturerId = await createUser({
      loginId: '1990010001',
      password: 'LecturerPass123',
      role: 'LECTURER',
    });
    await createUser({
      loginId: '1990010002',
      password: 'LecturerPass123',
      role: 'LECTURER',
    });
    await createAdmin();
    await createTeachingAssignment(classId, lecturerId);
    await createStudent('2025000001');
    await createStudent('2025000002');
    await createStudent('2025000003');

    topicId = await createTopic({
      classId,
      createdBy: lecturerId,
      title: 'Topik Penilaian',
      status: 'PUBLISHED',
    });

    const lecturer = await lecturerToken();
    const created = await createCaseForTopic(
      lecturer.accessToken,
      topicId,
      new Date(Date.now() + 86400000).toISOString(),
    );
    caseId = created.body.data.id;
  });

  afterAll(async () => {
    await closeDb();
  });

  it('keeps a submitted answer visible after the student logs in again', async () => {
    const lecturer = await lecturerToken();
    const student = await loginStudent('2025000001', 'KINESTHETIC');
    const submitted = await submit(student.accessToken, 'Jawaban tetap terlihat.');
    const submissionId = submitted.body.data.submissionId as string;

    // A new login revokes the previous session and creates one without VARK.
    // The submission must still be listed for the lecturer.
    await loginAs(app, '2025000001', 'StudentPass123');

    const response = await request(app)
      .get('/api/v1/lecturer/cases/' + caseId + '/submissions')
      .set('Authorization', bearer(lecturer.accessToken))
      .expect(200);

    const row = (response.body.data as Array<{
      studentId: string;
      submissionId: string | null;
      status: string;
    }>).find((item) => item.studentId === student.user.id);
    expect(row?.submissionId).toBe(submissionId);
    expect(row?.status).toBe('MENUNGGU_PENILAIAN');

    const detail = await request(app)
      .get('/api/v1/submissions/' + submissionId)
      .set('Authorization', bearer(lecturer.accessToken))
      .expect(200);
    expect(detail.body.data.answerText).toBe('Jawaban tetap terlihat.');
  });

  it('keeps submitted answers listed after the enrollment and account are disabled', async () => {
    const lecturer = await lecturerToken();
    const first = await loginStudent('2025000001', 'KINESTHETIC');
    const second = await loginStudent('2025000002', 'KINESTHETIC');
    const firstSubmit = await submit(first.accessToken, 'Jawaban mahasiswa satu.');
    const secondSubmit = await submit(second.accessToken, 'Jawaban mahasiswa dua.');

    // The answers were stored first; the academic data then changed. The grading
    // tab still shows the aggregate count, so the detail list must agree with it.
    await getPool().query(
      "UPDATE enrollments SET status = 'LEFT', left_at = now() WHERE student_id = $1",
      [first.user.id],
    );
    await getPool().query("UPDATE users SET status = 'INACTIVE' WHERE id = $1", [
      first.user.id,
    ]);

    const response = await request(app)
      .get('/api/v1/lecturer/cases/' + caseId + '/submissions')
      .set('Authorization', bearer(lecturer.accessToken))
      .expect(200);

    const rows = response.body.data as Array<{
      studentId: string;
      submissionId: string | null;
    }>;
    const listedSubmissions = rows
      .map((row) => row.submissionId)
      .filter((id): id is string => id !== null);
    expect(listedSubmissions).toHaveLength(2);
    expect(listedSubmissions).toEqual(
      expect.arrayContaining([
        firstSubmit.body.data.submissionId,
        secondSubmit.body.data.submissionId,
      ]),
    );
    expect(rows.some((row) => row.studentId === first.user.id)).toBe(true);

    const caseResponse = await request(app)
      .get('/api/v1/lecturer/topics/' + topicId + '/case')
      .set('Authorization', bearer(lecturer.accessToken))
      .expect(200);
    expect(caseResponse.body.data.submissionCount).toBe(listedSubmissions.length);
  });

  it('lists Kinesthetic students with derived statuses', async () => {
    const lecturer = await lecturerToken();
    const kinesthetic = await loginStudent('2025000001', 'KINESTHETIC');
    const otherKinesthetic = await loginStudent('2025000002', 'KINESTHETIC');
    await loginStudent('2025000003', 'VISUAL');

    const submitted = await submit(kinesthetic.accessToken, 'Jawaban pertama.');
    expect(submitted.body.data.submissionId).toBeTruthy();

    const response = await request(app)
      .get('/api/v1/lecturer/cases/' + caseId + '/submissions')
      .set('Authorization', bearer(lecturer.accessToken))
      .expect(200);

    const rows = response.body.data as Array<{
      studentId: string;
      status: string;
      submissionId: string | null;
    }>;
    expect(rows).toHaveLength(2);
    expect(rows.some((row) => row.studentId === otherKinesthetic.user.id)).toBe(true);
    expect(rows.find((row) => row.studentId === kinesthetic.user.id)?.status).toBe(
      'MENUNGGU_PENILAIAN',
    );
    expect(rows.find((row) => row.studentId === otherKinesthetic.user.id)?.status).toBe(
      'BELUM_MENGUMPULKAN',
    );
  });

  it('grades once and exposes the final result to the student', async () => {
    const lecturer = await lecturerToken();
    const student = await loginStudent('2025000001', 'KINESTHETIC');
    const submitted = await submit(student.accessToken);
    const submissionId = submitted.body.data.submissionId as string;

    const graded = await request(app)
      .post('/api/v1/submissions/' + submissionId + '/assessment')
      .set('Authorization', bearer(lecturer.accessToken))
      .send({ score: 85, comment: 'Analisis baik.' })
      .expect(201);

    expect(graded.body.data.score).toBe(85);
    expect(graded.body.data.comment).toBe('Analisis baik.');

    const detail = await request(app)
      .get('/api/v1/submissions/' + submissionId)
      .set('Authorization', bearer(student.accessToken))
      .expect(200);
    expect(detail.body.data.status).toBe('SUDAH_DINILAI');
    expect(detail.body.data.score).toBe(85);

    const caseResponse = await request(app)
      .get('/api/v1/topics/' + topicId + '/case')
      .set('Authorization', bearer(student.accessToken))
      .expect(200);
    expect(caseResponse.body.data.status).toBe('SUDAH_DINILAI');
    expect(caseResponse.body.data.score).toBe(85);
    expect(caseResponse.body.data.comment).toBe('Analisis baik.');
  });

  it('rejects a second assessment and keeps one row', async () => {
    const lecturer = await lecturerToken();
    const student = await loginStudent('2025000001', 'KINESTHETIC');
    const submitted = await submit(student.accessToken);
    const submissionId = submitted.body.data.submissionId as string;

    await request(app)
      .post('/api/v1/submissions/' + submissionId + '/assessment')
      .set('Authorization', bearer(lecturer.accessToken))
      .send({ score: 80 })
      .expect(201);

    const second = await request(app)
      .post('/api/v1/submissions/' + submissionId + '/assessment')
      .set('Authorization', bearer(lecturer.accessToken))
      .send({ score: 90 })
      .expect(409);
    expect(second.body.error.code).toBe('ALREADY_ASSESSED');

    const count = await getPool().query<{ count: string }>(
      'SELECT count(*)::text AS count FROM assessments',
    );
    expect(Number(count.rows[0]?.count)).toBe(1);
  });

  it('rejects invalid score and comment', async () => {
    const lecturer = await lecturerToken();
    const student = await loginStudent('2025000001', 'KINESTHETIC');
    const submitted = await submit(student.accessToken);
    const submissionId = submitted.body.data.submissionId as string;

    const invalidScore = await request(app)
      .post('/api/v1/submissions/' + submissionId + '/assessment')
      .set('Authorization', bearer(lecturer.accessToken))
      .send({ score: 101 })
      .expect(422);
    expect(invalidScore.body.error.code).toBe('INVALID_SCORE');

    const invalidComment = await request(app)
      .post('/api/v1/submissions/' + submissionId + '/assessment')
      .set('Authorization', bearer(lecturer.accessToken))
      .send({ score: 80, comment: 'x'.repeat(1001) })
      .expect(422);
    expect(invalidComment.body.error.code).toBe('INVALID_COMMENT');
  });

  it('enforces class ownership and student ownership', async () => {
    const otherLecturer = await lecturerToken('1990010002');
    const student = await loginStudent('2025000001', 'KINESTHETIC');
    const otherStudent = await loginStudent('2025000002', 'KINESTHETIC');
    const submitted = await submit(student.accessToken);
    const submissionId = submitted.body.data.submissionId as string;

    const listForbidden = await request(app)
      .get('/api/v1/lecturer/cases/' + caseId + '/submissions')
      .set('Authorization', bearer(otherLecturer.accessToken))
      .expect(403);
    expect(listForbidden.body.error.code).toBe('CLASS_FORBIDDEN');

    const gradeForbidden = await request(app)
      .post('/api/v1/submissions/' + submissionId + '/assessment')
      .set('Authorization', bearer(otherLecturer.accessToken))
      .send({ score: 80 })
      .expect(403);
    expect(gradeForbidden.body.error.code).toBe('CLASS_FORBIDDEN');

    const otherStudentRead = await request(app)
      .get('/api/v1/submissions/' + submissionId)
      .set('Authorization', bearer(otherStudent.accessToken))
      .expect(403);
    expect(otherStudentRead.body.error.code).toBe('SUBMISSION_FORBIDDEN');

    const studentGrade = await request(app)
      .post('/api/v1/submissions/' + submissionId + '/assessment')
      .set('Authorization', bearer(student.accessToken))
      .send({ score: 80 })
      .expect(403);
    expect(studentGrade.body.error.code).toBe('FORBIDDEN');

    const admin = await loginAs(app, TEST_ADMIN_LOGIN_ID, TEST_ADMIN_PASSWORD);
    const adminRead = await request(app)
      .get('/api/v1/submissions/' + submissionId)
      .set('Authorization', bearer(admin.accessToken))
      .expect(403);
    expect(adminRead.body.error.code).toBe('FORBIDDEN');
  });

  it('lists Tidak Mengumpulkan after the deadline without creating a score', async () => {
    const lecturer = await lecturerToken();
    const pastTopic = await createTopic({
      classId,
      createdBy: lecturerId,
      title: 'Topik Lewat Deadline',
      status: 'PUBLISHED',
      sortOrder: 1,
    });
    const pastCase = await createCaseForTopic(
      lecturer.accessToken,
      pastTopic,
      new Date(Date.now() - 60_000).toISOString(),
    );
    await loginStudent('2025000001', 'KINESTHETIC');
    await loginStudent('2025000002', 'KINESTHETIC');

    const response = await request(app)
      .get('/api/v1/lecturer/cases/' + pastCase.body.data.id + '/submissions')
      .set('Authorization', bearer(lecturer.accessToken))
      .expect(200);

    const rows = response.body.data as Array<{ status: string; score: number | null }>;
    expect(rows).toHaveLength(2);
    for (const row of rows) {
      expect(row.status).toBe('TIDAK_MENGUMPULKAN');
      expect(row.score).toBeNull();
    }

    const submissions = await getPool().query<{ count: string }>(
      'SELECT count(*)::text AS count FROM submissions WHERE case_study_id = $1',
      [pastCase.body.data.id],
    );
    expect(Number(submissions.rows[0]?.count)).toBe(0);
  });
});
