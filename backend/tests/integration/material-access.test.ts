import { mkdtempSync } from 'node:fs';
import { unlink } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import path from 'node:path';
import request from 'supertest';
import { afterAll, beforeEach, describe, expect, it } from 'vitest';
import { createApp } from '../../src/app';
import { getPool } from '../../src/db/client';
import { resetLoginThrottle } from '../../src/lib/login-throttle';
import { getSignedObjectUrl, putObject } from '../../src/lib/storage';
import { assertSecureLinkTarget } from '../../src/modules/materials/materials.service';
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
  createEnrollment,
  createMaterialLink,
  createTeachingAssignment,
} from '../helpers/fixtures';
import { bearer, loginAs } from '../helpers/api';

// Storage lokal privat + TTL URL lama yang sangat pendek dipakai untuk
// membuktikan perilaku kedaluwarsa tautan yang sudah terlanjur terbit (C1-02).
const storageRoot = mkdtempSync(path.join(tmpdir(), 'eduvark-p2-storage-'));
process.env.LOCAL_STORAGE_DIR = storageRoot;
process.env.R2_SIGNED_URL_TTL_SECONDS = '1';

const app = createApp();
const describeDb = process.env.DATABASE_URL ? describe : describe.skip;

let classId: string;
let lecturerId: string;
let studentId: string;

const LECTURER_LOGIN = '1990010001';
const LECTURER_PASSWORD = 'LecturerPass123';
const STUDENT_LOGIN = '2025000001';
const STUDENT_PASSWORD = 'StudentPass123';

function smartParser(
  res: NodeJS.ReadableStream & {
    headers?: Record<string, string>;
    on: (event: string, handler: (...args: never[]) => void) => void;
  },
  callback: (error: Error | null, body: unknown) => void,
): void {
  const chunks: Buffer[] = [];
  res.on('data', (chunk: Buffer) => chunks.push(chunk));
  res.on('end', () => {
    const buffer = Buffer.concat(chunks);
    const contentType = String(res.headers?.['content-type'] ?? '');
    if (contentType.includes('application/json')) {
      try {
        callback(null, JSON.parse(buffer.toString('utf8')));
        return;
      } catch {
        // fall through to raw buffer
      }
    }
    callback(null, buffer);
  });
}

const binaryParser = smartParser;

function getContent(token: string, materialId: string, headers: Record<string, string> = {}) {
  let probe = request(app)
    .get('/api/v1/materials/' + materialId + '/content')
    .set('Authorization', bearer(token));
  for (const [name, value] of Object.entries(headers)) {
    probe = probe.set(name, value);
  }
  return probe.buffer(true).parse(binaryParser);
}

async function loginLecturer(loginId = LECTURER_LOGIN, password = LECTURER_PASSWORD) {
  return loginAs(app, loginId, password);
}

async function loginStudent(
  loginId = STUDENT_LOGIN,
  password = STUDENT_PASSWORD,
  vark: 'VISUAL' | 'AURAL' | 'READ_WRITE' | 'KINESTHETIC' = 'VISUAL',
) {
  const session = await loginAs(app, loginId, password);
  await request(app)
    .post('/api/v1/auth/vark')
    .set('Authorization', bearer(session.accessToken))
    .send({ vark })
    .expect(200);
  return session;
}

async function createTopicApi(token: string, title: string) {
  const response = await request(app)
    .post('/api/v1/lecturer/topics')
    .set('Authorization', bearer(token))
    .send({ title })
    .expect(201);
  return response.body.data as { id: string };
}

async function publishTopicApi(token: string, topicId: string) {
  await request(app)
    .post('/api/v1/lecturer/topics/' + topicId + '/publish')
    .set('Authorization', bearer(token))
    .expect(200);
}

const PDF_BYTES = Buffer.from(
  '%PDF-1.7\n1 0 obj\n<< /Type /Catalog >>\nendobj\n%%EOF\n',
);

async function uploadFileApi(
  token: string,
  topicId: string,
  input: {
    title: string;
    vark: 'VISUAL' | 'AURAL' | 'READ_WRITE' | 'KINESTHETIC';
    content?: Buffer;
    filename?: string;
    contentType?: string;
  },
) {
  const response = await request(app)
    .post('/api/v1/lecturer/topics/' + topicId + '/materials/file')
    .set('Authorization', bearer(token))
    .field('title', input.title)
    .field('vark', input.vark)
    .attach('file', input.content ?? PDF_BYTES, {
      filename: input.filename ?? 'materi.pdf',
      contentType: input.contentType ?? 'application/pdf',
    })
    .expect(201);
  return response.body.data as {
    id: string;
    contentUrl: string | null;
    linkTargetUrl: string | null;
    signedUrl: string | null;
    expiresAt: string | null;
  };
}

async function objectKeyOf(materialId: string): Promise<string> {
  const result = await getPool().query<{ object_key: string }>(
    'SELECT object_key FROM materials WHERE id = $1',
    [materialId],
  );
  const key = result.rows[0]?.object_key;
  if (!key) throw new Error('object_key tidak ditemukan');
  return key;
}

beforeEach(async () => {
  await resetDb();
  resetLoginThrottle();
  classId = await createClass();
  lecturerId = await createUser({
    loginId: LECTURER_LOGIN,
    password: LECTURER_PASSWORD,
    role: 'LECTURER',
  });
  await createTeachingAssignment(classId, lecturerId);
  studentId = await createUser({
    loginId: STUDENT_LOGIN,
    password: STUDENT_PASSWORD,
    role: 'STUDENT',
  });
  await createEnrollment(classId, studentId);
  await createAdmin();
});

afterAll(async () => {
  await closeDb();
});

describeDb('fixed material content access (FR-007/014/015, C1-02)', () => {
  it('AT-01: known fixed address di luar VARK sesi tidak mengirim isi materi', async () => {
    const lecturer = await loginLecturer();
    const topic = await createTopicApi(lecturer.accessToken, 'Topik AT-01');
    await publishTopicApi(lecturer.accessToken, topic.id);
    const material = await uploadFileApi(lecturer.accessToken, topic.id, {
      title: 'Materi Aural',
      vark: 'AURAL',
    });

    const student = await loginStudent(STUDENT_LOGIN, STUDENT_PASSWORD, 'VISUAL');

    // Daftar materi tersaring, dan alamat tetap materi VARK lain tetap ditolak
    // walau ID-nya diketahui (bukan hanya penyaringan daftar).
    const list = await request(app)
      .get('/api/v1/topics/' + topic.id + '/materials')
      .set('Authorization', bearer(student.accessToken))
      .expect(404);
    expect(list.body.error.code).toBe('MATERIAL_NOT_AVAILABLE');

    const denied = await getContent(student.accessToken, material.id);
    expect(denied.status).toBe(403);
    expect(denied.body.error.code).toBe('MATERIAL_VARK_FORBIDDEN');
    expect(denied.headers['content-disposition']).toBeUndefined();
    expect(denied.body.toString('utf8')).not.toContain('%PDF');

    // Pemilik materi (dosen pengampu) tetap bisa mengunduh berkas yang sama.
    const allowed = await getContent(lecturer.accessToken, material.id);
    expect(allowed.status).toBe(200);
    expect(Buffer.compare(allowed.body as Buffer, PDF_BYTES)).toBe(0);
  });

  it('AT-03: alamat lama setelah logout lalu login dengan VARK berbeda ditolak untuk isi lama', async () => {
    const lecturer = await loginLecturer();
    const topic = await createTopicApi(lecturer.accessToken, 'Topik AT-03');
    await publishTopicApi(lecturer.accessToken, topic.id);
    const visual = await uploadFileApi(lecturer.accessToken, topic.id, {
      title: 'Materi Visual',
      vark: 'VISUAL',
    });
    const aural = await uploadFileApi(lecturer.accessToken, topic.id, {
      title: 'Materi Aural',
      vark: 'AURAL',
    });

    const firstSession = await loginStudent(STUDENT_LOGIN, STUDENT_PASSWORD, 'VISUAL');
    const visualAddress = visual.contentUrl ?? '';
    expect(visualAddress).toBe('/api/v1/materials/' + visual.id + '/content');
    expect(await (await getContent(firstSession.accessToken, visual.id)).status).toBe(200);

    await request(app)
      .post('/api/v1/auth/logout')
      .set('Authorization', bearer(firstSession.accessToken))
      .expect(204);

    // Sesi pertama mati: alamat yang sama dengan token lama tidak lagi bekerja.
    const revoked = await getContent(firstSession.accessToken, visual.id);
    expect(revoked.status).toBe(401);
    expect(revoked.body.error.code).toBe('SESSION_REVOKED');

    const secondSession = await loginStudent(STUDENT_LOGIN, STUDENT_PASSWORD, 'AURAL');
    expect((await getContent(secondSession.accessToken, visual.id)).status).toBe(403);
    const auralContent = await getContent(secondSession.accessToken, aural.id);
    expect(auralContent.status).toBe(200);
    expect(Buffer.compare(auralContent.body as Buffer, PDF_BYTES)).toBe(0);
  });

  it('AT-18: mahasiswa berhak dan dosen pengampu mengunduh isi dengan header yang benar', async () => {
    const lecturer = await loginLecturer();
    const topic = await createTopicApi(lecturer.accessToken, 'Topik AT-18');
    await publishTopicApi(lecturer.accessToken, topic.id);
    const material = await uploadFileApi(lecturer.accessToken, topic.id, {
      title: 'Modul PDF',
      vark: 'VISUAL',
      filename: 'modul-pdf.pdf',
    });

    const student = await loginStudent();
    const response = await getContent(student.accessToken, material.id);
    expect(response.status).toBe(200);
    expect(response.headers['content-type']).toContain('application/pdf');
    expect(Number(response.headers['content-length'])).toBe(PDF_BYTES.length);
    expect(response.headers['content-disposition']).toContain('attachment');
    expect(response.headers['content-disposition']).toContain('Modul');
    expect(response.headers['cache-control']).toBe('no-store');
    expect(response.headers['vary']).toContain('Authorization');
    expect(response.headers['accept-ranges']).toBe('bytes');
    expect(Buffer.compare(response.body as Buffer, PDF_BYTES)).toBe(0);

    // Dosen pengampu pada kelas aktif juga berhak (tanpa syarat VARK).
    const lecturerResponse = await getContent(lecturer.accessToken, material.id);
    expect(lecturerResponse.status).toBe(200);
    expect(Buffer.compare(lecturerResponse.body as Buffer, PDF_BYTES)).toBe(0);

    // Tanpa token tidak ada isi.
    const anonymous = await request(app)
      .get('/api/v1/materials/' + material.id + '/content')
      .buffer(true)
      .parse(binaryParser);
    expect(anonymous.status).toBe(401);
    expect(anonymous.body.error.code).toBe('UNAUTHENTICATED');

    // Header cache tidak boleh memuat isi materi.
    const list = await request(app)
      .get('/api/v1/topics/' + topic.id + '/materials')
      .set('Authorization', bearer(student.accessToken))
      .expect(200);
    const serialized = JSON.stringify(list.body);
    expect(serialized).not.toContain('signed.example');
    expect(serialized).not.toContain('X-Amz-Signature');
    expect(list.body.data[0].signedUrl).toBeNull();
    expect(list.body.data[0].expiresAt).toBeNull();
    expect(list.body.data[0].contentUrl).toBe('/api/v1/materials/' + material.id + '/content');
  });

  it('AT-19: logout dan sesi kedaluwarsa menolak unduhan pada permintaan berikutnya', async () => {
    const lecturer = await loginLecturer();
    const topic = await createTopicApi(lecturer.accessToken, 'Topik AT-19');
    await publishTopicApi(lecturer.accessToken, topic.id);
    const material = await uploadFileApi(lecturer.accessToken, topic.id, {
      title: 'Materi Sesi',
      vark: 'VISUAL',
    });

    const logoutSession = await loginStudent();
    expect((await getContent(logoutSession.accessToken, material.id)).status).toBe(200);
    await request(app)
      .post('/api/v1/auth/logout')
      .set('Authorization', bearer(logoutSession.accessToken))
      .expect(204);
    const afterLogout = await getContent(logoutSession.accessToken, material.id);
    expect(afterLogout.status).toBe(401);
    expect(afterLogout.body.error.code).toBe('SESSION_REVOKED');

    const expiringSession = await loginStudent();
    expect((await getContent(expiringSession.accessToken, material.id)).status).toBe(200);
    // ck_auth_expiry menuntut expires_at > created_at, jadi kedua kolom
    // digeser ke masa lalu agar sesi benar-benar kedaluwarsa.
    await getPool().query(
      "UPDATE auth_sessions SET created_at = now() - interval '2 hours', expires_at = now() - interval '1 hour' WHERE user_id = $1 AND is_active = true",
      [studentId],
    );
    const expired = await getContent(expiringSession.accessToken, material.id);
    expect(expired.status).toBe(401);
    expect(expired.body.error.code).toBe('SESSION_EXPIRED');
  });

  it('AT-20: mahasiswa LEFT kehilangan akses dan kembali mendapat akses saat ACTIVE lagi', async () => {
    const lecturer = await loginLecturer();
    const topic = await createTopicApi(lecturer.accessToken, 'Topik AT-20');
    await publishTopicApi(lecturer.accessToken, topic.id);
    const material = await uploadFileApi(lecturer.accessToken, topic.id, {
      title: 'Materi Enrollment',
      vark: 'VISUAL',
    });

    const student = await loginStudent();
    expect((await getContent(student.accessToken, material.id)).status).toBe(200);

    await getPool().query(
      "UPDATE enrollments SET status = 'LEFT', left_at = now() WHERE student_id = $1 AND class_id = $2",
      [studentId, classId],
    );
    const left = await getContent(student.accessToken, material.id);
    expect(left.status).toBe(403);
    expect(left.body.error.code).toBe('NOT_ENROLLED');
    expect(Buffer.isBuffer(left.body)).toBe(false);

    await getPool().query(
      "UPDATE enrollments SET status = 'ACTIVE', left_at = NULL WHERE student_id = $1 AND class_id = $2",
      [studentId, classId],
    );
    const restored = await getContent(student.accessToken, material.id);
    expect(restored.status).toBe(200);
    expect(Buffer.compare(restored.body as Buffer, PDF_BYTES)).toBe(0);
  });

  it('AT-21: topik di-unpublish / materi soft-deleted / topik soft-deleted tidak mengirim isi', async () => {
    const lecturer = await loginLecturer();
    const topic = await createTopicApi(lecturer.accessToken, 'Topik AT-21');
    await publishTopicApi(lecturer.accessToken, topic.id);
    const unpublishMaterial = await uploadFileApi(lecturer.accessToken, topic.id, {
      title: 'Materi Unpublish',
      vark: 'VISUAL',
    });

    const student = await loginStudent();
    expect((await getContent(student.accessToken, unpublishMaterial.id)).status).toBe(200);

    await request(app)
      .post('/api/v1/lecturer/topics/' + topic.id + '/unpublish')
      .set('Authorization', bearer(lecturer.accessToken))
      .expect(200);
    const unpublished = await getContent(student.accessToken, unpublishMaterial.id);
    expect(unpublished.status).toBe(404);
    expect(unpublished.body.error.code).toBe('MATERIAL_NOT_FOUND');

    await publishTopicApi(lecturer.accessToken, topic.id);
    const deleted = await request(app)
      .delete('/api/v1/lecturer/materials/' + unpublishMaterial.id)
      .set('Authorization', bearer(lecturer.accessToken))
      .expect(200);
    expect(deleted.body.data.fileDeletion).toBe('COMPLETED');
    const deletedMaterial = await getContent(student.accessToken, unpublishMaterial.id);
    expect(deletedMaterial.status).toBe(404);
    expect(deletedMaterial.body.error.code).toBe('MATERIAL_NOT_FOUND');

    const softDeletedTopicMaterial = await uploadFileApi(lecturer.accessToken, topic.id, {
      title: 'Materi Topik Hapus',
      vark: 'VISUAL',
    });
    const missingMaterial = await uploadFileApi(lecturer.accessToken, topic.id, {
      title: 'Materi Objek Hilang',
      vark: 'VISUAL',
    });
    await getPool().query('UPDATE topics SET deleted_at = now() WHERE id = $1', [topic.id]);
    const deletedTopic = await getContent(student.accessToken, softDeletedTopicMaterial.id);
    expect(deletedTopic.status).toBe(404);
    expect(deletedTopic.body.error.code).toBe('MATERIAL_NOT_FOUND');

    // Objek yang hilang dari storage juga tidak boleh menghasilkan isi.
    const missingKey = await objectKeyOf(missingMaterial.id);
    await unlink(path.join(storageRoot, missingKey));
    await getPool().query('UPDATE topics SET deleted_at = NULL WHERE id = $1', [topic.id]);
    const missing = await getContent(student.accessToken, missingMaterial.id);
    expect(missing.status).toBe(404);
    expect(missing.body.error.code).toBe('MATERIAL_NOT_FOUND');
  });

  it('AT-22: tautan eksternal memeriksa izin terkini dan hanya HTTPS yang dibuka', async () => {
    const lecturer = await loginLecturer();
    const topic = await createTopicApi(lecturer.accessToken, 'Topik AT-22');
    await publishTopicApi(lecturer.accessToken, topic.id);
    const link = await request(app)
      .post('/api/v1/lecturer/topics/' + topic.id + '/materials/link')
      .set('Authorization', bearer(lecturer.accessToken))
      .send({
        title: 'Tautan Eksternal',
        vark: 'VISUAL',
        url: 'https://example.com/materi',
      })
      .expect(201);
    expect(link.body.data.linkTargetUrl).toBe(
      '/api/v1/materials/' + link.body.data.id + '/link-target',
    );
    expect(link.body.data.signedUrl).toBeNull();

    const student = await loginStudent();
    const target = await request(app)
      .get('/api/v1/materials/' + link.body.data.id + '/link-target')
      .set('Authorization', bearer(student.accessToken))
      .expect(200);
    expect(target.body.data.url).toBe('https://example.com/materi');
    expect(target.headers['cache-control']).toBe('no-store');

    // Izin dicabut: tujuan tidak boleh dikembalikan lagi.
    await getPool().query(
      "UPDATE enrollments SET status = 'LEFT', left_at = now() WHERE student_id = $1 AND class_id = $2",
      [studentId, classId],
    );
    const revoked = await request(app)
      .get('/api/v1/materials/' + link.body.data.id + '/link-target')
      .set('Authorization', bearer(student.accessToken))
      .expect(403);
    expect(revoked.body.error.code).toBe('NOT_ENROLLED');

    // Skema database sudah menolak URL non-HTTPS (ck_material_shape), sehingga
    // pertahanan lapis service diuji langsung sebagai unit logika.
    expect(() => assertSecureLinkTarget('http://example.com/tidak-aman')).toThrow();
    expect(() => assertSecureLinkTarget('javascript:alert(1)')).toThrow();
    expect(() => assertSecureLinkTarget('bukan-url')).toThrow();
    expect(() => assertSecureLinkTarget('https://example.com/aman')).not.toThrow();
  });

  it('AT-26: akun wajib ganti password ditolak pada semua permukaan materi', async () => {
    await createUser({
      loginId: '2025000099',
      password: 'StudentPass123',
      role: 'STUDENT',
      mustChangePassword: true,
    });
    const mandatoryId = await getPool().query<{ id: string }>(
      "SELECT id FROM users WHERE login_id = '2025000099'",
    );
    await createEnrollment(classId, mandatoryId.rows[0]?.id ?? '');

    const lecturer = await loginLecturer();
    const topic = await createTopicApi(lecturer.accessToken, 'Topik AT-26');
    await publishTopicApi(lecturer.accessToken, topic.id);
    const material = await uploadFileApi(lecturer.accessToken, topic.id, {
      title: 'Materi Wajib Ganti',
      vark: 'VISUAL',
    });

    const session = await loginAs(app, '2025000099', 'StudentPass123');
    const denied = await getContent(session.accessToken, material.id);
    expect(denied.status).toBe(403);
    expect(denied.body.error.code).toBe('PASSWORD_CHANGE_REQUIRED');

    await request(app)
      .post('/api/v1/auth/change-password')
      .set('Authorization', bearer(session.accessToken))
      .send({
        currentPassword: 'StudentPass123',
        newPassword: 'StudentBaru123',
        confirmPassword: 'StudentBaru123',
      })
      .expect(204);

    const afterChange = await loginStudent('2025000099', 'StudentBaru123', 'VISUAL');
    const allowed = await getContent(afterChange.accessToken, material.id);
    expect(allowed.status).toBe(200);
  });

  it('AT-32: matriks role/objek pada alamat tetap dan alamat tautan', async () => {
    const lecturer = await loginLecturer();
    const topic = await createTopicApi(lecturer.accessToken, 'Topik AT-32');
    await publishTopicApi(lecturer.accessToken, topic.id);
    const material = await uploadFileApi(lecturer.accessToken, topic.id, {
      title: 'Materi Matriks',
      vark: 'VISUAL',
    });
    const linkMaterial = await createMaterialLink({
      topicId: topic.id,
      uploadedBy: lecturerId,
      vark: 'VISUAL',
      title: 'Tautan Matriks',
      url: 'https://example.com/matriks',
    });

    // Mahasiswa belum memilih VARK → 428.
    const noVarkSession = await loginAs(app, STUDENT_LOGIN, STUDENT_PASSWORD);
    const noVark = await getContent(noVarkSession.accessToken, material.id);
    expect(noVark.status).toBe(428);
    expect(noVark.body.error.code).toBe('VARK_REQUIRED');

    // Admin tidak berwenang.
    const admin = await loginAs(app, TEST_ADMIN_LOGIN_ID, TEST_ADMIN_PASSWORD);
    const adminDenied = await getContent(admin.accessToken, material.id);
    expect(adminDenied.status).toBe(403);
    expect(adminDenied.body.error.code).toBe('FORBIDDEN');

    // Dosen lain yang tidak ditugaskan pada kelas materi.
    await createUser({
      loginId: '1990010099',
      password: LECTURER_PASSWORD,
      role: 'LECTURER',
    });
    const otherLecturer = await loginLecturer('1990010099', LECTURER_PASSWORD);
    const unassigned = await getContent(otherLecturer.accessToken, material.id);
    expect(unassigned.status).toBe(404);
    expect(unassigned.body.error.code).toBe('MATERIAL_NOT_FOUND');

    // Mahasiswa yang tidak terdaftar (walau kelas aktif).
    await createUser({
      loginId: '2025000088',
      password: STUDENT_PASSWORD,
      role: 'STUDENT',
    });
    const outsider = await loginStudent('2025000088', STUDENT_PASSWORD, 'VISUAL');
    const notEnrolled = await getContent(outsider.accessToken, material.id);
    expect(notEnrolled.status).toBe(403);
    expect(notEnrolled.body.error.code).toBe('NOT_ENROLLED');

    // Endpoint content hanya untuk berkas; endpoint link-target hanya tautan.
    const wrongKindContent = await getContent(lecturer.accessToken, linkMaterial);
    expect(wrongKindContent.status).toBe(404);
    const wrongKindTarget = await request(app)
      .get('/api/v1/materials/' + material.id + '/link-target')
      .set('Authorization', bearer(lecturer.accessToken))
      .expect(404);
    expect(wrongKindTarget.body.error.code).toBe('MATERIAL_NOT_FOUND');

    // Materi kelas lain (kelas nonaktif) tidak dapat diakses dosen lain.
    const otherClass = await getPool().query<{ id: string }>(
      "INSERT INTO classes (code, name, semester, is_active) VALUES ('IBD-02', 'Kelas Lain', '2026-1', false) RETURNING id",
    );
    const otherClassId = otherClass.rows[0]?.id ?? '';
    const otherClassTopic = await getPool().query<{ id: string }>(
      "INSERT INTO topics (class_id, created_by, title, status) VALUES ($1, $2, 'Topik Kelas Lain', 'PUBLISHED') RETURNING id",
      [otherClassId, lecturerId],
    );
    const otherClassMaterial = await getPool().query<{ id: string }>(
      "INSERT INTO materials (topic_id, uploaded_by, vark_type, title, kind, object_key, mime, size_bytes) VALUES ($1, $2, 'VISUAL', 'Materi Kelas Lain', 'FILE', 'other/kelas-lain.pdf', 'application/pdf', 10) RETURNING id",
      [otherClassTopic.rows[0]?.id ?? '', lecturerId],
    );
    const crossClass = await getContent(lecturer.accessToken, otherClassMaterial.rows[0]?.id ?? '');
    expect(crossClass.status).toBe(404);
  });

  it('Range/If-Range: setiap permintaan tetap diotorisasi penuh dan divalidasi', async () => {
    const lecturer = await loginLecturer();
    const topic = await createTopicApi(lecturer.accessToken, 'Topik Range');
    await publishTopicApi(lecturer.accessToken, topic.id);
    // Validasi unggah mendeteksi MIME dari magic bytes, jadi isi diawali %PDF-.
    const bytes = Buffer.from('%PDF-1.7\nabcdefghij'); // 19 bytes
    const material = await uploadFileApi(lecturer.accessToken, topic.id, {
      title: 'Materi Range',
      vark: 'VISUAL',
      content: bytes,
      filename: 'range.pdf',
    });
    const student = await loginStudent();

    const first = await getContent(student.accessToken, material.id, { Range: 'bytes=0-8' });
    expect(first.status).toBe(206);
    expect(first.headers['content-range']).toBe('bytes 0-8/19');
    expect(Number(first.headers['content-length'])).toBe(9);
    expect((first.body as Buffer).toString()).toBe('%PDF-1.7\n');

    const suffix = await getContent(student.accessToken, material.id, { Range: 'bytes=-5' });
    expect(suffix.status).toBe(206);
    expect(suffix.headers['content-range']).toBe('bytes 14-18/19');
    expect((suffix.body as Buffer).toString()).toBe('fghij');

    const openEnded = await getContent(student.accessToken, material.id, {
      Range: 'bytes=17-',
    });
    expect(openEnded.status).toBe(206);
    expect((openEnded.body as Buffer).toString()).toBe('ij');

    const unsatisfiable = await getContent(student.accessToken, material.id, {
      Range: 'bytes=100-200',
    });
    expect(unsatisfiable.status).toBe(416);
    expect(unsatisfiable.headers['content-range']).toBe('bytes */19');

    const multiRange = await getContent(student.accessToken, material.id, {
      Range: 'bytes=0-1,3-4',
    });
    expect(multiRange.status).toBe(416);

    const full = await getContent(student.accessToken, material.id);
    const etag = full.headers.etag as string;
    expect(etag).toBeTruthy();

    const matchingIfRange = await getContent(student.accessToken, material.id, {
      Range: 'bytes=0-3',
      'If-Range': etag,
    });
    expect(matchingIfRange.status).toBe(206);
    expect((matchingIfRange.body as Buffer).toString()).toBe('%PDF');

    const staleIfRange = await getContent(student.accessToken, material.id, {
      Range: 'bytes=0-3',
      'If-Range': '"etag-objek-lama"',
    });
    expect(staleIfRange.status).toBe(200);
    expect((staleIfRange.body as Buffer).length).toBe(19);

    // Izin dicabut di tengah unduhan berurutan: permintaan Range baru ditolak.
    await getPool().query(
      "UPDATE enrollments SET status = 'LEFT', left_at = now() WHERE student_id = $1 AND class_id = $2",
      [studentId, classId],
    );
    const revokedRange = await getContent(student.accessToken, material.id, {
      Range: 'bytes=0-9',
    });
    expect(revokedRange.status).toBe(403);
    expect(revokedRange.body.error.code).toBe('NOT_ENROLLED');
  });

  it('C1-02: URL lama yang sudah terbit tetap melayani sampai kedaluwarsa lalu berhenti', async () => {
    const lecturer = await loginLecturer();
    const topic = await createTopicApi(lecturer.accessToken, 'Topik C1-02');
    await publishTopicApi(lecturer.accessToken, topic.id);
    const material = await uploadFileApi(lecturer.accessToken, topic.id, {
      title: 'Materi Transisi',
      vark: 'VISUAL',
    });

    // Simulasi tautan yang sudah telanjur terbit sebelum C1-02 (bukan lagi
    // diterbitkan oleh API respons mana pun).
    const objectKey = await objectKeyOf(material.id);
    await putObject(objectKey, PDF_BYTES, 'application/pdf');
    const legacyUrl = new URL(await getSignedObjectUrl(objectKey));
    const legacyPath = legacyUrl.pathname + legacyUrl.search;

    const beforeExpiry = await request(app)
      .get(legacyPath)
      .buffer(true)
      .parse(binaryParser);
    expect(beforeExpiry.status).toBe(200);
    expect(Buffer.compare(beforeExpiry.body as Buffer, PDF_BYTES)).toBe(0);
    expect(beforeExpiry.headers['cache-control']).toBe('no-store');

    // TTL lokal dibuktikan: R2_SIGNED_URL_TTL_SECONDS=1 di berkas tes ini.
    await new Promise((resolve) => setTimeout(resolve, 2200));

    const afterExpiry = await request(app)
      .get(legacyPath)
      .buffer(true)
      .parse(binaryParser);
    expect(afterExpiry.status).toBe(403);
    expect(afterExpiry.body.error.code).toBe('SIGNED_URL_EXPIRED');

    // API baru tidak pernah lagi menerbitkan URL bertanda tangan.
    const lecturerMaterial = await request(app)
      .get('/api/v1/lecturer/topics/' + topic.id + '/materials')
      .set('Authorization', bearer(lecturer.accessToken))
      .expect(200);
    const serialized = JSON.stringify(lecturerMaterial.body);
    expect(serialized).not.toContain('signed.example');
    expect(serialized).not.toContain('signature=');
    expect(lecturerMaterial.body.data[0].signedUrl).toBeNull();
  });
});
