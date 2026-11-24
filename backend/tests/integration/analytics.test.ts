import request from 'supertest';
import { afterAll, beforeEach, describe, expect, it } from 'vitest';
import { createApp } from '../../src/app';
import { resetLoginThrottle } from '../../src/lib/login-throttle';
import { closeDb, createAdmin, createClass, createUser, resetDb } from '../helpers/db';
import {
  createEnrollment,
  createMaterialLink,
  createTeachingAssignment,
  createTopic,
} from '../helpers/fixtures';
import { bearer, loginAs } from '../helpers/api';

const app = createApp();
const describeDb = process.env.DATABASE_URL ? describe : describe.skip;

const TEST_ADMIN_LOGIN_ID = '01401400001';

let classId: string;
let lecturerId: string;
let topicId: string;
let lecturerToken: string;
let adminToken: string;
let student1Id: string;
let student3Id: string;
let student4Id: string;
let student5Id: string;
let student1Token: string;
let student2Token: string;
let student3Token: string;
let student4Token: string;
let student5Token: string;

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

async function putFeedback(token: string, value: number) {
  return request(app)
    .put('/api/v1/topics/' + topicId + '/feedback')
    .set('Authorization', bearer(token))
    .send({ value })
    .expect(200);
}

async function logout(token: string) {
  await request(app)
    .post('/api/v1/auth/logout')
    .set('Authorization', bearer(token))
    .expect(204);
}

async function getAnalytics(query = '') {
  return request(app)
    .get('/api/v1/lecturer/analytics/topics/' + topicId + query)
    .set('Authorization', bearer(lecturerToken))
    .expect(200);
}

function studentsFrom(response: { body: { data: { students: Array<Record<string, unknown>> } } }) {
  return response.body.data.students;
}

describeDb('analytics integration', () => {
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
    student1Id = await createStudent('2025000001');
    await createStudent('2025000002');
    student3Id = await createStudent('2025000003');
    student4Id = await createStudent('2025000004');
    student5Id = await createStudent('2025000005');

    topicId = await createTopic({
      classId,
      createdBy: lecturerId,
      title: 'Topik Analitik',
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
    const admin = await loginAs(app, TEST_ADMIN_LOGIN_ID, 'AdminPass1234');
    adminToken = admin.accessToken;

    const student1 = await loginStudent('2025000001', 'VISUAL');
    student1Token = student1.accessToken;
    const student2 = await loginStudent('2025000002', 'VISUAL');
    student2Token = student2.accessToken;
    const student3 = await loginStudent('2025000003', 'AURAL');
    student3Token = student3.accessToken;
    const student4 = await loginStudent('2025000004', 'VISUAL');
    student4Token = student4.accessToken;
    const student5 = await loginStudent('2025000005', 'KINESTHETIC');
    student5Token = student5.accessToken;

    await putFeedback(student1Token, 40);
    await putFeedback(student2Token, 60);
    await putFeedback(student3Token, 80);

    await request(app)
      .put('/api/v1/lecturer/topics/' + topicId + '/case')
      .set('Authorization', bearer(lecturerToken))
      .send({
        theory: 'Teori',
        instructions: 'Instruksi',
        deadlineAt: new Date(Date.now() + 86400000).toISOString(),
      })
      .expect(200);

    const submitted = await request(app)
      .post('/api/v1/topics/' + topicId + '/case/submission')
      .set('Authorization', bearer(student5Token))
      .send({ answerText: 'Jawaban Kinesthetic.' })
      .expect(201);

    await request(app)
      .post('/api/v1/submissions/' + submitted.body.data.submissionId + '/assessment')
      .set('Authorization', bearer(lecturerToken))
      .send({ score: 80, comment: 'Baik.' })
      .expect(201);
  });

  afterAll(async () => {
    await closeDb();
  });

  it('aggregates VISUAL feedback by its stored origin category', async () => {
    const response = await getAnalytics('?category=VISUAL');
    const data = response.body.data;

    expect(data.category).toBe('VISUAL');
    expect(data.respondentCount).toBe(2);
    expect(data.mean).toBe(50);
    // Legacy population for category=VISUAL: students with Visual feedback
    // (student1, student2) plus the active Visual session of student4.
    expect(data.notRespondedCount).toBe(1);
    expect(data.histogram).toEqual([
      { bucket: '0-20', count: 0 },
      { bucket: '21-40', count: 1 },
      { bucket: '41-60', count: 1 },
      { bucket: '61-80', count: 0 },
      { bucket: '81-100', count: 0 },
    ]);
    // Whole class population is listed; everyone without a Visual value is
    // shown as "belum merespons" (null) instead of being dropped or counted 0.
    expect(data.students).toHaveLength(5);

    const byId = new Map(
      studentsFrom(response).map((row) => [row.studentId as string, row]),
    );
    expect(byId.get(student1Id)).toMatchObject({
      vark: 'VISUAL',
      originCategories: ['VISUAL'],
      feedback: { VISUAL: 40, AURAL: null, READ_WRITE: null },
    });
    expect(byId.get(student3Id)).toMatchObject({
      vark: null,
      originCategories: ['AURAL'],
      feedback: { VISUAL: null, AURAL: 80, READ_WRITE: null },
    });
    expect(byId.get(student4Id)).toMatchObject({
      vark: null,
      originCategories: [],
      feedback: { VISUAL: null, AURAL: null, READ_WRITE: null },
    });
  });

  it('aggregates all V/A/R categories when no filter is given', async () => {
    const response = await getAnalytics();
    const data = response.body.data;

    expect(data.category).toBeNull();
    expect(data.respondentCount).toBe(3);
    expect(data.mean).toBe(60);
    // Legacy population without filter: everyone with V/A/R feedback or an
    // active V/A/R session (student5 is Kinesthetic => excluded).
    expect(data.notRespondedCount).toBe(1);
    expect(data.histogram).toEqual([
      { bucket: '0-20', count: 0 },
      { bucket: '21-40', count: 1 },
      { bucket: '41-60', count: 1 },
      { bucket: '61-80', count: 1 },
      { bucket: '81-100', count: 0 },
    ]);

    const byId = new Map(
      studentsFrom(response).map((row) => [row.studentId as string, row]),
    );
    expect(byId.get(student1Id)?.vark).toBe('VISUAL');
    expect(byId.get(student3Id)?.vark).toBe('AURAL');
    expect(byId.get(student5Id)?.vark).toBeNull();
  });

  it('keeps Kinesthetic aggregate separate from subjective feedback', async () => {
    const response = await getAnalytics();
    const data = response.body.data;

    expect(data.mean).toBe(60);
    expect(data.kinesthetic.assessedCount).toBe(1);
    expect(data.kinesthetic.meanScore).toBe(80);
    expect(data.kinesthetic.notSubmittedCount).toBe(0);
    // The Kinesthetic row of a Kinesthetic-session student is not mixed into
    // the V/A/R aggregate nor into the respondent count.
    expect(
      studentsFrom(response).find((row) => row.studentId === student5Id)?.vark,
    ).toBeNull();
  });

  it('publishes the aggregation definition and compatibility field definitions', async () => {
    const response = await getAnalytics('?category=VISUAL');
    const data = response.body.data;

    expect(typeof data.aggregation).toBe('string');
    expect(data.aggregation).toContain('feedback terbaru');
    expect(data.aggregation).toContain('tidak dihitung sebagai nilai 0%');
    expect(data.definitions).toMatchObject({
      respondentCount: expect.any(String),
      notRespondedCount: expect.any(String),
      kinestheticNotSubmittedCount: expect.any(String),
    });
    expect(data.definitions.notRespondedCount).toContain('FR-048');
  });

  it('filters by student and category', async () => {
    const response = await getAnalytics(
      '?category=VISUAL&studentId=' + student1Id,
    );
    const data = response.body.data;
    expect(data.studentId).toBe(student1Id);
    expect(data.respondentCount).toBe(1);
    expect(data.mean).toBe(40);
    expect(data.notRespondedCount).toBe(0);
    expect(data.students).toHaveLength(1);
    expect(data.students[0].vark).toBe('VISUAL');
  });

  it('returns null mean and "belum merespons" for a student without feedback', async () => {
    const response = await getAnalytics(
      '?category=VISUAL&studentId=' + student4Id,
    );
    const data = response.body.data;
    expect(data.respondentCount).toBe(0);
    expect(data.mean).toBeNull();
    expect(data.notRespondedCount).toBe(1);
    expect(data.students[0]).toMatchObject({
      vark: null,
      originCategories: [],
      feedback: { VISUAL: null, AURAL: null, READ_WRITE: null },
    });
  });

  it('keeps stored feedback visible in its origin category after logout', async () => {
    await logout(student1Token);

    const response = await getAnalytics('?category=VISUAL');
    const data = response.body.data;
    expect(data.respondentCount).toBe(2);
    expect(data.mean).toBe(50);
    const row = studentsFrom(response).find((item) => item.studentId === student1Id);
    expect(row).toMatchObject({ vark: 'VISUAL', feedback: { VISUAL: 40 } });
  });

  it('reads a historical category even when the latest login VARK is different', async () => {
    const student6Id = await createStudent('2025000006');
    const visualSession = await loginStudent('2025000006', 'VISUAL');
    await request(app)
      .put('/api/v1/topics/' + topicId + '/feedback')
      .set('Authorization', bearer(visualSession.accessToken))
      .send({ value: 75 })
      .expect(200);
    // Last login is Kinesthetic; the old API rejected this historical filter
    // with VARK_MISMATCH although the stored Visual value still exists.
    await loginStudent('2025000006', 'KINESTHETIC');

    const visual = await getAnalytics(
      '?category=VISUAL&studentId=' + student6Id,
    );
    expect(visual.body.data.respondentCount).toBe(1);
    expect(visual.body.data.mean).toBe(75);
    expect(visual.body.data.students[0]).toMatchObject({
      vark: 'VISUAL',
      originCategories: ['VISUAL'],
      feedback: { VISUAL: 75 },
    });

    const aural = await getAnalytics(
      '?category=AURAL&studentId=' + student6Id,
    );
    expect(aural.body.data.respondentCount).toBe(0);
    expect(aural.body.data.mean).toBeNull();
    expect(aural.body.data.students[0].vark).toBeNull();
  });

  it('counts a stored 0% separately from a missing feedback (null)', async () => {
    await putFeedback(student4Token, 0);

    const response = await getAnalytics('?category=VISUAL');
    const data = response.body.data;

    expect(data.respondentCount).toBe(3);
    expect(data.mean).toBe(33.33);
    // A stored 0% is a response: student4 joins the legacy population and is
    // not counted as "not responded".
    expect(data.notRespondedCount).toBe(0);
    expect(data.histogram[0]).toEqual({ bucket: '0-20', count: 1 });

    const row = studentsFrom(response).find((item) => item.studentId === student4Id);
    expect(row).toMatchObject({ vark: 'VISUAL', feedback: { VISUAL: 0 } });
    expect(row?.feedback).not.toBeNull();
  });

  it('restores the session-based respondent population for notRespondedCount', async () => {
    const readWriteNoFeedbackId = await createStudent('2025000007');
    await loginStudent('2025000007', 'READ_WRITE');

    const readWrite = await getAnalytics('?category=READ_WRITE');
    const rwData = readWrite.body.data;
    // Legacy population for READ_WRITE: only the active Read-Write session of
    // this student (nobody stored Read-Write feedback).
    expect(rwData.respondentCount).toBe(0);
    expect(rwData.mean).toBeNull();
    expect(rwData.notRespondedCount).toBe(1);

    const visual = await getAnalytics('?category=VISUAL');
    const visualData = visual.body.data;
    // Visual feedback: student1 + student2 (responded). student4 has an active
    // Visual session without feedback => the only non-respondent.
    expect(visualData.respondentCount).toBe(2);
    expect(visualData.notRespondedCount).toBe(1);
    // The class list itself stays the full FR-029 population (all 6 students,
    // including the Read-Write session student just created).
    expect(studentsFrom(visual)).toHaveLength(6);
    // Students outside the legacy population still appear as "belum merespons".
    expect(
      studentsFrom(visual).find((row) => row.studentId === student5Id)?.vark,
    ).toBeNull();
    expect(
      studentsFrom(visual).find((row) => row.studentId === readWriteNoFeedbackId)?.vark,
    ).toBeNull();
  });

  it('keeps notRespondedCount and notSubmittedCount as compatibility fields', async () => {
    // Student 4 switches to a Kinesthetic session without submitting anything.
    await loginStudent('2025000004', 'KINESTHETIC');

    const response = await getAnalytics('?category=VISUAL');
    const data = response.body.data;

    // student4 is now Kinesthetic-session and without feedback, so it is no
    // longer part of the legacy Visual population; only student1/student2
    // remain there and both responded.
    expect(data.notRespondedCount).toBe(0);
    expect(data.kinesthetic.assessedCount).toBe(1);
    expect(data.kinesthetic.meanScore).toBe(80);
    expect(data.kinesthetic.notSubmittedCount).toBe(1);
    expect(data.definitions.kinestheticNotSubmittedCount).toContain('FR-048');
  });

  it('rejects a lecturer outside the class, ADMIN, and an invalid category', async () => {
    const otherLecturer = await loginAs(app, '1990010002', 'LecturerPass123');
    const forbidden = await request(app)
      .get('/api/v1/lecturer/analytics/topics/' + topicId)
      .set('Authorization', bearer(otherLecturer.accessToken))
      .expect(403);
    expect(forbidden.body.error.code).toBe('CLASS_FORBIDDEN');

    const admin = await request(app)
      .get('/api/v1/lecturer/analytics/topics/' + topicId)
      .set('Authorization', bearer(adminToken))
      .expect(403);
    expect(admin.body.error.code).toBe('FORBIDDEN');

    const invalid = await request(app)
      .get('/api/v1/lecturer/analytics/topics/' + topicId + '?category=KINESTHETIC')
      .set('Authorization', bearer(lecturerToken))
      .expect(422);
    expect(invalid.body.error.code).toBe('VALIDATION_ERROR');

    const outsider = await createUser({
      loginId: '2025000099',
      password: 'StudentPass123',
      role: 'STUDENT',
    });
    const notInClass = await request(app)
      .get(
        '/api/v1/lecturer/analytics/topics/' + topicId + '?studentId=' + outsider,
      )
      .set('Authorization', bearer(lecturerToken))
      .expect(404);
    expect(notInClass.body.error.code).toBe('STUDENT_NOT_FOUND');
  });
});
