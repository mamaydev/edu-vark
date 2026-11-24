import request from 'supertest';
import { afterAll, beforeEach, describe, expect, it } from 'vitest';
import { createApp } from '../../src/app';
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
const TOPIC_COUNT = 55;

let lecturerToken: string;
let studentToken: string;
let topicIds: string[];

function listLecturer(page: number, pageSize: number) {
  return request(app)
    .get('/api/v1/lecturer/topics?page=' + page + '&pageSize=' + pageSize)
    .set('Authorization', bearer(lecturerToken));
}

function listStudent(page: number, pageSize: number) {
  return request(app)
    .get('/api/v1/topics?page=' + page + '&pageSize=' + pageSize)
    .set('Authorization', bearer(studentToken));
}

describeDb('topic pagination and reorder', () => {
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

    topicIds = [];
    for (let index = 1; index <= TOPIC_COUNT; index += 1) {
      topicIds.push(
        await createTopic({
          classId,
          createdBy: lecturerId,
          title: 'Topik ' + String(index).padStart(2, '0'),
          status: 'PUBLISHED',
          sortOrder: index,
        }),
      );
    }

    const lecturer = await loginAs(app, LECTURER_LOGIN_ID, LECTURER_PASSWORD);
    lecturerToken = lecturer.accessToken;
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

  it('paginates lecturer topics with default page size 20 and reports total', async () => {
    const first = await listLecturer(1, 20).expect(200);
    expect(first.body.data).toHaveLength(20);
    expect(first.body.meta).toEqual({ page: 1, pageSize: 20, total: TOPIC_COUNT });
    expect(first.body.data[0].title).toBe('Topik 01');
    expect(first.body.data[19].title).toBe('Topik 20');

    const third = await listLecturer(3, 20).expect(200);
    expect(third.body.data).toHaveLength(15);
    expect(third.body.data[0].title).toBe('Topik 41');
    expect(third.body.data[14].title).toBe('Topik 55');
  });

  it('caps page size at 100', async () => {
    const all = await listLecturer(1, 100).expect(200);
    expect(all.body.data).toHaveLength(TOPIC_COUNT);
    expect(all.body.meta.total).toBe(TOPIC_COUNT);

    const tooLarge = await listLecturer(1, 101);
    expect(tooLarge.status).toBe(422);
    expect(tooLarge.body.error.code).toBe('VALIDATION_ERROR');
  });

  it('reorders all live topics and students see the saved order', async () => {
    const reversed = [...topicIds].reverse();

    await request(app)
      .patch('/api/v1/lecturer/topics/reorder')
      .set('Authorization', bearer(lecturerToken))
      .send({ topicIds: reversed })
      .expect(200);

    const titleById = new Map(
      topicIds.map((id, index) => [id, 'Topik ' + String(index + 1).padStart(2, '0')]),
    );
    const firstPage = await listStudent(1, 20).expect(200);
    expect(firstPage.body.meta.total).toBe(TOPIC_COUNT);
    expect(firstPage.body.data.map((topic: { title: string }) => topic.title)).toEqual(
      reversed.slice(0, 20).map((id) => titleById.get(id)),
    );

    const secondPage = await listStudent(2, 20).expect(200);
    expect(secondPage.body.data[0].title).toBe('Topik 35');

    const lastPage = await listStudent(3, 20).expect(200);
    expect(lastPage.body.data).toHaveLength(15);
    expect(lastPage.body.data[14].title).toBe('Topik 01');
  });

  it('keeps the complete order when the full list is sent in one reorder', async () => {
    const reversed = [...topicIds].reverse();
    await request(app)
      .patch('/api/v1/lecturer/topics/reorder')
      .set('Authorization', bearer(lecturerToken))
      .send({ topicIds: reversed })
      .expect(200);

    const all = await listStudent(1, 100).expect(200);
    const returnedIds = all.body.data.map((topic: { id: string }) => topic.id);
    expect(returnedIds).toEqual(reversed);
  });

  it('rejects an incomplete reorder list', async () => {
    const response = await request(app)
      .patch('/api/v1/lecturer/topics/reorder')
      .set('Authorization', bearer(lecturerToken))
      .send({ topicIds: topicIds.slice(0, TOPIC_COUNT - 1) });

    expect(response.status).toBe(422);
    expect(response.body.error.code).toBe('TOPIC_ORDER_INCOMPLETE');
    expect(response.body.error.message).toBe(
      'Daftar urutan harus memuat seluruh topik aktif.',
    );

    const first = await listLecturer(1, 20).expect(200);
    expect(first.body.data[0].title).toBe('Topik 01');
  });

  it('rejects a duplicate id inside the reorder list', async () => {
    const duplicated = [...topicIds];
    duplicated[0] = duplicated[1]!;

    const response = await request(app)
      .patch('/api/v1/lecturer/topics/reorder')
      .set('Authorization', bearer(lecturerToken))
      .send({ topicIds: duplicated });

    expect(response.status).toBe(422);
    expect(response.body.error.code).toBe('VALIDATION_ERROR');
  });
});
