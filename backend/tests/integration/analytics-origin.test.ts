import request from 'supertest';
import { afterAll, beforeEach, describe, expect, it } from 'vitest';
import { createApp } from '../../src/app';
import { getPool } from '../../src/db/client';
import { resetLoginThrottle } from '../../src/lib/login-throttle';
import { closeDb, createClass, createUser, resetDb } from '../helpers/db';
import {
  createEnrollment,
  createMaterialLink,
  createTeachingAssignment,
  createTopic,
} from '../helpers/fixtures';
import { bearer, loginAs } from '../helpers/api';

const app = createApp();
const describeDb = process.env.DATABASE_URL ? describe : describe.skip;

/**
 * Acceptance dataset for FR-029–033 / AT-05, AT-24, AT-34:
 *
 * - `studentA` menyimpan Visual 40, Audio 60, lalu Visual 75 pada topik yang
 *   sama, kemudian login terakhir sebagai KINESTHETIC.
 * - `studentK1` dan `studentK2` masing-masing mengirim jawaban studi kasus dan
 *   dinilai final 80 dan 100. Setelah itu `studentK1` logout + enrollment LEFT,
 *   sedangkan `studentK2` logout lalu login dengan VARK AURAL.
 *
 * Semua nilai harus tetap terbaca dosen: Visual 75 dan Audio 60 pada filter
 * kategori asal masing-masing, serta assessedCount 2 dengan meanScore 90 untuk
 * Kinesthetic.
 */
let classId: string;
let lecturerId: string;
let topicId: string;
let lecturerToken: string;
let studentAId: string;
let studentK1Id: string;

async function loginStudent(loginId: string, vark: string) {
  const session = await loginAs(app, loginId, 'StudentPass123');
  await request(app)
    .post('/api/v1/auth/vark')
    .set('Authorization', bearer(session.accessToken))
    .send({ vark })
    .expect(200);
  return session;
}

async function createEnrolledStudent(loginId: string) {
  const id = await createUser({
    loginId,
    password: 'StudentPass123',
    role: 'STUDENT',
  });
  await createEnrollment(classId, id);
  return id;
}

async function putFeedback(token: string, value: number) {
  await request(app)
    .put('/api/v1/topics/' + topicId + '/feedback')
    .set('Authorization', bearer(token))
    .send({ value })
    .expect(200);
}

async function getAnalytics(query = '') {
  return request(app)
    .get('/api/v1/lecturer/analytics/topics/' + topicId + query)
    .set('Authorization', bearer(lecturerToken))
    .expect(200);
}

async function submitAndAssess(
  studentToken: string,
  score: number,
): Promise<string> {
  const submitted = await request(app)
    .post('/api/v1/topics/' + topicId + '/case/submission')
    .set('Authorization', bearer(studentToken))
    .send({ answerText: 'Jawaban studi kasus.' })
    .expect(201);
  const submissionId = submitted.body.data.submissionId as string;
  await request(app)
    .post('/api/v1/submissions/' + submissionId + '/assessment')
    .set('Authorization', bearer(lecturerToken))
    .send({ score, comment: 'Nilai final.' })
    .expect(201);
  return submissionId;
}

describeDb('analytics origin-category acceptance', () => {
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
    studentAId = await createEnrolledStudent('2025000001');
    studentK1Id = await createEnrolledStudent('2025000002');
    await createEnrolledStudent('2025000003');

    topicId = await createTopic({
      classId,
      createdBy: lecturerId,
      title: 'Topik Acceptance Analitik',
      status: 'PUBLISHED',
    });
    for (const vark of ['VISUAL', 'AURAL', 'READ_WRITE'] as const) {
      await createMaterialLink({
        topicId,
        uploadedBy: lecturerId,
        vark,
        title: vark,
        url: 'https://example.com/' + vark.toLowerCase(),
      });
    }

    const lecturer = await loginAs(app, '1990010001', 'LecturerPass123');
    lecturerToken = lecturer.accessToken;

    // AT-05: Visual 40 -> Audio 60 -> Visual 75 (latest value per category is
    // what the lecturer must see).
    const visualFirst = await loginStudent('2025000001', 'VISUAL');
    await putFeedback(visualFirst.accessToken, 40);
    const aural = await loginStudent('2025000001', 'AURAL');
    await putFeedback(aural.accessToken, 60);
    const visualLast = await loginStudent('2025000001', 'VISUAL');
    await putFeedback(visualLast.accessToken, 75);

    await request(app)
      .put('/api/v1/lecturer/topics/' + topicId + '/case')
      .set('Authorization', bearer(lecturerToken))
      .send({
        theory: 'Teori',
        instructions: 'Instruksi',
        deadlineAt: new Date(Date.now() + 86400000).toISOString(),
      })
      .expect(200);

    // AT-24: two final Kinesthetic assessments 80 and 100.
    const k1 = await loginStudent('2025000002', 'KINESTHETIC');
    await submitAndAssess(k1.accessToken, 80);
    const k2 = await loginStudent('2025000003', 'KINESTHETIC');
    await submitAndAssess(k2.accessToken, 100);

    // Session churn after the values are stored: studentA's last session is
    // Kinesthetic, studentK1 logs out and its enrollment becomes LEFT, and
    // studentK2 logs out then signs in with another VARK.
    await loginStudent('2025000001', 'KINESTHETIC');
    await request(app)
      .post('/api/v1/auth/logout')
      .set('Authorization', bearer(k1.accessToken))
      .expect(204);
    await request(app)
      .post('/api/v1/auth/logout')
      .set('Authorization', bearer(k2.accessToken))
      .expect(204);
    await loginStudent('2025000003', 'AURAL');
    await getPool().query(
      "UPDATE enrollments SET status = 'LEFT', left_at = now() WHERE class_id = $1 AND student_id = $2",
      [classId, studentK1Id],
    );
  });

  afterAll(async () => {
    await closeDb();
  });

  it('shows Visual 75 in the Visual filter after the student relogged as Kinesthetic', async () => {
    const response = await getAnalytics('?category=VISUAL');
    const data = response.body.data;

    expect(data.respondentCount).toBe(1);
    expect(data.mean).toBe(75);
    const row = data.students.find(
      (item: { studentId: string }) => item.studentId === studentAId,
    );
    expect(row).toMatchObject({
      vark: 'VISUAL',
      originCategories: ['VISUAL', 'AURAL'],
      feedback: { VISUAL: 75, AURAL: 60, READ_WRITE: null },
    });
  });

  it('shows Audio 60 in the Audio filter for the same student and topic', async () => {
    const response = await getAnalytics('?category=AURAL');
    const data = response.body.data;

    expect(data.respondentCount).toBe(1);
    expect(data.mean).toBe(60);
    const row = data.students.find(
      (item: { studentId: string }) => item.studentId === studentAId,
    );
    expect(row).toMatchObject({
      vark: 'AURAL',
      originCategories: ['VISUAL', 'AURAL'],
      feedback: { VISUAL: 75, AURAL: 60 },
    });
  });

  it('accepts a historical category filter for a Kinesthetic-session student', async () => {
    // Before FR-030 was fixed this returned 403 VARK_MISMATCH even though the
    // Visual 75 value was still stored for the same topic.
    const response = await request(app)
      .get(
        '/api/v1/lecturer/analytics/topics/' +
          topicId +
          '?category=VISUAL&studentId=' +
          studentAId,
      )
      .set('Authorization', bearer(lecturerToken))
      .expect(200);

    expect(response.body.data.respondentCount).toBe(1);
    expect(response.body.data.mean).toBe(75);
    expect(response.body.data.students[0].vark).toBe('VISUAL');
  });

  it('keeps the Kinesthetic aggregate at 2 assessments / mean 90 after logout, VARK change, and LEFT', async () => {
    const response = await getAnalytics();
    const data = response.body.data;

    // studentK1 is logged out and LEFT; studentK2 now has an AURAL session;
    // neither session state may remove their final assessments.
    expect(data.kinesthetic.assessedCount).toBe(2);
    expect(data.kinesthetic.meanScore).toBe(90);

    // Subjective V/A/R aggregate stays separate: (75 + 60) / 2.
    expect(data.mean).toBe(67.5);
    expect(data.respondentCount).toBe(1);

    // Compatibility field keeps the pre-existing definition (students with an
    // active Kinesthetic session and no submission): only studentA here.
    expect(data.kinesthetic.notSubmittedCount).toBe(1);
    expect(data.definitions.kinestheticAssessedCount).toContain('enrollment LEFT');
  });
});
