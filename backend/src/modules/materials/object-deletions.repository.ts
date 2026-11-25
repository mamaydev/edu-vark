import type { PoolClient } from 'pg';
import { getPool } from '../../db/client';

export type ObjectDeletionReason =
  | 'MATERIAL_DELETED'
  | 'FILE_REPLACED'
  | 'BACKLOG_CLEANUP'
  | 'REPLACEMENT_ABANDONED';

export type ObjectDeletionState =
  | 'PENDING'
  | 'COMPLETED'
  | 'ABSENT'
  | 'FAILED'
  | 'SKIPPED';

export interface ObjectDeletionJob {
  id: string;
  objectKey: string;
  materialId: string | null;
  requestedBy: string | null;
  reason: ObjectDeletionReason;
  state: ObjectDeletionState;
  attempts: number;
  nextAttemptAt: Date;
  lastError: string | null;
  createdAt: Date;
}

interface JobRow {
  id: string;
  object_key: string;
  material_id: string | null;
  requested_by: string | null;
  reason: ObjectDeletionReason;
  state: ObjectDeletionState;
  attempts: number;
  next_attempt_at: Date;
  last_error: string | null;
  created_at: Date;
}

const COLUMNS =
  'id, object_key, material_id, requested_by, reason, state, attempts, ' +
  'next_attempt_at, last_error, created_at';

function toJob(row: JobRow): ObjectDeletionJob {
  return {
    id: row.id,
    objectKey: row.object_key,
    materialId: row.material_id,
    requestedBy: row.requested_by,
    reason: row.reason,
    state: row.state,
    attempts: row.attempts,
    nextAttemptAt: row.next_attempt_at,
    lastError: row.last_error,
    createdAt: row.created_at,
  };
}

/**
 * Catat niat penghapusan objek di dalam transaksi perubahan data aplikasi.
 * Unique index parsial menjamin hanya ada satu pekerjaan PENDING per object key,
 * sehingga percobaan berulang tidak menggandakan penghapusan. Bila pekerjaan
 * PENDING untuk key yang sama sudah ada, id-nya dikembalikan.
 */
export async function enqueueObjectDeletion(
  client: PoolClient,
  input: {
    objectKey: string;
    materialId: string | null;
    requestedBy: string | null;
    reason: ObjectDeletionReason;
  },
): Promise<string> {
  await client.query(
    `
      INSERT INTO material_object_deletions (object_key, material_id, requested_by, reason)
      VALUES ($1, $2, $3, $4)
      ON CONFLICT DO NOTHING
    `,
    [input.objectKey, input.materialId, input.requestedBy, input.reason],
  );

  const result = await client.query<{ id: string }>(
    `
      SELECT id
      FROM material_object_deletions
      WHERE object_key = $1 AND state = 'PENDING'
      ORDER BY created_at ASC
      LIMIT 1
    `,
    [input.objectKey],
  );
  const id = result.rows[0]?.id;
  if (!id) {
    throw new Error('Gagal mencatat pekerjaan penghapusan objek.');
  }
  return id;
}

/** Objek tidak boleh dihapus selama masih dirujuk materi yang aktif. */
export async function hasActiveObjectReference(
  client: Pick<PoolClient, 'query'>,
  objectKey: string,
): Promise<boolean> {
  const result = await client.query(
    `
      SELECT 1
      FROM materials
      WHERE object_key = $1 AND deleted_at IS NULL
      LIMIT 1
    `,
    [objectKey],
  );
  return result.rowCount !== null && result.rowCount > 0;
}

export async function listDueJobIds(limit: number): Promise<string[]> {
  const result = await getPool().query<{ id: string }>(
    `
      SELECT id
      FROM material_object_deletions
      WHERE state = 'PENDING' AND next_attempt_at <= now()
      ORDER BY next_attempt_at ASC
      LIMIT $1
    `,
    [limit],
  );
  return result.rows.map((row) => row.id);
}

/**
 * Ambil pekerjaan secara atomik dengan masa sewa (lease). Hanya satu proses
 * yang berhasil dalam jendela yang sama; bila proses mati di tengah jalan,
 * lease habis dan percobaan berikutnya mengambil alih. Upaya penghapusan
 * objek bersifat idempoten sehingga pengulangan aman.
 */
export async function claimJob(
  jobId: string,
  leaseSeconds: number,
): Promise<ObjectDeletionJob | null> {
  const result = await getPool().query<JobRow>(
    `
      UPDATE material_object_deletions
      SET attempts = attempts + 1,
          next_attempt_at = now() + ($2::int * interval '1 second')
      WHERE id = $1
        AND state = 'PENDING'
        AND next_attempt_at <= now()
      RETURNING ${COLUMNS}
    `,
    [jobId, leaseSeconds],
  );
  const row = result.rows[0];
  return row ? toJob(row) : null;
}

export async function findJob(jobId: string): Promise<ObjectDeletionJob | null> {
  const result = await getPool().query<JobRow>(
    `SELECT ${COLUMNS} FROM material_object_deletions WHERE id = $1`,
    [jobId],
  );
  const row = result.rows[0];
  return row ? toJob(row) : null;
}

/*
 * Semua pembaruan hasil memakai syarat `attempts = $n`, yaitu nomor klaim yang
 * diterima worker saat `claimJob`. Bila lease kedaluwarsa dan worker lain sudah
 * mengklaim ulang, `attempts` berubah sehingga hasil worker lama tidak dapat
 * menimpa hasil klaim yang lebih baru. Nilai `false` berarti baris sudah
 * dimiliki klaim lain dan pemanggil wajib membaca status terkini, bukan
 * menganggap hasilnya tercatat.
 */

export async function markCompleted(
  jobId: string,
  attempts: number,
  state: 'COMPLETED' | 'ABSENT',
): Promise<boolean> {
  const result = await getPool().query(
    `
      UPDATE material_object_deletions
      SET state = $3, completed_at = now(), last_error = NULL, next_attempt_at = now()
      WHERE id = $1 AND state = 'PENDING' AND attempts = $2
    `,
    [jobId, attempts, state],
  );
  return (result.rowCount ?? 0) > 0;
}

export async function markSkipped(
  jobId: string,
  attempts: number,
  reason: string,
): Promise<boolean> {
  const result = await getPool().query(
    `
      UPDATE material_object_deletions
      SET state = 'SKIPPED', completed_at = now(), last_error = $3, next_attempt_at = now()
      WHERE id = $1 AND state = 'PENDING' AND attempts = $2
    `,
    [jobId, attempts, reason],
  );
  return (result.rowCount ?? 0) > 0;
}

export async function markRetry(
  jobId: string,
  attempts: number,
  nextAttemptAt: Date,
  error: string,
): Promise<boolean> {
  const result = await getPool().query(
    `
      UPDATE material_object_deletions
      SET next_attempt_at = $3, last_error = $4
      WHERE id = $1 AND state = 'PENDING' AND attempts = $2
    `,
    [jobId, attempts, nextAttemptAt, error],
  );
  return (result.rowCount ?? 0) > 0;
}

export async function markFailed(
  jobId: string,
  attempts: number,
  error: string,
): Promise<boolean> {
  const result = await getPool().query(
    `
      UPDATE material_object_deletions
      SET state = 'FAILED', last_error = $3, completed_at = now()
      WHERE id = $1 AND state = 'PENDING' AND attempts = $2
    `,
    [jobId, attempts, error],
  );
  return (result.rowCount ?? 0) > 0;
}

/** Untuk test dan skrip operasional: riwayat pekerjaan sebuah materi. */
export async function listJobsForMaterial(
  materialId: string,
): Promise<ObjectDeletionJob[]> {
  const result = await getPool().query<JobRow>(
    `
      SELECT ${COLUMNS}
      FROM material_object_deletions
      WHERE material_id = $1
      ORDER BY created_at ASC
    `,
    [materialId],
  );
  return result.rows.map(toJob);
}
