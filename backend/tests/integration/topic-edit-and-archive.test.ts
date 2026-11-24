import request from 'supertest';
import { afterAll, beforeEach, describe, expect, it } from 'vitest';
import { createApp } from '../../src/app';
import { resetLoginThrottle } from '../../src/lib/login-throttle';
import { closeDb, createClass, createUser, resetDb } from '../helpers/db';
import {
  createCaseWithSubmission,
  createEnrollment,
  createTeachingAssignment,
  createTopic,
} from '../helpers/fixtures';
import { bearer, loginAs } from '../helpers/api';

/**
 * Remediasi cross-check P5 (FR-009 / DEC-002):
 * - PATCH topik harus membedakan `description` DIHILANGKAN (tidak berubah) dari
 *   `description: null` (dikosongkan) dan menyimpannya secara persisten.
 * - Topik yang sudah punya submission tidak boleh dihapus (arsip), dan penolakan
 *   harus terlihat sebagai non-2xx agar Android tidak menampilkan sukses palsu.
 */
const app = createApp();
const describeDb = process.env.DATABASE_URL ? describe : describe.skip;

const LECTURER_LOGIN_ID = '1990010002';
const LECTURER_PASSWORD = 'LecturerPass123';
const STUDENT_LOGIN_ID = '2025000002';
const STUDENT_PASSWORD = 'StudentPass123';

let lecturerToken: string;
let studentId: string;
let classId: string;
let lecturerId: string;

function patchTopic(topicId: string, body: Record<string, unknown>) {
  return request(app)
    .patch('/api/v1/lecturer/topics/' + topicId)
    .set('Authorization', bearer(lecturerToken))
    .send(body);
}

function getLecturerTopic(topicId: string) {
  return request(app)
    .get('/api/v1/lecturer/topics/' + topicId)
    .set('Authorization', bearer(lecturerToken));
}

async function createPublishedTopic(title: string, sortOrder = 1): Promise<string> {
  return createTopic({
    classId,
    createdBy: lecturerId,
    title,
    status: 'PUBLISHED',
    sortOrder,
  });
}

describeDb('topic edit description and archive protection', () => {
  beforeEach(async () => {
    await resetDb();
    resetLoginThrottle();
    classId = await createClass();
    lecturerId = await createUser({
      loginId: LECTURER_LOGIN_ID,
      password: LECTURER_PASSWORD,
      role: 'LECTURER',
    });
    await createTeachingAssignment(classId, lecturerId);
    studentId = await createUser({
      loginId: STUDENT_LOGIN_ID,
      password: STUDENT_PASSWORD,
      role: 'STUDENT',
    });
    await createEnrollment(classId, studentId);

    const lecturer = await loginAs(app, LECTURER_LOGIN_ID, LECTURER_PASSWORD);
    lecturerToken = lecturer.accessToken;
  });

  afterAll(async () => {
    await closeDb();
  });

  it('clears description when the body contains description: null and persists it', async () => {
    const topicId = await createPublishedTopic('Topik Deskripsi');
    await patchTopic(topicId, { description: 'Deskripsi awal' }).expect(200);

    const cleared = await patchTopic(topicId, {
      title: 'Topik Deskripsi',
      description: null,
    }).expect(200);

    expect(cleared.body.data.description).toBeNull();
    expect(cleared.body.data.title).toBe('Topik Deskripsi');
    // Publikasi tidak berubah karena edit konten.
    expect(cleared.body.data.status).toBe('PUBLISHED');

    // Buka ulang (GET) memakai ID yang sama: deskripsi tetap kosong.
    const reloaded = await getLecturerTopic(topicId).expect(200);
    expect(reloaded.body.data.id).toBe(topicId);
    expect(reloaded.body.data.description).toBeNull();
  });

  it('keeps existing description when the field is omitted from the body', async () => {
    const topicId = await createPublishedTopic('Topik Tanpa Ubah Deskripsi');
    await patchTopic(topicId, { description: 'Deskripsi tetap' }).expect(200);

    const renamed = await patchTopic(topicId, { title: 'Judul Baru Saja' }).expect(200);

    expect(renamed.body.data.title).toBe('Judul Baru Saja');
    expect(renamed.body.data.description).toBe('Deskripsi tetap');

    const reloaded = await getLecturerTopic(topicId).expect(200);
    expect(reloaded.body.data.description).toBe('Deskripsi tetap');
  });

  it('replaces description text and persists it on reload', async () => {
    const topicId = await createPublishedTopic('Topik Ganti Deskripsi');
    await patchTopic(topicId, { description: 'Lama' }).expect(200);

    await patchTopic(topicId, { description: 'Deskripsi definitif' }).expect(200);

    const reloaded = await getLecturerTopic(topicId).expect(200);
    expect(reloaded.body.data.description).toBe('Deskripsi definitif');
  });

  it('rejects PATCH without any updatable field', async () => {
    const topicId = await createPublishedTopic('Topik Validasi');

    const response = await patchTopic(topicId, {}).expect(422);

    expect(response.body.error.code).toBe('VALIDATION_ERROR');
  });

  it('rejects deleting a topic that already has a submission and keeps the row', async () => {
    const topicId = await createPublishedTopic('Topik dengan Submission');
    await createCaseWithSubmission({ topicId, lecturerId, studentId });

    const response = await request(app)
      .delete('/api/v1/lecturer/topics/' + topicId)
      .set('Authorization', bearer(lecturerToken))
      .expect(409);

    expect(response.body.error.code).toBe('TOPIC_HAS_ACADEMIC_DATA');
    // Topik masih ada dan dapat dibaca (arsip, bukan hapus paksa).
    const reloaded = await getLecturerTopic(topicId).expect(200);
    expect(reloaded.body.data.id).toBe(topicId);
  });

  it('allows deleting a topic without academic data', async () => {
    const topicId = await createPublishedTopic('Topik Tanpa Submission');

    await request(app)
      .delete('/api/v1/lecturer/topics/' + topicId)
      .set('Authorization', bearer(lecturerToken))
      .expect(204);

    await getLecturerTopic(topicId).expect(404);
  });
});
