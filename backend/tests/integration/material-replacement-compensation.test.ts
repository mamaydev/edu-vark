import { randomUUID } from 'node:crypto';
import { mkdtempSync } from 'node:fs';
import { readdir, stat } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import path from 'node:path';
import request from 'supertest';
import { afterAll, beforeEach, describe, expect, it, vi } from 'vitest';
import { createApp } from '../../src/app';
import { getPool } from '../../src/db/client';
import { withTransaction } from '../../src/db/transaction';
import { resetLoginThrottle } from '../../src/lib/login-throttle';
import { deleteObject, objectStat, putObject } from '../../src/lib/storage';
import {
  claimExpiredReplacement,
  findReplacement,
  markReplacementActive,
} from '../../src/modules/materials/file-replacements.repository';
import {
  beginFileReplacement,
  reconcileFileReplacements,
} from '../../src/modules/materials/file-replacements.service';
import {
  listJobsForMaterial,
} from '../../src/modules/materials/object-deletions.repository';
import {
  attemptObjectDeletion,
  processPendingObjectDeletions,
} from '../../src/modules/materials/object-deletions.service';
import { replaceMaterialFile } from '../../src/modules/topics/topics.service';
import {
  closeDb,
  createAdmin,
  createClass,
  createUser,
  resetDb,
} from '../helpers/db';
import { createTeachingAssignment } from '../helpers/fixtures';
import { bearer, loginAs } from '../helpers/api';

// Penyimpanan lokal privat + worker dimatikan agar test deterministik.
const storageRoot = mkdtempSync(path.join(tmpdir(), 'eduvark-replacement-storage-'));
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

const PDF_A = Buffer.from(
  '%PDF-1.7\n1 0 obj\n<< /Type /Catalog /Berkas (A) >>\nendobj\n%%EOF\n',
);
const PDF_B = Buffer.from(
  '%PDF-1.7\n1 0 obj\n<< /Type /Catalog /Berkas (B) >>\nendobj\n%%EOF\n',
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

async function uploadFileApi(token: string, topicId: string, title: string) {
  const response = await request(app)
    .post('/api/v1/lecturer/topics/' + topicId + '/materials/file')
    .set('Authorization', bearer(token))
    .field('title', title)
    .field('vark', 'VISUAL')
    .attach('file', PDF_A, {
      filename: 'awal.pdf',
      contentType: 'application/pdf',
    })
    .expect(201);
  return response.body.data as { id: string };
}

async function replaceFileApi(token: string, materialId: string, content = PDF_B) {
  return request(app)
    .post('/api/v1/lecturer/materials/' + materialId + '/file')
    .set('Authorization', bearer(token))
    .attach('file', content, {
      filename: 'pengganti.pdf',
      contentType: 'application/pdf',
    });
}

async function materialRow(materialId: string) {
  const result = await getPool().query<{
    object_key: string;
    title: string;
    mime: string;
    size_bytes: string;
    deleted_at: Date | null;
  }>(
    'SELECT object_key, title, mime, size_bytes, deleted_at FROM materials WHERE id = $1',
    [materialId],
  );
  return result.rows[0]!;
}

async function storedFiles(): Promise<string[]> {
  try {
    return (await readdir(path.join(storageRoot, 'materials'))).sort();
  } catch {
    return [];
  }
}

async function jobsFor(materialId: string) {
  return listJobsForMaterial(materialId);
}

function replaceInput(content = PDF_B) {
  return {
    buffer: content,
    mimetype: 'application/pdf',
    size: content.length,
    originalname: 'pengganti.pdf',
  };
}

async function expireReplacementLease(replacementId: string) {
  await getPool().query(
    "UPDATE material_file_replacements SET lease_expires_at = now() - interval '1 second' WHERE id = $1",
    [replacementId],
  );
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
  await createAdmin();
  for (const file of await storedFiles()) {
    await deleteObject('materials/' + file).catch(() => undefined);
  }
  vi.mocked(deleteObject).mockClear();
  vi.mocked(putObject).mockClear();
});

afterAll(async () => {
  await closeDb();
});

describeDb('kompensasi Ganti File tahan restart', () => {
  it('COMMIT berhasil lalu konfirmasi hilang: record menunjuk objek baru dan objek baru tetap dapat diunduh', async () => {
    const lecturer = await loginLecturer();
    const topic = await createTopicApi(lecturer.accessToken, 'Topik Commit Hilang');
    await publishTopicApi(lecturer.accessToken, topic.id);
    const material = await uploadFileApi(lecturer.accessToken, topic.id, 'Materi A');
    const oldKey = (await materialRow(material.id)).object_key;

    const result = await replaceMaterialFile(
      material.id,
      lecturerId,
      replaceInput(PDF_B),
      {
        afterMetadataCommit: () => {
          throw new Error('koneksi terputus setelah COMMIT');
        },
      },
    );

    // Pemulihan memakai state database, bukan respons yang hilang.
    const row = await materialRow(material.id);
    expect(row.object_key).not.toBe(oldKey);
    expect(row.title).toBe('Materi A');
    expect(result.material.id).toBe(material.id);
    expect(result.material.title).toBe('Materi A');
    expect(result.material.mime).toBe('application/pdf');
    expect(result.previousFileDeletion).toBe('COMPLETED');
    expect(await objectStat(oldKey)).toBeNull();
    expect(await stat(path.join(storageRoot, row.object_key))).toBeTruthy();

    // Objek baru tidak boleh dihapus oleh jalur kompensasi.
    expect(vi.mocked(deleteObject)).not.toHaveBeenCalledWith(row.object_key);

    const intent = await findReplacement(
      (
        await getPool().query<{ id: string }>(
          'SELECT id FROM material_file_replacements WHERE material_id = $1',
          [material.id],
        )
      ).rows[0]!.id,
    );
    expect(intent?.state).toBe('ACTIVE');
    expect(intent?.newObjectKey).toBe(row.object_key);

    const oldJob = (await jobsFor(material.id)).find(
      (job) => job.objectKey === oldKey,
    );
    expect(oldJob).toMatchObject({ reason: 'FILE_REPLACED', state: 'COMPLETED' });

    // Unduh melalui alur mahasiswa/dosen: isinya berkas pengganti.
    const content = await getContent(lecturer.accessToken, material.id);
    expect(content.status).toBe(200);
    expect(Buffer.isBuffer(content.body)).toBe(true);
    expect((content.body as Buffer).equals(PDF_B)).toBe(true);

    const audit = await getPool().query<{ action: string }>(
      "SELECT action FROM audit_logs WHERE entity = 'MATERIAL' AND entity_id = $1",
      [material.id],
    );
    expect(audit.rows.map((row) => row.action)).toContain('MATERIAL_FILE_REPLACED');
  });

  it('transaksi metadata rollback: berkas lama tetap aktif dan objek pengganti dibersihkan/tercatat', async () => {
    const lecturer = await loginLecturer();
    const topic = await createTopicApi(lecturer.accessToken, 'Topik Rollback');
    await publishTopicApi(lecturer.accessToken, topic.id);
    const material = await uploadFileApi(lecturer.accessToken, topic.id, 'Materi Rollback');
    const oldKey = (await materialRow(material.id)).object_key;

    await expect(
      replaceMaterialFile(material.id, lecturerId, replaceInput(PDF_B), {
        afterMetadataUpdate: () => {
          throw new Error('rollback metadata');
        },
      }),
    ).rejects.toThrow('rollback metadata');

    // Record tidak berubah dan berkas lama tetap tersaji.
    expect((await materialRow(material.id)).object_key).toBe(oldKey);
    expect(await objectStat(oldKey)).not.toBeNull();
    const content = await getContent(lecturer.accessToken, material.id);
    expect(content.status).toBe(200);
    expect((content.body as Buffer).equals(PDF_A)).toBe(true);

    // Objek pengganti dibersihkan atau tercatat dalam antrean yang dapat diulang.
    const intentRows = await getPool().query<{
      id: string;
      state: string;
      new_object_key: string;
      cleanup_job_id: string | null;
    }>(
      'SELECT id, state, new_object_key, cleanup_job_id FROM material_file_replacements WHERE material_id = $1',
      [material.id],
    );
    expect(intentRows.rows[0]?.state).toBe('ABANDONED');
    const cleanupJobId = intentRows.rows[0]?.cleanup_job_id;
    expect(cleanupJobId).toBeTruthy();
    expect(await objectStat(intentRows.rows[0]!.new_object_key)).toBeNull();
    const cleanupJob = (await jobsFor(material.id)).find(
      (job) => job.id === cleanupJobId,
    );
    expect(cleanupJob).toMatchObject({
      reason: 'REPLACEMENT_ABANDONED',
      state: 'COMPLETED',
    });

    // Tidak ada objek yatim yang tersisa selain berkas lama.
    expect(await storedFiles()).toEqual([oldKey.replace(/^materials\//, '')]);

    // Ganti berikutnya berhasil normal.
    const replaced = await replaceFileApi(lecturer.accessToken, material.id);
    expect(replaced.status).toBe(200);
    expect(replaced.body.data.material.id).toBe(material.id);
    const afterContent = await getContent(lecturer.accessToken, material.id);
    expect((afterContent.body as Buffer).equals(PDF_B)).toBe(true);
  });

  it('unggahan tertulis tetapi proses berhenti sebelum metadata: rekonsiliasi restart membersihkan objek pengganti', async () => {
    const lecturer = await loginLecturer();
    const topic = await createTopicApi(lecturer.accessToken, 'Topik Restart');
    await publishTopicApi(lecturer.accessToken, topic.id);
    const material = await uploadFileApi(lecturer.accessToken, topic.id, 'Materi Restart');
    const oldKey = (await materialRow(material.id)).object_key;

    // Simulasi proses berhenti setelah putObject: niat PENDING tercatat,
    // objek pengganti sudah ada, metadata belum berubah.
    const intent = await beginFileReplacement({
      materialId: material.id,
      requestedBy: lecturerId,
      oldObjectKey: oldKey,
      newObjectKey: 'materials/' + '11111111-1111-4111-8111-111111111111.pdf',
    });
    await putObject(intent.newObjectKey, PDF_B, 'application/pdf');
    await expireReplacementLease(intent.id);

    const reconciled = await reconcileFileReplacements();
    expect(reconciled.processed).toBe(1);
    expect(reconciled.abandoned).toBe(1);

    const after = await materialRow(material.id);
    expect(after.object_key).toBe(oldKey);
    expect((await getContent(lecturer.accessToken, material.id)).body).toEqual(
      expect.any(Buffer),
    );
    expect(await objectStat(intent.newObjectKey)).toBeNull();
    expect(await objectStat(oldKey)).not.toBeNull();
    expect((await findReplacement(intent.id))?.state).toBe('ABANDONED');

    // Rekonsiliasi ulang aman walau objek sudah hilang.
    const again = await reconcileFileReplacements();
    expect(again.processed).toBe(0);
  });

  it('rekonsiliasi tidak menyentuh unggahan yang masih berjalan dan tidak pernah menghapus objek aktif', async () => {
    const lecturer = await loginLecturer();
    const topic = await createTopicApi(lecturer.accessToken, 'Topik In-Flight');
    const material = await uploadFileApi(lecturer.accessToken, topic.id, 'Materi In-Flight');
    const oldKey = (await materialRow(material.id)).object_key;

    // Lease masih aktif => bukan wewenang rekonsiliasi.
    const inflight = await beginFileReplacement({
      materialId: material.id,
      requestedBy: lecturerId,
      oldObjectKey: oldKey,
      newObjectKey: 'materials/' + '22222222-2222-4222-8222-222222222222.pdf',
    });
    await putObject(inflight.newObjectKey, PDF_B, 'application/pdf');
    const none = await reconcileFileReplacements();
    expect(none.processed).toBe(0);
    expect((await findReplacement(inflight.id))?.state).toBe('PENDING');
    expect(await objectStat(inflight.newObjectKey)).not.toBeNull();

    // Klaim kedaluwarsa: klaim lama tidak boleh menimpa hasil klaim baru.
    await expireReplacementLease(inflight.id);
    const claimA = await claimExpiredReplacement(inflight.id, randomUUID(), 60_000);
    expect(claimA).not.toBeNull();
    await getPool().query(
      "UPDATE material_file_replacements SET lease_expires_at = now() - interval '1 second' WHERE id = $1",
      [inflight.id],
    );
    const claimB = await claimExpiredReplacement(inflight.id, randomUUID(), 60_000);
    expect(claimB).not.toBeNull();
    expect(claimA?.leaseToken).not.toBe(claimB?.leaseToken);

    const oldTokenRecorded = await withTransaction((client) =>
      markReplacementActive(
        client,
        inflight.id,
        claimA!.leaseToken!,
        '11111111-1111-4111-8111-111111111111',
      ),
    );
    expect(oldTokenRecorded).toBe(false);
    expect((await findReplacement(inflight.id))?.state).toBe('PENDING');

    // Database menunjuk objek pengganti (commit tersimpan): rekonsiliasi
    // melindungi objek, tidak mencantumkannya ke antrean hapus.
    await getPool().query(
      'UPDATE materials SET object_key = $2 WHERE id = $1',
      [material.id, inflight.newObjectKey],
    );
    await expireReplacementLease(inflight.id);
    const protectedRun = await reconcileFileReplacements();
    expect(protectedRun.active).toBe(1);
    expect((await findReplacement(inflight.id))?.state).toBe('ACTIVE');
    expect(await objectStat(inflight.newObjectKey)).not.toBeNull();
    expect(
      (await jobsFor(material.id)).filter(
        (job) =>
          job.objectKey === inflight.newObjectKey &&
          job.reason === 'REPLACEMENT_ABANDONED',
      ),
    ).toEqual([]);

    // Pekerjaan hapus objek lama tercatat dan selesai.
    const oldJob = (await jobsFor(material.id)).find(
      (job) => job.objectKey === oldKey && job.reason === 'FILE_REPLACED',
    );
    expect(oldJob?.state).toBe('COMPLETED');
  });

  it('kegagalan kompensasi sementara: pekerjaan tertunda, rerun aman, objek pengganti akhirnya hilang', async () => {
    const lecturer = await loginLecturer();
    const topic = await createTopicApi(lecturer.accessToken, 'Topik Retry Kompensasi');
    await publishTopicApi(lecturer.accessToken, topic.id);
    const material = await uploadFileApi(lecturer.accessToken, topic.id, 'Materi Retry');
    const oldKey = (await materialRow(material.id)).object_key;

    vi.mocked(deleteObject).mockRejectedValueOnce(new Error('R2 sementara gagal'));
    await expect(
      replaceMaterialFile(material.id, lecturerId, replaceInput(PDF_B), {
        afterMetadataUpdate: () => {
          throw new Error('rollback metadata');
        },
      }),
    ).rejects.toThrow('rollback metadata');

    expect((await materialRow(material.id)).object_key).toBe(oldKey);
    const intentRows = await getPool().query<{
      new_object_key: string;
      cleanup_job_id: string;
    }>(
      'SELECT new_object_key, cleanup_job_id FROM material_file_replacements WHERE material_id = $1',
      [material.id],
    );
    const newKey = intentRows.rows[0]!.new_object_key;
    const cleanupJob = (await jobsFor(material.id)).find(
      (job) => job.id === intentRows.rows[0]!.cleanup_job_id,
    );
    expect(cleanupJob).toMatchObject({
      reason: 'REPLACEMENT_ABANDONED',
      state: 'PENDING',
    });
    expect(await objectStat(newKey)).not.toBeNull();

    // Worker retry berikutnya menyelesaikan tanpa menghapus berkas lama.
    await getPool().query(
      'UPDATE material_object_deletions SET next_attempt_at = now() WHERE id = $1',
      [cleanupJob?.id],
    );
    const drained = await processPendingObjectDeletions();
    expect(drained.completed).toBe(1);
    expect(await objectStat(newKey)).toBeNull();
    expect(await objectStat(oldKey)).not.toBeNull();
    expect((await jobsFor(material.id)).find((job) => job.id === cleanupJob?.id)?.state).toBe(
      'COMPLETED',
    );

    // Rerun/duplikasi aman: tidak ada objek yang dihapus lagi.
    const rerun = await processPendingObjectDeletions();
    expect(rerun.processed).toBe(0);
    const retry = await attemptObjectDeletion(cleanupJob!.id);
    expect(retry).toBe('COMPLETED');
  });

  it('Ganti File bersamaan dengan Hapus: objek berpotensi aktif tidak pernah dihapus', async () => {
    const lecturer = await loginLecturer();
    const topic = await createTopicApi(lecturer.accessToken, 'Topik Balapan Hapus');
    await publishTopicApi(lecturer.accessToken, topic.id);
    const material = await uploadFileApi(lecturer.accessToken, topic.id, 'Materi Balapan');
    const oldKey = (await materialRow(material.id)).object_key;

    let deleted = false;
    await expect(
      replaceMaterialFile(material.id, lecturerId, replaceInput(PDF_B), {
        afterUpload: async () => {
          // Hapus menang sebelum transaksi metadata Ganti File berjalan.
          const response = await request(app)
            .delete('/api/v1/lecturer/materials/' + material.id)
            .set('Authorization', bearer(lecturer.accessToken));
          expect(response.status).toBe(200);
          deleted = true;
        },
      }),
    ).rejects.toThrow();

    expect(deleted).toBe(true);
    expect((await materialRow(material.id)).deleted_at).not.toBeNull();

    // Objek lama dihapus oleh jalur Hapus; objek pengganti tidak menjadi aktif
    // dan dibersihkan, bukan dihapus sebagai objek yang sedang dipakai.
    expect(await objectStat(oldKey)).toBeNull();
    const intentRows = await getPool().query<{
      new_object_key: string;
      state: string;
      cleanup_job_id: string | null;
    }>(
      'SELECT new_object_key, state, cleanup_job_id FROM material_file_replacements WHERE material_id = $1',
      [material.id],
    );
    expect(intentRows.rows[0]?.state).toBe('ABANDONED');
    expect(await objectStat(intentRows.rows[0]!.new_object_key)).toBeNull();
    expect(intentRows.rows[0]!.cleanup_job_id).toBeTruthy();

    // Materi tetap tersembunyi.
    const content = await getContent(lecturer.accessToken, material.id);
    expect(content.status).toBe(404);
  });
});
