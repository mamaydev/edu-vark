import request from 'supertest';
import { afterAll, beforeEach, describe, expect, it, vi } from 'vitest';
import { createApp } from '../../src/app';
import { getPool } from '../../src/db/client';
import { resetLoginThrottle } from '../../src/lib/login-throttle';
import {
  closeDb,
  createAdmin,
  createClass,
  createUser,
  resetDb,
  TEST_ADMIN_LOGIN_ID,
  TEST_ADMIN_PASSWORD,
} from '../helpers/db';
import {
  createCaseWithSubmission,
  createEnrollment,
  createTeachingAssignment,
} from '../helpers/fixtures';
import { bearer, loginAs } from '../helpers/api';

vi.mock('../../src/lib/storage', () => ({
  putObject: vi.fn(async () => undefined),
  getSignedObjectUrl: vi.fn(async (key: string) => 'https://signed.example/' + key),
  deleteObject: vi.fn(async () => undefined),
  objectStat: vi.fn(async () => ({ size: 1 })),
  listObjectKeys: vi.fn(async () => []),
  storageMode: vi.fn(() => 'local'),
}));

const app = createApp();
const describeDb = process.env.DATABASE_URL ? describe : describe.skip;

let classId: string;
let lecturerId: string;
let studentId: string;

async function loginLecturer() {
  return loginAs(app, '1990010001', 'LecturerPass123');
}

async function loginStudent(loginId = '2025000001', vark = 'VISUAL') {
  const session = await loginAs(app, loginId, 'StudentPass123');
  await request(app)
    .post('/api/v1/auth/vark')
    .set('Authorization', bearer(session.accessToken))
    .send({ vark })
    .expect(200);
  return session;
}

async function createTopic(token: string, title: string) {
  const response = await request(app)
    .post('/api/v1/lecturer/topics')
    .set('Authorization', bearer(token))
    .send({ title })
    .expect(201);
  return response.body.data as { id: string; title: string; status: string };
}

async function publishTopic(token: string, topicId: string) {
  return request(app)
    .post('/api/v1/lecturer/topics/' + topicId + '/publish')
    .set('Authorization', bearer(token))
    .expect(200);
}

async function createStudent(loginId: string, vark: string) {
  const id = await createUser({
    loginId,
    password: 'StudentPass123',
    role: 'STUDENT',
  });
  await createEnrollment(classId, id);
  const session = await loginStudent(loginId, vark);
  return { id, token: session.accessToken };
}

describeDb('topics and materials integration', () => {
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
    studentId = await createUser({
      loginId: '2025000001',
      password: 'StudentPass123',
      role: 'STUDENT',
    });
    await createEnrollment(classId, studentId);
    await createAdmin();
  });

  afterAll(async () => {
    await closeDb();
  });

  it('returns the topic itself from GET /topics/:id, not the dashboard', async () => {
    const lecturer = await loginLecturer();
    const topic = await createTopic(lecturer.accessToken, 'Topik Detail');
    await publishTopic(lecturer.accessToken, topic.id);

    const student = await loginStudent();
    const detail = await request(app)
      .get('/api/v1/topics/' + topic.id)
      .set('Authorization', bearer(student.accessToken))
      .expect(200);

    expect(detail.body.data.id).toBe(topic.id);
    expect(detail.body.data.title).toBe('Topik Detail');
    // Regression guard: a second handler on this route used to answer with the
    // student dashboard shape ({ vark, availableMaterials, ... }) first, which
    // made the Android client fail to deserialize the topic.
    expect(detail.body.data).not.toHaveProperty('vark');
    expect(detail.body.data).not.toHaveProperty('availableMaterials');
  });

  it('publishes without materials and returns the exact unavailable message', async () => {
    const lecturer = await loginLecturer();
    const topic = await createTopic(lecturer.accessToken, 'Topik Tanpa Materi');
    await publishTopic(lecturer.accessToken, topic.id);

    const student = await loginStudent();
    const list = await request(app)
      .get('/api/v1/topics')
      .set('Authorization', bearer(student.accessToken))
      .expect(200);

    const row = list.body.data.find((item: { id: string }) => item.id === topic.id);
    expect(row).toBeTruthy();
    expect(row.materialAvailable).toBe(false);

    const materials = await request(app)
      .get('/api/v1/topics/' + topic.id + '/materials')
      .set('Authorization', bearer(student.accessToken))
      .expect(404);

    expect(materials.body.error.code).toBe('MATERIAL_NOT_AVAILABLE');
    expect(materials.body.error.message).toBe(
      'materi belum tersedia, harap hubungi dosen terlebih dahulu',
    );
  });

  it('hides drafts and unpublished topics from students', async () => {
    const lecturer = await loginLecturer();
    const topic = await createTopic(lecturer.accessToken, 'Topik Draft');
    const student = await loginStudent();

    let list = await request(app)
      .get('/api/v1/topics')
      .set('Authorization', bearer(student.accessToken))
      .expect(200);
    expect(list.body.data.some((item: { id: string }) => item.id === topic.id)).toBe(false);

    await publishTopic(lecturer.accessToken, topic.id);
    list = await request(app)
      .get('/api/v1/topics')
      .set('Authorization', bearer(student.accessToken))
      .expect(200);
    expect(list.body.data.some((item: { id: string }) => item.id === topic.id)).toBe(true);

    await request(app)
      .post('/api/v1/lecturer/topics/' + topic.id + '/unpublish')
      .set('Authorization', bearer(lecturer.accessToken))
      .expect(200);

    list = await request(app)
      .get('/api/v1/topics')
      .set('Authorization', bearer(student.accessToken))
      .expect(200);
    expect(list.body.data.some((item: { id: string }) => item.id === topic.id)).toBe(false);
  });

  it('returns only materials for the session VARK', async () => {
    const lecturer = await loginLecturer();
    const topic = await createTopic(lecturer.accessToken, 'Topik VARK');
    await publishTopic(lecturer.accessToken, topic.id);

    await request(app)
      .post('/api/v1/lecturer/topics/' + topic.id + '/materials/link')
      .set('Authorization', bearer(lecturer.accessToken))
      .send({ title: 'Materi Visual', vark: 'VISUAL', url: 'https://example.com/visual' })
      .expect(201);

    await request(app)
      .post('/api/v1/lecturer/topics/' + topic.id + '/materials/link')
      .set('Authorization', bearer(lecturer.accessToken))
      .send({ title: 'Materi Audio', vark: 'AURAL', url: 'https://example.com/audio' })
      .expect(201);

    const visualStudent = await loginStudent('2025000001', 'VISUAL');
    const visualMaterials = await request(app)
      .get('/api/v1/topics/' + topic.id + '/materials')
      .set('Authorization', bearer(visualStudent.accessToken))
      .expect(200);

    expect(visualMaterials.body.data).toHaveLength(1);
    expect(visualMaterials.body.data[0].title).toBe('Materi Visual');

    const aural = await createStudent('2025000002', 'AURAL');
    const auralMaterials = await request(app)
      .get('/api/v1/topics/' + topic.id + '/materials')
      .set('Authorization', bearer(aural.token))
      .expect(200);

    expect(auralMaterials.body.data).toHaveLength(1);
    expect(auralMaterials.body.data[0].title).toBe('Materi Audio');
  });

  it('soft deletes a topic without academic data', async () => {
    const lecturer = await loginLecturer();
    const topic = await createTopic(lecturer.accessToken, 'Topik Arsip');
    await publishTopic(lecturer.accessToken, topic.id);

    await request(app)
      .delete('/api/v1/lecturer/topics/' + topic.id)
      .set('Authorization', bearer(lecturer.accessToken))
      .expect(204);

    const student = await loginStudent();
    const list = await request(app)
      .get('/api/v1/topics')
      .set('Authorization', bearer(student.accessToken))
      .expect(200);
    expect(list.body.data.some((item: { id: string }) => item.id === topic.id)).toBe(false);
  });

  it('rejects deleting a topic with academic data', async () => {
    const lecturer = await loginLecturer();
    const topic = await createTopic(lecturer.accessToken, 'Topik Bernilai');
    await createCaseWithSubmission({ topicId: topic.id, lecturerId, studentId });

    const response = await request(app)
      .delete('/api/v1/lecturer/topics/' + topic.id)
      .set('Authorization', bearer(lecturer.accessToken))
      .expect(409);

    expect(response.body.error.code).toBe('TOPIC_HAS_ACADEMIC_DATA');
  });

  it('reorders topics and reflects the order to students', async () => {
    const lecturer = await loginLecturer();
    const first = await createTopic(lecturer.accessToken, 'Topik 1');
    const second = await createTopic(lecturer.accessToken, 'Topik 2');
    const third = await createTopic(lecturer.accessToken, 'Topik 3');
    await publishTopic(lecturer.accessToken, first.id);
    await publishTopic(lecturer.accessToken, second.id);
    await publishTopic(lecturer.accessToken, third.id);

    await request(app)
      .patch('/api/v1/lecturer/topics/reorder')
      .set('Authorization', bearer(lecturer.accessToken))
      .send({ topicIds: [third.id, first.id, second.id] })
      .expect(200);

    const student = await loginStudent();
    const list = await request(app)
      .get('/api/v1/topics')
      .set('Authorization', bearer(student.accessToken))
      .expect(200);

    const ids = list.body.data.map((item: { id: string }) => item.id);
    expect(ids).toEqual([third.id, first.id, second.id]);
  });

  it('uploads a valid PDF and exposes the fixed EduVARK content address', async () => {
    const lecturer = await loginLecturer();
    const topic = await createTopic(lecturer.accessToken, 'Topik PDF');
    await publishTopic(lecturer.accessToken, topic.id);

    const pdf = Buffer.from('%PDF-1.7\n1 0 obj\n<<>>\nendobj\n%%EOF');
    const uploaded = await request(app)
      .post('/api/v1/lecturer/topics/' + topic.id + '/materials/file')
      .set('Authorization', bearer(lecturer.accessToken))
      .field('title', 'Materi PDF')
      .field('vark', 'VISUAL')
      .attach('file', pdf, { filename: 'materi.pdf', contentType: 'application/pdf' })
      .expect(201);

    expect(uploaded.body.data.kind).toBe('FILE');
    // C1-02: URL bertanda tangan berhenti diterbitkan; alamat tetap dipakai.
    expect(uploaded.body.data.signedUrl).toBeNull();
    expect(uploaded.body.data.expiresAt).toBeNull();
    expect(uploaded.body.data.contentUrl).toBe(
      '/api/v1/materials/' + uploaded.body.data.id + '/content',
    );

    const student = await loginStudent('2025000001', 'VISUAL');
    const materials = await request(app)
      .get('/api/v1/topics/' + topic.id + '/materials')
      .set('Authorization', bearer(student.accessToken))
      .expect(200);

    expect(materials.body.data).toHaveLength(1);
    expect(materials.body.data[0].signedUrl).toBeNull();
    expect(materials.body.data[0].contentUrl).toBe(
      '/api/v1/materials/' + materials.body.data[0].id + '/content',
    );
  });

  it('trims material titles and rejects blank or oversized titles', async () => {
    const lecturer = await loginLecturer();
    const topic = await createTopic(lecturer.accessToken, 'Topik Validasi Judul');

    const blank = await request(app)
      .post('/api/v1/lecturer/topics/' + topic.id + '/materials/link')
      .set('Authorization', bearer(lecturer.accessToken))
      .send({ title: '   \n  ', vark: 'VISUAL', url: 'https://example.com/a' })
      .expect(422);
    expect(blank.body.error.code).toBe('VALIDATION_ERROR');

    const oversized = await request(app)
      .post('/api/v1/lecturer/topics/' + topic.id + '/materials/link')
      .set('Authorization', bearer(lecturer.accessToken))
      .send({ title: 'a'.repeat(201), vark: 'VISUAL', url: 'https://example.com/a' })
      .expect(422);
    expect(oversized.body.error.code).toBe('VALIDATION_ERROR');

    const accepted = await request(app)
      .post('/api/v1/lecturer/topics/' + topic.id + '/materials/link')
      .set('Authorization', bearer(lecturer.accessToken))
      .send({ title: '  Materi Visual  ', vark: 'VISUAL', url: 'https://example.com/a' })
      .expect(201);
    expect(accepted.body.data.title).toBe('Materi Visual');
  });

  it('lets the owning lecturer list materials without a student VARK', async () => {
    const lecturer = await loginLecturer();
    const topic = await createTopic(lecturer.accessToken, 'Topik Materi Dosen');
    await request(app)
      .post('/api/v1/lecturer/topics/' + topic.id + '/materials/link')
      .set('Authorization', bearer(lecturer.accessToken))
      .send({ title: 'Materi Dosen', vark: 'KINESTHETIC', url: 'https://example.com/dosen' })
      .expect(201);

    const materials = await request(app)
      .get('/api/v1/lecturer/topics/' + topic.id + '/materials')
      .set('Authorization', bearer(lecturer.accessToken))
      .expect(200);
    expect(materials.body.data).toHaveLength(1);
    expect(materials.body.data[0].title).toBe('Materi Dosen');

    const student = await loginStudent('2025000001', 'VISUAL');
    await request(app)
      .get('/api/v1/lecturer/topics/' + topic.id + '/materials')
      .set('Authorization', bearer(student.accessToken))
      .expect(403);
  });

  it('lets the owning lecturer read draft and published topics without VARK', async () => {
    const lecturer = await loginLecturer();
    const topic = await createTopic(lecturer.accessToken, 'Topik Detail Dosen');

    const draft = await request(app)
      .get('/api/v1/lecturer/topics/' + topic.id)
      .set('Authorization', bearer(lecturer.accessToken))
      .expect(200);
    expect(draft.body.data.id).toBe(topic.id);
    expect(draft.body.data.status).toBe('DRAFT');

    await publishTopic(lecturer.accessToken, topic.id);

    const published = await request(app)
      .get('/api/v1/lecturer/topics/' + topic.id)
      .set('Authorization', bearer(lecturer.accessToken))
      .expect(200);
    expect(published.body.data.status).toBe('PUBLISHED');
  });

  it('returns student dashboard counters scoped to enrollment and VARK', async () => {
    const lecturer = await loginLecturer();
    const topic = await createTopic(lecturer.accessToken, 'Topik Dashboard');
    await request(app)
      .post('/api/v1/lecturer/topics/' + topic.id + '/materials/link')
      .set('Authorization', bearer(lecturer.accessToken))
      .send({ title: 'Materi Visual', vark: 'VISUAL', url: 'https://example.com/v' })
      .expect(201);
    await publishTopic(lecturer.accessToken, topic.id);

    const student = await loginStudent('2025000001', 'VISUAL');
    const before = await request(app)
      .get('/api/v1/student/dashboard')
      .set('Authorization', bearer(student.accessToken))
      .expect(200);
    expect(before.body.data).toMatchObject({
      vark: 'VISUAL',
      availableMaterials: 1,
      notCompletedMaterials: 1,
    });

    await request(app)
      .put('/api/v1/topics/' + topic.id + '/feedback')
      .set('Authorization', bearer(student.accessToken))
      .send({ value: 80 })
      .expect(200);

    const after = await request(app)
      .get('/api/v1/student/dashboard')
      .set('Authorization', bearer(student.accessToken))
      .expect(200);
    expect(after.body.data).toMatchObject({
      vark: 'VISUAL',
      availableMaterials: 1,
      notCompletedMaterials: 0,
    });

    const lecturerForbidden = await request(app)
      .get('/api/v1/student/dashboard')
      .set('Authorization', bearer(lecturer.accessToken))
      .expect(403);
    expect(lecturerForbidden.body.error.code).toBe('FORBIDDEN');
  });

  it('rejects a forged file type and a missing file', async () => {
    const lecturer = await loginLecturer();
    const topic = await createTopic(lecturer.accessToken, 'Topik Invalid');

    const forged = await request(app)
      .post('/api/v1/lecturer/topics/' + topic.id + '/materials/file')
      .set('Authorization', bearer(lecturer.accessToken))
      .field('title', 'Palsu')
      .field('vark', 'VISUAL')
      .attach('file', Buffer.from('not really a pdf'), {
        filename: 'palsu.pdf',
        contentType: 'application/pdf',
      })
      .expect(422);

    expect(forged.body.error.code).toBe('INVALID_FILE_TYPE');

    const missing = await request(app)
      .post('/api/v1/lecturer/topics/' + topic.id + '/materials/file')
      .set('Authorization', bearer(lecturer.accessToken))
      .field('title', 'Tanpa File')
      .field('vark', 'VISUAL')
      .expect(422);

    expect(missing.body.error.code).toBe('FILE_REQUIRED');
  });

  it('rejects oversized uploads and preserves feedback after material deletion', async () => {
    const lecturer = await loginLecturer();
    const topic = await createTopic(lecturer.accessToken, 'Topik Hardening');
    await publishTopic(lecturer.accessToken, topic.id);

    const pdf = Buffer.from('%PDF-1.7\n1 0 obj\n<<>>\nendobj\n%%EOF');
    const uploaded = await request(app)
      .post('/api/v1/lecturer/topics/' + topic.id + '/materials/file')
      .set('Authorization', bearer(lecturer.accessToken))
      .field('title', 'Materi Hardening')
      .field('vark', 'VISUAL')
      .attach('file', pdf, { filename: 'materi.pdf', contentType: 'application/pdf' })
      .expect(201);

    const materialId = uploaded.body.data.id as string;
    expect(uploaded.body.data.signedUrl).toBeNull();
    expect(uploaded.body.data.contentUrl).toBe(
      '/api/v1/materials/' + materialId + '/content',
    );

    const student = await loginStudent('2025000001', 'VISUAL');
    await request(app)
      .put('/api/v1/topics/' + topic.id + '/feedback')
      .set('Authorization', bearer(student.accessToken))
      .send({ value: 75 })
      .expect(200);

    const deleted = await request(app)
      .delete('/api/v1/lecturer/materials/' + materialId)
      .set('Authorization', bearer(lecturer.accessToken))
      .expect(200);
    expect(deleted.body.data.id).toBe(materialId);
    expect(deleted.body.data.fileDeletion).toBe('COMPLETED');

    const materials = await request(app)
      .get('/api/v1/topics/' + topic.id + '/materials')
      .set('Authorization', bearer(student.accessToken))
      .expect(404);
    expect(materials.body.error.code).toBe('MATERIAL_NOT_AVAILABLE');
    expect(materials.body.error.message).toBe(
      'materi belum tersedia, harap hubungi dosen terlebih dahulu',
    );

    const feedback = await request(app)
      .get('/api/v1/topics/' + topic.id + '/feedback')
      .set('Authorization', bearer(student.accessToken))
      .expect(404);
    expect(feedback.body.error.code).toBe('MATERIAL_NOT_AVAILABLE');

    const count = await getPool().query<{ count: string }>(
      'SELECT count(*)::text AS count FROM feedback WHERE topic_id = $1',
      [topic.id],
    );
    expect(Number(count.rows[0]?.count)).toBe(1);

    const oversized = Buffer.alloc(2 * 1024 * 1024);
    oversized.write('%PDF-');
    const tooLarge = await request(app)
      .post('/api/v1/lecturer/topics/' + topic.id + '/materials/file')
      .set('Authorization', bearer(lecturer.accessToken))
      .field('title', 'Besar')
      .field('vark', 'VISUAL')
      .attach('file', oversized, {
        filename: 'besar.pdf',
        contentType: 'application/pdf',
      })
      .expect(413);
    expect(tooLarge.body.error.code).toBe('FILE_TOO_LARGE');
  });

  it('enforces RBAC and the VARK gate', async () => {
    const student = await loginAs(app, '2025000001', 'StudentPass123');
    const noVark = await request(app)
      .get('/api/v1/topics')
      .set('Authorization', bearer(student.accessToken))
      .expect(428);
    expect(noVark.body.error.code).toBe('VARK_REQUIRED');

    const studentToken = await loginStudent('2025000001', 'VISUAL');
    const studentCreate = await request(app)
      .post('/api/v1/lecturer/topics')
      .set('Authorization', bearer(studentToken.accessToken))
      .send({ title: 'Tidak Boleh' })
      .expect(403);
    expect(studentCreate.body.error.code).toBe('FORBIDDEN');

    const admin = await loginAs(app, TEST_ADMIN_LOGIN_ID, TEST_ADMIN_PASSWORD);
    const adminCreate = await request(app)
      .post('/api/v1/lecturer/topics')
      .set('Authorization', bearer(admin.accessToken))
      .send({ title: 'Admin Tidak Boleh' })
      .expect(403);
    expect(adminCreate.body.error.code).toBe('FORBIDDEN');
  });
});
