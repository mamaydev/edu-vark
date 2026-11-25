import { randomUUID } from 'node:crypto';
import { mkdtempSync } from 'node:fs';
import { stat } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import path from 'node:path';
import { afterAll, beforeEach, describe, expect, it, vi } from 'vitest';
import { getPool } from '../../src/db/client';
import { AppError } from '../../src/lib/errors';
import { deleteObject, putObject } from '../../src/lib/storage';
import {
  claimJob,
  enqueueObjectDeletion,
  findJob,
  markCompleted,
  markSkipped,
} from '../../src/modules/materials/object-deletions.repository';
import { attemptObjectDeletion } from '../../src/modules/materials/object-deletions.service';
import {
  closeDb,
  createClass,
  createUser,
  resetDb,
} from '../helpers/db';
import { createTopic } from '../helpers/fixtures';

const storageRoot = mkdtempSync(path.join(tmpdir(), 'eduvark-status-storage-'));
process.env.LOCAL_STORAGE_DIR = storageRoot;
process.env.STORAGE_DELETION_INTERVAL_MS = '0';
process.env.STORAGE_DELETION_MAX_ATTEMPTS = '2';

vi.mock('../../src/lib/storage', async (importOriginal) => {
  const actual = await importOriginal<typeof import('../../src/lib/storage')>();
  return {
    ...actual,
    putObject: vi.fn(actual.putObject),
    deleteObject: vi.fn(actual.deleteObject),
  };
});

const describeDb = process.env.DATABASE_URL ? describe : describe.skip;
const mockedDeleteObject = vi.mocked(deleteObject);

let classId: string;
let lecturerId: string;
let topicId: string;

async function insertMaterial(input: {
  objectKey: string;
  deletedAt: Date | null;
}): Promise<string> {
  const result = await getPool().query<{ id: string }>(
    `
      INSERT INTO materials (
        topic_id, uploaded_by, vark_type, title, kind, object_key, mime,
        size_bytes, deleted_at
      )
      VALUES ($1, $2, 'VISUAL', 'Materi Status', 'FILE', $3,
              'application/pdf', 32, $4)
      RETURNING id
    `,
    [topicId, lecturerId, input.objectKey, input.deletedAt],
  );
  return result.rows[0]!.id;
}

async function createJob(objectKey: string, materialId: string): Promise<string> {
  const client = await getPool().connect();
  try {
    await client.query('BEGIN');
    const jobId = await enqueueObjectDeletion(client, {
      objectKey,
      materialId,
      requestedBy: lecturerId,
      reason: 'MATERIAL_DELETED',
    });
    await client.query('COMMIT');
    return jobId;
  } finally {
    client.release();
  }
}

async function makeDue(jobId: string): Promise<void> {
  await getPool().query(
    'UPDATE material_object_deletions SET next_attempt_at = now() WHERE id = $1',
    [jobId],
  );
}

beforeEach(async () => {
  mockedDeleteObject.mockClear();
  await resetDb();
  classId = await createClass();
  lecturerId = await createUser({
    loginId: '1990010001',
    password: 'LecturerPass123',
    role: 'LECTURER',
  });
  topicId = await createTopic({
    classId,
    createdBy: lecturerId,
    title: 'Topik Status',
    status: 'DRAFT',
  });
});

afterAll(async () => {
  delete process.env.STORAGE_DELETION_MAX_ATTEMPTS;
  await closeDb();
});

describeDb('status penghapusan fisik objek penyimpanan', () => {
  it('COMPLETED saat objek berhasil dihapus', async () => {
    const key = 'materials/status-success.pdf';
    await putObject(key, Buffer.from('%PDF-1.7 sukses'), 'application/pdf');
    const materialId = await insertMaterial({
      objectKey: key,
      deletedAt: new Date(Date.now() - 1000),
    });
    const jobId = await createJob(key, materialId);

    expect(await attemptObjectDeletion(jobId)).toBe('COMPLETED');
    expect(mockedDeleteObject).toHaveBeenCalledWith(key);
    expect((await findJob(jobId))?.state).toBe('COMPLETED');
    await expect(stat(path.join(storageRoot, key))).rejects.toThrow();
  });

  it('COMPLETED saat objek sudah tidak ada (ABSENT) tanpa panggilan deleteObject', async () => {
    const key = 'materials/status-absent.pdf';
    const materialId = await insertMaterial({
      objectKey: key,
      deletedAt: new Date(Date.now() - 1000),
    });
    const jobId = await createJob(key, materialId);

    expect(await attemptObjectDeletion(jobId)).toBe('COMPLETED');
    expect(mockedDeleteObject).not.toHaveBeenCalled();
    expect((await findJob(jobId))?.state).toBe('ABSENT');
  });

  it('PENDING saat pekerjaan sedang dijalankan proses lain (klaim masih aktif)', async () => {
    const key = 'materials/status-running.pdf';
    await putObject(key, Buffer.from('%PDF-1.7 jalan'), 'application/pdf');
    const materialId = await insertMaterial({
      objectKey: key,
      deletedAt: new Date(Date.now() - 1000),
    });
    const jobId = await createJob(key, materialId);

    const claimed = await claimJob(jobId, 120);
    expect(claimed?.attempts).toBe(1);

    expect(await attemptObjectDeletion(jobId)).toBe('PENDING');
    expect(mockedDeleteObject).not.toHaveBeenCalled();
    expect((await findJob(jobId))?.state).toBe('PENDING');
  });

  it('SKIPPED saat objek masih dirujuk materi aktif; objek dipertahankan', async () => {
    const key = 'materials/status-protected.pdf';
    await putObject(key, Buffer.from('%PDF-1.7 lindung'), 'application/pdf');
    const softDeletedId = await insertMaterial({
      objectKey: key,
      deletedAt: new Date(Date.now() - 1000),
    });
    // Materi aktif dengan kunci yang sama (mis. hasil salin data historis).
    await insertMaterial({ objectKey: key, deletedAt: null });
    const jobId = await createJob(key, softDeletedId);

    expect(await attemptObjectDeletion(jobId)).toBe('SKIPPED');
    expect(mockedDeleteObject).not.toHaveBeenCalled();
    const job = await findJob(jobId);
    expect(job?.state).toBe('SKIPPED');
    expect(job?.lastError).toBe('ACTIVE_REFERENCE');
    await stat(path.join(storageRoot, key));
  });

  it('PENDING saat percobaan gagal dan batas belum habis, FAILED saat percobaan terakhir gagal', async () => {
    const key = 'materials/status-failure.pdf';
    await putObject(key, Buffer.from('%PDF-1.7 gagal'), 'application/pdf');
    const materialId = await insertMaterial({
      objectKey: key,
      deletedAt: new Date(Date.now() - 1000),
    });
    const jobId = await createJob(key, materialId);

    mockedDeleteObject.mockRejectedValueOnce(
      new AppError('STORAGE_UNAVAILABLE', 502, 'R2 down'),
    );
    expect(await attemptObjectDeletion(jobId)).toBe('PENDING');
    expect((await findJob(jobId))?.state).toBe('PENDING');

    // Percobaan kedua adalah percobaan terakhir (max attempts = 2).
    await makeDue(jobId);
    mockedDeleteObject.mockRejectedValueOnce(
      new AppError('STORAGE_UNAVAILABLE', 502, 'R2 still down'),
    );
    expect(await attemptObjectDeletion(jobId)).toBe('FAILED');
    const job = await findJob(jobId);
    expect(job?.state).toBe('FAILED');
    expect(job?.attempts).toBe(2);
    expect(job?.lastError).toContain('R2 still down');
    // Materi tetap tersembunyi; objek tetap ada untuk tindak lanjut operator.
    await stat(path.join(storageRoot, key));
  });

  it('UNKNOWN saat pekerjaan penghapusan tidak ditemukan (status fisik tidak pasti)', async () => {
    expect(await attemptObjectDeletion(randomUUID())).toBe('UNKNOWN');
    expect(mockedDeleteObject).not.toHaveBeenCalled();
  });

  it('hasil worker dengan lease kedaluwarsa tidak dapat menimpa klaim yang lebih baru', async () => {
    const key = 'materials/status-stale-claim.pdf';
    await putObject(key, Buffer.from('%PDF-1.7 klaim'), 'application/pdf');
    const materialId = await insertMaterial({
      objectKey: key,
      deletedAt: new Date(Date.now() - 1000),
    });
    const jobId = await createJob(key, materialId);

    // Worker lama klaim #1, lalu lease kedaluwarsa (disimulasikan jatuh tempo).
    const firstClaim = await claimJob(jobId, 120);
    expect(firstClaim?.attempts).toBe(1);
    await makeDue(jobId);

    // Worker baru klaim #2.
    const secondClaim = await claimJob(jobId, 120);
    expect(secondClaim?.attempts).toBe(2);

    // Hasil worker lama (klaim #1) harus ditolak.
    expect(await markCompleted(jobId, firstClaim!.attempts, 'COMPLETED')).toBe(false);
    expect(await markSkipped(jobId, firstClaim!.attempts, 'ACTIVE_REFERENCE')).toBe(false);
    const stillMine = await findJob(jobId);
    expect(stillMine?.state).toBe('PENDING');
    expect(stillMine?.attempts).toBe(2);

    // Klaim terbaru tetap dapat menyelesaikan pekerjaan.
    expect(await attemptObjectDeletion(jobId)).toBe('PENDING'); // masih diklaim #2
    await makeDue(jobId);
    expect(await attemptObjectDeletion(jobId)).toBe('COMPLETED');
    expect((await findJob(jobId))?.attempts).toBe(3);
  });
});
