import request from 'supertest';
import { afterAll, beforeEach, describe, expect, it } from 'vitest';
import { createApp } from '../../src/app';
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

let classId: string;
let lecturerId: string;
let topicId: string;
let lecturerToken: string;
let student1Id: string;
let student3Id: string;
let student4Id: string;

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

async function getAnalytics(query = '') {
  return request(app)
    .get('/api/v1/lecturer/analytics/topics/' + topicId + query)
    .set('Authorization', bearer(lecturerToken))
    .expect(200);
}

describeDb('analytics integration', () => {
  beforeEach(async () => {
    await resetDb();
    resetLoginThrottle();
    classId = await createClass();
    lecturerId = await createUser({
      loginId: 'lect1',
      password: 'LecturerPass123',
      role: 'LECTURER',
    });
    await createUser({
      loginId: 'lect2',
      password: 'LecturerPass123',
      role: 'LECTURER',
    });
    await createTeachingAssignment(classId, lecturerId);
    student1Id = await createStudent('stu1');
    await createStudent('stu2');
    student3Id = await createStudent('stu3');
    student4Id = await createStudent('stu4');
    await createStudent('stu5');

    topicId = await createTopic({
      classId,
      createdBy: lecturerId,
      title: 'Topik Analitik',
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

    const lecturer = await loginAs(app, 'lect1', 'LecturerPass123');
    lecturerToken = lecturer.accessToken;
    const student1 = await loginStudent('stu1', 'VISUAL');
    const student2 = await loginStudent('stu2', 'VISUAL');
    const student3 = await loginStudent('stu3', 'AURAL');
    await loginStudent('stu4', 'VISUAL');
    const student5 = await loginStudent('stu5', 'KINESTHETIC');

    await putFeedback(student1.accessToken, 40);
    await putFeedback(student2.accessToken, 60);
    await putFeedback(student3.accessToken, 80);

    const createdCase = await request(app)
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
      .set('Authorization', bearer(student5.accessToken))
      .send({ answerText: 'Jawaban Kinesthetic.' })
      .expect(201);

    await request(app)
      .post('/api/v1/submissions/' + submitted.body.data.submissionId + '/assessment')
      .set('Authorization', bearer(lecturerToken))
      .send({ score: 80, comment: 'Baik.' })
      .expect(201);

    expect(createdCase.body.data.id).toBeTruthy();
  });

  afterAll(async () => {
    await closeDb();
  });

  it('aggregates VISUAL feedback with n, mean, histogram, and not-responded count', async () => {
    const response = await getAnalytics('?category=VISUAL');
    const data = response.body.data;

    expect(data.category).toBe('VISUAL');
    expect(data.respondentCount).toBe(2);
    expect(data.mean).toBe(50);
    expect(data.notRespondedCount).toBe(1);
    expect(data.histogram).toEqual([
      { bucket: '0-20', count: 0 },
      { bucket: '21-40', count: 1 },
      { bucket: '41-60', count: 1 },
      { bucket: '61-80', count: 0 },
      { bucket: '81-100', count: 0 },
    ]);
    expect(data.students).toHaveLength(3);
    expect(data.aggregation).toBe('mean of latest feedback per student-topic-category');
  });

  it('keeps Kinesthetic assessment separate from subjective feedback', async () => {
    const response = await getAnalytics('?category=VISUAL');
    const data = response.body.data;

    expect(data.mean).toBe(50);
    expect(data.kinesthetic.assessedCount).toBe(1);
    expect(data.kinesthetic.meanScore).toBe(80);
    expect(data.kinesthetic.notSubmittedCount).toBe(0);
  });

  it('aggregates all V/A/R categories when no filter is given', async () => {
    const response = await getAnalytics();
    const data = response.body.data;

    expect(data.category).toBeNull();
    expect(data.respondentCount).toBe(3);
    expect(data.mean).toBe(60);
    expect(data.notRespondedCount).toBe(1);
    expect(data.histogram).toEqual([
      { bucket: '0-20', count: 0 },
      { bucket: '21-40', count: 1 },
      { bucket: '41-60', count: 1 },
      { bucket: '61-80', count: 1 },
      { bucket: '81-100', count: 0 },
    ]);
  });

  it('filters by student and category', async () => {
    const response = await getAnalytics(
      '?category=VISUAL&studentId=' + student1Id,
    );
    const data = response.body.data;
    expect(data.studentId).toBe(student1Id);
    expect(data.respondentCount).toBe(1);
    expect(data.mean).toBe(40);
    expect(data.students).toHaveLength(1);
  });

  it('returns empty mean when the selected student has no feedback', async () => {
    const response = await getAnalytics(
      '?category=VISUAL&studentId=' + student4Id,
    );
    const data = response.body.data;
    expect(data.respondentCount).toBe(0);
    expect(data.mean).toBeNull();
    expect(data.notRespondedCount).toBe(1);
  });

  it('rejects a VARK/category mismatch, a non-owner, and an invalid category', async () => {
    const mismatch = await request(app)
      .get(
        '/api/v1/lecturer/analytics/topics/' +
          topicId +
          '?category=VISUAL&studentId=' +
          student3Id,
      )
      .set('Authorization', bearer(lecturerToken))
      .expect(403);
    expect(mismatch.body.error.code).toBe('VARK_MISMATCH');

    const otherLecturer = await loginAs(app, 'lect2', 'LecturerPass123');
    const forbidden = await request(app)
      .get('/api/v1/lecturer/analytics/topics/' + topicId)
      .set('Authorization', bearer(otherLecturer.accessToken))
      .expect(403);
    expect(forbidden.body.error.code).toBe('CLASS_FORBIDDEN');

    const invalid = await request(app)
      .get('/api/v1/lecturer/analytics/topics/' + topicId + '?category=KINESTHETIC')
      .set('Authorization', bearer(lecturerToken))
      .expect(422);
    expect(invalid.body.error.code).toBe('VALIDATION_ERROR');
  });
});
