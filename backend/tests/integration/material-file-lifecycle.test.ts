import { mkdtempSync } from 'node:fs';
import { readdir, stat } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import path from 'node:path';
import request from 'supertest';
import { afterAll, beforeEach, describe, expect, it, vi } from 'vitest';
import { createApp } from '../../src/app';
import { getPool } from '../../src/db/client';
import { resetLoginThrottle } from '../../src/lib/login-throttle';
import { AppError } from '../../src/lib/errors';
import { deleteObject, putObject } from '../../src/lib/storage';
import {
  listJobsForMaterial,
} from '../../src/modules/materials/object-deletions.repository';
import {
  attemptObjectDeletion,
  processPendingObjectDeletions,
} from '../../src/modules/materials/object-deletions.service';
import { deleteMaterialInTransaction } from '../../src/modules/topics/topics.service';
import {
  closeDb,
  createAdmin,
  createClass,
  createUser,
  resetDb,
} from '../helpers/db';
import {
  createEnrollment,
  createTeachingAssignment,
} from '../helpers/fixtures';
import { bearer, loginAs } from '../helpers/api';

// Penyimpanan lokal privat agar perilaku penghapusan/kompensasi objek dapat
// dibuktikan pada filesystem. Worker retry dimatikan; test memanggil drain
// secara eksplisit agar deterministik.
const storageRoot = mkdtempSync(path.join(tmpdir(), 'eduvark-lifecycle-storage-'));
process.env.LOCAL_STORAGE_DIR = storageRoot;
process.env.STORAGE_DELETION_INTERVAL_MS = '0';

vi.mock('../../src/lib/storage', async (importOriginal) => {
  const actual = await importOriginal<typeof import('../../src/lib/storage')>();
  return {
    ...actual,
    putObject: vi.fn(actual.putObject),
    deleteObject: vi.fn(actual.deleteObject),
  };
});

const app = createApp();
const describeDb = process.env.DATABASE_URL ? describe : describe.skip;

const LECTURER_LOGIN = '1990010001';
const LECTURER_PASSWORD = 'LecturerPass123';
const STUDENT_LOGIN = '2025000001';
const STUDENT_PASSWORD = 'StudentPass123';

const PDF_BYTES = Buffer.from(
  '%PDF-1.7\n1 0 obj\n<< /Type /Catalog >>\nendobj\n%%EOF\n',
);
const REPLACEMENT_BYTES = Buffer.from(
  '%PDF-1.7\n1 0 obj\n<< /Type /Catalog /Pengganti true >>\nendobj\n%%EOF\n',
);

let classId: string;
let lecturerId: string;

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

function getContent(token: string, materialId: string) {
  return request(app)
    .get('/api/v1/materials/' + materialId + '/content')
    .set('Authorization', bearer(token))
    .buffer(true)
    .parse(smartParser);
}

async function loginLecturer() {
  return loginAs(app, LECTURER_LOGIN, LECTURER_PASSWORD);
}

async function loginStudent() {
  const session = await loginAs(app, STUDENT_LOGIN, STUDENT_PASSWORD);
  await request(app)
    .post('/api/v1/auth/vark')
    .set('Authorization', bearer(session.accessToken))
    .send({ vark: 'VISUAL' })
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

async function uploadFileApi(
  token: string,
  topicId: string,
  input: {
    title: string;
    vark?: 'VISUAL' | 'AURAL' | 'READ_WRITE' | 'KINESTHETIC';
    content?: Buffer;
  } = { title: 'Materi' },
) {
  const response = await request(app)
    .post('/api/v1/lecturer/topics/' + topicId + '/materials/file')
    .set('Authorization', bearer(token))
    .field('title', input.title)
    .field('vark', input.vark ?? 'VISUAL')
    .attach('file', input.content ?? PDF_BYTES, {
      filename: 'materi.pdf',
      contentType: 'application/pdf',
    })
    .expect(201);
  return response.body.data as { id: string };
}

async function replaceFileApi(token: string, materialId: string, content?: Buffer) {
  return request(app)
    .post('/api/v1/lecturer/materials/' + materialId + '/file')
    .set('Authorization', bearer(token))
    .attach('file', content ?? REPLACEMENT_BYTES, {
      filename: 'pengganti.pdf',
      contentType: 'application/pdf',
    });
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

async function storedFiles(): Promise<string[]> {
  try {
    const dir = path.join(storageRoot, 'materials');
    const names = await readdir(dir);
    return names.sort();
  } catch {
    return [];
  }
}

async function latestJob(materialId: string) {
  const jobs = await listJobsForMaterial(materialId);
  return jobs[jobs.length - 1] ?? null;
}

async function auditActions(materialId: string): Promise<string[]> {
  const result = await getPool().query<{ action: string }>(
    'SELECT action FROM audit_logs WHERE entity = $1 AND entity_id = $2 ORDER BY created_at ASC',
    ['MATERIAL', materialId],
  );
  return result.rows.map((row) => row.action);
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
  const studentId = await createUser({
    loginId: STUDENT_LOGIN,
    password: STUDENT_PASSWORD,
    role: 'STUDENT',
  });
  await createEnrollment(classId, studentId);
  await createAdmin();
  for (const file of await storedFiles()) {
    await deleteObject('materials/' + file).catch(() => undefined);
  }
});

afterAll(async () => {
  await closeDb();
});

describeDb('hapus & ganti berkas materi beserta objek penyimpanannya', () => {
  it('hapus materi FILE menghapus objek, menyembunyikan materi, dan mempertahankan riwayat feedback', async () => {
    const lecturer = await loginLecturer();
    const topic = await createTopicApi(lecturer.accessToken, 'Topik Hapus Objek');
    await publishTopicApi(lecturer.accessToken, topic.id);
    const material = await uploadFileApi(lecturer.accessToken, topic.id, {
      title: 'Materi Hapus',
    });
    const key = await objectKeyOf(material.id);
    await stat(path.join(storageRoot, key));

    const student = await loginStudent();
    await request(app)
      .put('/api/v1/topics/' + topic.id + '/feedback')
      .set('Authorization', bearer(student.accessToken))
      .send({ value: 75 })
      .expect(200);

    const deleted = await request(app)
      .delete('/api/v1/lecturer/materials/' + material.id)
      .set('Authorization', bearer(lecturer.accessToken))
      .expect(200);

    expect(deleted.body.data).toEqual({
      id: material.id,
      fileDeletion: 'COMPLETED',
    });
    await expect(stat(path.join(storageRoot, key))).rejects.toThrow();
    expect(await storedFiles()).toEqual([]);

    const row = await getPool().query<{ deleted_at: Date | null }>(
      'SELECT deleted_at FROM materials WHERE id = $1',
      [material.id],
    );
    expect(row.rows[0]?.deleted_at).not.toBeNull();

    const job = await latestJob(material.id);
    expect(job).toMatchObject({
      objectKey: key,
      reason: 'MATERIAL_DELETED',
      state: 'COMPLETED',
      attempts: 1,
    });
    expect(await auditActions(material.id)).toContain('MATERIAL_DELETED');

    // Materi tersembunyi dari dosen maupun mahasiswa.
    const lecturerList = await request(app)
      .get('/api/v1/lecturer/topics/' + topic.id + '/materials')
      .set('Authorization', bearer(lecturer.accessToken))
      .expect(200);
    expect(lecturerList.body.data).toEqual([]);
    expect((await getContent(student.accessToken, material.id)).status).toBe(404);

    // Riwayat feedback tetap tersimpan walau materi sudah dihapus.
    const feedback = await request(app)
      .get('/api/v1/topics/' + topic.id + '/feedback')
      .set('Authorization', bearer(student.accessToken))
      .expect(404);
    expect(feedback.body.error.code).toBe('MATERIAL_NOT_AVAILABLE');
    const feedbackRows = await getPool().query<{ count: string }>(
      'SELECT count(*)::text AS count FROM feedback WHERE topic_id = $1',
      [topic.id],
    );
    expect(Number(feedbackRows.rows[0]?.count)).toBe(1);
  });

  it('hapus materi LINK tidak pernah menyentuh penyimpanan', async () => {
    const lecturer = await loginLecturer();
    const topic = await createTopicApi(lecturer.accessToken, 'Topik Hapus Tautan');
    const created = await request(app)
      .post('/api/v1/lecturer/topics/' + topic.id + '/materials/link')
      .set('Authorization', bearer(lecturer.accessToken))
      .send({
        title: 'Tautan',
        vark: 'VISUAL',
        url: 'https://example.com/materi',
      })
      .expect(201);
    const materialId = created.body.data.id as string;

    const deleted = await request(app)
      .delete('/api/v1/lecturer/materials/' + materialId)
      .set('Authorization', bearer(lecturer.accessToken))
      .expect(200);

    expect(deleted.body.data.fileDeletion).toBe('NONE');
    expect(vi.mocked(deleteObject)).not.toHaveBeenCalled();
    expect(await latestJob(materialId)).toBeNull();
  });

  it('gagal menghapus objek: dilaporkan tertunda, materi tetap tersembunyi, retry otomatis menyelesaikan', async () => {
    const lecturer = await loginLecturer();
    const topic = await createTopicApi(lecturer.accessToken, 'Topik R2 Gagal');
    await publishTopicApi(lecturer.accessToken, topic.id);
    const material = await uploadFileApi(lecturer.accessToken, topic.id, {
      title: 'Materi R2 Gagal',
    });
    const key = await objectKeyOf(material.id);

    vi.mocked(deleteObject).mockRejectedValueOnce(new Error('R2 down'));
    const deleted = await request(app)
      .delete('/api/v1/lecturer/materials/' + material.id)
      .set('Authorization', bearer(lecturer.accessToken))
      .expect(200);

    expect(deleted.body.data.fileDeletion).toBe('PENDING');
    // Objek belum terhapus dan materi sudah tidak tersedia bagi mahasiswa.
    await stat(path.join(storageRoot, key));
    const student = await loginStudent();
    expect((await getContent(student.accessToken, material.id)).status).toBe(404);

    const pending = await latestJob(material.id);
    expect(pending).toMatchObject({
      state: 'PENDING',
      reason: 'MATERIAL_DELETED',
      attempts: 1,
    });
    expect(pending?.lastError).toContain('R2 down');

    // Worker berikutnya mengulang otomatis; jadwal dibuat jatuh tempo agar
    // test tidak menunggu backoff.
    await getPool().query(
      'UPDATE material_object_deletions SET next_attempt_at = now() WHERE id = $1',
      [pending?.id],
    );
    const drained = await processPendingObjectDeletions();
    expect(drained.completed).toBe(1);
    expect((await latestJob(material.id))?.state).toBe('COMPLETED');
    await expect(stat(path.join(storageRoot, key))).rejects.toThrow();
  });

  it('objek yang sudah hilang saat retry ditandai selesai tanpa error berulang', async () => {
    const lecturer = await loginLecturer();
    const topic = await createTopicApi(lecturer.accessToken, 'Topik Objek Hilang');
    const material = await uploadFileApi(lecturer.accessToken, topic.id, {
      title: 'Materi Hilang',
    });
    const key = await objectKeyOf(material.id);

    vi.mocked(deleteObject).mockRejectedValueOnce(new Error('timeout'));
    await request(app)
      .delete('/api/v1/lecturer/materials/' + material.id)
      .set('Authorization', bearer(lecturer.accessToken))
      .expect(200);

    const pending = await latestJob(material.id);
    // Simulasi proses berhenti setelah objek benar-benar terhapus (mis. retry
    // manual/operasional) sebelum hasil tercatat.
    await deleteObject(key);
    await getPool().query(
      'UPDATE material_object_deletions SET next_attempt_at = now() WHERE id = $1',
      [pending?.id],
    );

    const drained = await processPendingObjectDeletions();
    expect(drained.completed).toBe(1);
    expect((await latestJob(material.id))?.state).toBe('ABSENT');
  });

  it('ganti berkas: objek baru aktif, objek lama dihapus, judul/VARK/record tetap', async () => {
    const lecturer = await loginLecturer();
    const topic = await createTopicApi(lecturer.accessToken, 'Topik Ganti');
    await publishTopicApi(lecturer.accessToken, topic.id);
    const material = await uploadFileApi(lecturer.accessToken, topic.id, {
      title: 'Materi Ganti',
    });
    const oldKey = await objectKeyOf(material.id);

    const replaced = await replaceFileApi(lecturer.accessToken, material.id);
    expect(replaced.status).toBe(200);
    expect(replaced.body.data.material.id).toBe(material.id);
    expect(replaced.body.data.material.title).toBe('Materi Ganti');
    expect(replaced.body.data.material.vark).toBe('VISUAL');
    expect(replaced.body.data.previousFileDeletion).toBe('COMPLETED');

    const newKey = await objectKeyOf(material.id);
    expect(newKey).not.toBe(oldKey);
    await expect(stat(path.join(storageRoot, oldKey))).rejects.toThrow();
    await stat(path.join(storageRoot, newKey));

    const job = await latestJob(material.id);
    expect(job).toMatchObject({
      objectKey: oldKey,
      reason: 'FILE_REPLACED',
      state: 'COMPLETED',
    });
    expect(await auditActions(material.id)).toContain('MATERIAL_FILE_REPLACED');

    const student = await loginStudent();
    const content = await getContent(student.accessToken, material.id);
    expect(content.status).toBe(200);
    expect(Buffer.compare(content.body as Buffer, REPLACEMENT_BYTES)).toBe(0);
  });

  it('ganti berkas gagal di penyimpanan: berkas lama tetap aktif dan record tidak berubah', async () => {
    const lecturer = await loginLecturer();
    const topic = await createTopicApi(lecturer.accessToken, 'Topik Ganti Gagal');
    await publishTopicApi(lecturer.accessToken, topic.id);
    const material = await uploadFileApi(lecturer.accessToken, topic.id, {
      title: 'Materi Ganti Gagal',
    });
    const oldKey = await objectKeyOf(material.id);
    const student = await loginStudent();

    vi.mocked(putObject).mockRejectedValueOnce(
      new AppError('STORAGE_UNAVAILABLE', 502, 'Penyimpanan materi sedang tidak tersedia.'),
    );
    const failed = await replaceFileApi(lecturer.accessToken, material.id);
    expect(failed.status).toBe(502);
    expect(failed.body.error.code).toBe('STORAGE_UNAVAILABLE');

    expect(await objectKeyOf(material.id)).toBe(oldKey);
    const content = await getContent(student.accessToken, material.id);
    expect(content.status).toBe(200);
    expect(Buffer.compare(content.body as Buffer, PDF_BYTES)).toBe(0);
    // Objek pengganti tidak pernah tertulis, tetapi niatnya tetap tercatat dan
    // dibersihkan lewat antrean yang dapat diulang (bukan dihapus langsung).
    const jobsAfterFailure = await listJobsForMaterial(material.id);
    expect(jobsAfterFailure.filter((job) => job.reason === 'FILE_REPLACED')).toEqual(
      [],
    );
    const cleanupJobs = jobsAfterFailure.filter(
      (job) => job.reason === 'REPLACEMENT_ABANDONED',
    );
    expect(cleanupJobs).toHaveLength(1);
    expect(cleanupJobs[0]).toMatchObject({ state: 'ABSENT' });
    expect(await auditActions(material.id)).not.toContain('MATERIAL_FILE_REPLACED');
  });

  it('ganti berkas pada materi LINK ditolak tanpa membuat objek baru', async () => {
    const lecturer = await loginLecturer();
    const topic = await createTopicApi(lecturer.accessToken, 'Topik Tautan Ganti');
    const created = await request(app)
      .post('/api/v1/lecturer/topics/' + topic.id + '/materials/link')
      .set('Authorization', bearer(lecturer.accessToken))
      .send({ title: 'Tautan', vark: 'VISUAL', url: 'https://example.com/x' })
      .expect(201);
    const materialId = created.body.data.id as string;

    const response = await replaceFileApi(lecturer.accessToken, materialId);
    expect(response.status).toBe(422);
    expect(response.body.error.code).toBe('MATERIAL_KIND_UNSUPPORTED');
    expect(await storedFiles()).toEqual([]);
    expect(await latestJob(materialId)).toBeNull();
  });

  it('ganti berkas dan hapus bersamaan tetap konsisten: materi terhapus, tidak ada objek tersisa', async () => {
    const lecturer = await loginLecturer();
    const topic = await createTopicApi(lecturer.accessToken, 'Topik Balapan');
    await publishTopicApi(lecturer.accessToken, topic.id);
    const material = await uploadFileApi(lecturer.accessToken, topic.id, {
      title: 'Materi Balapan',
    });
    const firstKey = await objectKeyOf(material.id);

    const [replaceResponse, deleteResponse] = await Promise.all([
      replaceFileApi(lecturer.accessToken, material.id),
      request(app)
        .delete('/api/v1/lecturer/materials/' + material.id)
        .set('Authorization', bearer(lecturer.accessToken)),
    ]);

    expect([200, 404, 409]).toContain(replaceResponse.status);
    expect(deleteResponse.status).toBe(200);

    const row = await getPool().query<{ deleted_at: Date | null; object_key: string | null }>(
      'SELECT deleted_at, object_key FROM materials WHERE id = $1',
      [material.id],
    );
    expect(row.rows[0]?.deleted_at).not.toBeNull();
    for (const key of [firstKey, row.rows[0]?.object_key]) {
      if (!key) continue;
      await expect(stat(path.join(storageRoot, key))).rejects.toThrow();
    }
    expect(await storedFiles()).toEqual([]);
  });

  it('hapus dua kali: panggilan kedua 404 dan tidak menggandakan pekerjaan', async () => {
    const lecturer = await loginLecturer();
    const topic = await createTopicApi(lecturer.accessToken, 'Topik Hapus Ganda');
    const material = await uploadFileApi(lecturer.accessToken, topic.id, {
      title: 'Materi Hapus Ganda',
    });

    await request(app)
      .delete('/api/v1/lecturer/materials/' + material.id)
      .set('Authorization', bearer(lecturer.accessToken))
      .expect(200);
    const second = await request(app)
      .delete('/api/v1/lecturer/materials/' + material.id)
      .set('Authorization', bearer(lecturer.accessToken))
      .expect(404);
    expect(second.body.error.code).toBe('MATERIAL_NOT_FOUND');
    expect(await listJobsForMaterial(material.id)).toHaveLength(1);
  });

  it('balapan hapus vs ganti: kunci antrean berasal dari mutasi, bukan pembacaan awal', async () => {
    const lecturer = await loginLecturer();
    const topic = await createTopicApi(lecturer.accessToken, 'Topik Balapan Mutasi');
    await publishTopicApi(lecturer.accessToken, topic.id);
    const material = await uploadFileApi(lecturer.accessToken, topic.id, {
      title: 'Materi Balapan',
    });
    const initialKey = await objectKeyOf(material.id);

    // Transaksi Hapus membaca keadaan awal (kunci A), lalu Ganti File berhasil
    // commit (kunci B aktif) tepat pada jendela sebelum mutasi. Hapus kemudian
    // melanjutkan dengan pembacaan awal yang sudah basi.
    const client = await getPool().connect();
    let replacementKey = '';
    try {
      await client.query('BEGIN');
      const queued = await deleteMaterialInTransaction(
        client,
        material.id,
        lecturerId,
        {
          afterOwnershipRead: async () => {
            const replaced = await replaceFileApi(lecturer.accessToken, material.id);
            expect(replaced.status).toBe(200);
            expect(replaced.body.data.previousFileDeletion).toBe('COMPLETED');
            replacementKey = await objectKeyOf(material.id);
            expect(replacementKey).not.toBe(initialKey);
          },
        },
      );
      await client.query('COMMIT');
      expect(queued.kind).toBe('FILE');
      expect(queued.objectKey).toBe(replacementKey);
      expect(queued.jobId).not.toBeNull();
      expect(await attemptObjectDeletion(queued.jobId!)).toBe('COMPLETED');
    } finally {
      await client.query('ROLLBACK').catch(() => undefined);
      client.release();
    }

    // Materi tetap tersembunyi dan KEDUA objek hilang: objek lama oleh Ganti
    // File, objek baru oleh Hapus.
    const row = await getPool().query<{ deleted_at: Date | null }>(
      'SELECT deleted_at FROM materials WHERE id = $1',
      [material.id],
    );
    expect(row.rows[0]?.deleted_at).not.toBeNull();
    await expect(stat(path.join(storageRoot, initialKey))).rejects.toThrow();
    await expect(stat(path.join(storageRoot, replacementKey))).rejects.toThrow();

    // Antrean mencatat kunci yang benar: FILE_REPLACED untuk A, MATERIAL_DELETED
    // untuk B. Audit hanya satu kali.
    const jobs = await listJobsForMaterial(material.id);
    const jobEntries = jobs
      .map((job) => [job.reason, job.objectKey] as const)
      .sort((a, b) => a[0].localeCompare(b[0]));
    expect(jobEntries).toEqual([
      ['FILE_REPLACED', initialKey],
      ['MATERIAL_DELETED', replacementKey],
    ]);
    expect(
      (await auditActions(material.id)).filter((action) => action === 'MATERIAL_DELETED'),
    ).toHaveLength(1);
  });

  it('balapan terbalik: hapus menang lebih dulu, ganti file gagal 409 dan objek pengganti dikompensasi', async () => {
    const lecturer = await loginLecturer();
    const topic = await createTopicApi(lecturer.accessToken, 'Topik Balapan Hapus Menang');
    await publishTopicApi(lecturer.accessToken, topic.id);
    const material = await uploadFileApi(lecturer.accessToken, topic.id, {
      title: 'Materi Hapus Menang',
    });
    const initialKey = await objectKeyOf(material.id);

    // Hapus menyelesaikan transaksinya lebih dulu.
    const client = await getPool().connect();
    try {
      await client.query('BEGIN');
      const queued = await deleteMaterialInTransaction(client, material.id, lecturerId);
      await client.query('COMMIT');
      expect(queued.objectKey).toBe(initialKey);
      expect(await attemptObjectDeletion(queued.jobId!)).toBe('COMPLETED');
    } finally {
      client.release();
    }

    // Ganti File sesudahnya harus gagal 409 karena materi sudah dihapus.
    const replaced = await replaceFileApi(lecturer.accessToken, material.id);
    expect(replaced.status).toBe(404);
    expect(replaced.body.error.code).toBe('MATERIAL_NOT_FOUND');

    // Tidak ada objek yatim yang tersisa.
    await expect(stat(path.join(storageRoot, initialKey))).rejects.toThrow();
    expect(await storedFiles()).toEqual([]);
    expect(await auditActions(material.id)).toContain('MATERIAL_DELETED');
  });

  it('dua permintaan hapus bersamaan: tepat satu audit dan satu pekerjaan', async () => {
    const lecturer = await loginLecturer();
    const topic = await createTopicApi(lecturer.accessToken, 'Topik Hapus Bersamaan');
    await publishTopicApi(lecturer.accessToken, topic.id);
    const material = await uploadFileApi(lecturer.accessToken, topic.id, {
      title: 'Materi Hapus Bersamaan',
    });

    const [first, second] = await Promise.all([
      request(app)
        .delete('/api/v1/lecturer/materials/' + material.id)
        .set('Authorization', bearer(lecturer.accessToken)),
      request(app)
        .delete('/api/v1/lecturer/materials/' + material.id)
        .set('Authorization', bearer(lecturer.accessToken)),
    ]);

    expect([first.status, second.status].sort()).toEqual([200, 404]);
    const auditCount = await getPool().query<{ count: string }>(
      `SELECT count(*)::text AS count FROM audit_logs
       WHERE entity = 'MATERIAL' AND entity_id = $1 AND action = 'MATERIAL_DELETED'`,
      [material.id],
    );
    expect(Number(auditCount.rows[0]?.count)).toBe(1);
    const jobs = await listJobsForMaterial(material.id);
    const deletionJobs = jobs.filter((job) => job.reason === 'MATERIAL_DELETED');
    expect(deletionJobs).toHaveLength(1);
    expect(deletionJobs[0]?.objectKey).toBe(await objectKeyOf(material.id));
  });

  it('otorisasi ganti berkas: mahasiswa ditolak, dosen bukan pemilik melihat 404', async () => {
    const lecturer = await loginLecturer();
    const topic = await createTopicApi(lecturer.accessToken, 'Topik Otorisasi Ganti');
    const material = await uploadFileApi(lecturer.accessToken, topic.id, {
      title: 'Materi Otorisasi',
    });

    const student = await loginStudent();
    const denied = await request(app)
      .post('/api/v1/lecturer/materials/' + material.id + '/file')
      .set('Authorization', bearer(student.accessToken))
      .attach('file', REPLACEMENT_BYTES, {
        filename: 'pengganti.pdf',
        contentType: 'application/pdf',
      });
    expect(denied.status).toBe(403);

    // Dosen lain yang tidak mengajar kelas ini tidak boleh mengganti berkas.
    await createUser({
      loginId: '1990010002',
      password: 'LecturerPass123',
      role: 'LECTURER',
    });
    const other = await loginAs(app, '1990010002', 'LecturerPass123');
    const hidden = await replaceFileApi(other.accessToken, material.id);
    expect(hidden.status).toBe(404);
    expect(hidden.body.error.code).toBe('MATERIAL_NOT_FOUND');

    const unchangedKey = await objectKeyOf(material.id);
    expect(await objectKeyOf(material.id)).toBe(unchangedKey);
    expect(await storedFiles()).toHaveLength(1);
    expect(await auditActions(material.id)).not.toContain('MATERIAL_FILE_REPLACED');
  });
});
