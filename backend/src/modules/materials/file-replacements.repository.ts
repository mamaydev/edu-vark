import type { PoolClient } from 'pg';
import { getPool } from '../../db/client';

/**
 * Status niat penggantian berkas.
 *
 * - PENDING  : objek pengganti mungkin sudah/sedang ditulis, metadata belum
 *              dipastikan mengarah ke objek tersebut.
 * - ACTIVE   : metadata materi sudah commit menunjuk objek pengganti.
 * - ABANDONED: dipastikan tidak aktif; objek pengganti masuk antrean bersih.
 */
export type FileReplacementState = 'PENDING' | 'ACTIVE' | 'ABANDONED';

export interface FileReplacementRow {
  id: string;
  materialId: string;
  requestedBy: string | null;
  oldObjectKey: string;
  newObjectKey: string;
  state: FileReplacementState;
  leaseToken: string | null;
  leaseExpiresAt: Date | null;
  oldDeletionJobId: string | null;
  cleanupJobId: string | null;
  attempts: number;
  lastError: string | null;
  createdAt: Date;
  resolvedAt: Date | null;
}

interface Row {
  id: string;
  material_id: string;
  requested_by: string | null;
  old_object_key: string;
  new_object_key: string;
  state: FileReplacementState;
  lease_token: string | null;
  lease_expires_at: Date | null;
  old_deletion_job_id: string | null;
  cleanup_job_id: string | null;
  attempts: number;
  last_error: string | null;
  created_at: Date;
  resolved_at: Date | null;
}

const COLUMNS =
  'id, material_id, requested_by, old_object_key, new_object_key, state, ' +
  'lease_token, lease_expires_at, old_deletion_job_id, cleanup_job_id, ' +
  'attempts, last_error, created_at, resolved_at';

function toRow(row: Row): FileReplacementRow {
  return {
    id: row.id,
    materialId: row.material_id,
    requestedBy: row.requested_by,
    oldObjectKey: row.old_object_key,
    newObjectKey: row.new_object_key,
    state: row.state,
    leaseToken: row.lease_token,
    leaseExpiresAt: row.lease_expires_at,
    oldDeletionJobId: row.old_deletion_job_id,
    cleanupJobId: row.cleanup_job_id,
    attempts: row.attempts,
    lastError: row.last_error,
    createdAt: row.created_at,
    resolvedAt: row.resolved_at,
  };
}

/**
 * Catat niat penggantian SEBELUM objek pengganti ditulis. Baris ini tidak
 * memegang transaksi perubahan metadata sehingga tetap tersimpan walau proses
 * berhenti sebelum commit metadata.
 */
export async function beginReplacement(input: {
  materialId: string;
  requestedBy: string | null;
  oldObjectKey: string;
  newObjectKey: string;
  leaseToken: string;
  leaseMs: number;
}): Promise<FileReplacementRow> {
  const result = await getPool().query<Row>(
    `
      INSERT INTO material_file_replacements (
        material_id, requested_by, old_object_key, new_object_key,
        lease_token, lease_expires_at
      )
      VALUES ($1, $2, $3, $4, $5, now() + ($6::bigint * interval '1 millisecond'))
      RETURNING ${COLUMNS}
    `,
    [
      input.materialId,
      input.requestedBy,
      input.oldObjectKey,
      input.newObjectKey,
      input.leaseToken,
      input.leaseMs,
    ],
  );
  return toRow(result.rows[0]!);
}

export async function findReplacement(
  id: string,
): Promise<FileReplacementRow | null> {
  const result = await getPool().query<Row>(
    `SELECT ${COLUMNS} FROM material_file_replacements WHERE id = $1`,
    [id],
  );
  const row = result.rows[0];
  return row ? toRow(row) : null;
}

/** Kunci baris niat agar penyelesaian bersaing diserialkan. */
export async function lockReplacement(
  client: PoolClient,
  id: string,
): Promise<FileReplacementRow | null> {
  const result = await client.query<Row>(
    `SELECT ${COLUMNS} FROM material_file_replacements WHERE id = $1 FOR UPDATE`,
    [id],
  );
  const row = result.rows[0];
  return row ? toRow(row) : null;
}

/**
 * Tandai penggantian aktif. Syarat `lease_token` membuat pemilik klaim lama
 * tidak dapat menimpa hasil rekonsiliasi yang lebih baru.
 */
export async function markReplacementActive(
  client: PoolClient,
  id: string,
  leaseToken: string,
  oldDeletionJobId: string,
): Promise<boolean> {
  const result = await client.query(
    `
      UPDATE material_file_replacements
      SET state = 'ACTIVE',
          lease_token = NULL,
          lease_expires_at = NULL,
          old_deletion_job_id = $3,
          resolved_at = now(),
          last_error = NULL
      WHERE id = $1 AND state = 'PENDING' AND lease_token = $2
    `,
    [id, leaseToken, oldDeletionJobId],
  );
  return (result.rowCount ?? 0) > 0;
}

/** Tandai penggantian batal dan simpan pekerjaan bersih objek pengganti. */
export async function markReplacementAbandoned(
  client: PoolClient,
  id: string,
  leaseToken: string,
  cleanupJobId: string,
  reason: string | null,
): Promise<boolean> {
  const result = await client.query(
    `
      UPDATE material_file_replacements
      SET state = 'ABANDONED',
          lease_token = NULL,
          lease_expires_at = NULL,
          cleanup_job_id = $3,
          resolved_at = now(),
          last_error = $4
      WHERE id = $1 AND state = 'PENDING' AND lease_token = $2
    `,
    [id, leaseToken, cleanupJobId, reason],
  );
  return (result.rowCount ?? 0) > 0;
}

/** Simpan ID pekerjaan lama tanpa mengubah state (dipakai pemulihan). */
export async function recordOldDeletionJob(
  client: PoolClient,
  id: string,
  jobId: string,
): Promise<void> {
  await client.query(
    'UPDATE material_file_replacements SET old_deletion_job_id = $2 WHERE id = $1',
    [id, jobId],
  );
}

/**
 * Ambil alih niat PENDING yang lease-nya sudah habis. Pembaruan bersyarat
 * memastikan hanya satu worker yang berhasil untuk satu baris.
 */
export async function claimExpiredReplacement(
  id: string,
  leaseToken: string,
  leaseMs: number,
): Promise<FileReplacementRow | null> {
  const result = await getPool().query<Row>(
    `
      UPDATE material_file_replacements
      SET lease_token = $2,
          lease_expires_at = now() + ($3::bigint * interval '1 millisecond'),
          attempts = attempts + 1
      WHERE id = $1
        AND state = 'PENDING'
        AND (lease_expires_at IS NULL OR lease_expires_at < now())
      RETURNING ${COLUMNS}
    `,
    [id, leaseToken, leaseMs],
  );
  const row = result.rows[0];
  return row ? toRow(row) : null;
}

export async function listExpiredReplacementIds(limit: number): Promise<string[]> {
  const result = await getPool().query<{ id: string }>(
    `
      SELECT id
      FROM material_file_replacements
      WHERE state = 'PENDING'
        AND (lease_expires_at IS NULL OR lease_expires_at < now())
      ORDER BY created_at ASC
      LIMIT $1
    `,
    [limit],
  );
  return result.rows.map((row) => row.id);
}
