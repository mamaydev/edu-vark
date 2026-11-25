import type { PoolClient } from 'pg';
import { getEnv } from '../../config/env';
import { getPool } from '../../db/client';
import { logger } from '../../lib/logger';
import { deleteObject, objectStat } from '../../lib/storage';
import {
  claimJob,
  enqueueObjectDeletion as enqueue,
  findJob,
  hasActiveObjectReference,
  listDueJobIds,
  markCompleted,
  markFailed,
  markRetry,
  markSkipped,
  type ObjectDeletionJob,
  type ObjectDeletionReason,
} from './object-deletions.repository';

/**
 * Status penghapusan fisik yang dilaporkan ke klien.
 *
 * - NONE      : operasi memang tidak memerlukan penghapusan objek (materi LINK)
 * - COMPLETED : objek terhapus, atau sudah dipastikan tidak ada
 * - PENDING   : pekerjaan masih dijadwalkan/sedang diproses/masih akan dicoba
 * - SKIPPED   : objek sengaja dipertahankan karena masih dirujuk materi aktif
 * - FAILED    : retry otomatis sudah habis; perlu tindak lanjut operator
 * - UNKNOWN   : status fisik belum dapat dipastikan (mis. pekerjaan hilang)
 */
export type FileDeletionOutcome =
  | 'NONE'
  | 'COMPLETED'
  | 'PENDING'
  | 'SKIPPED'
  | 'FAILED'
  | 'UNKNOWN';

/**
 * Masa sewa klaim pekerjaan. Bila proses backend mati setelah klaim tetapi
 * sebelum hasil tercatat, pekerjaan otomatis diambil alih setelah lease habis.
 * Penghapusan objek bersifat idempoten sehingga percobaan ulang aman.
 */
const LEASE_SECONDS = 120;
const RETRY_BASE_MS = 30_000;
const RETRY_MAX_MS = 30 * 60_000;

export async function enqueueObjectDeletion(
  client: PoolClient,
  input: {
    objectKey: string;
    materialId: string | null;
    requestedBy: string | null;
    reason: ObjectDeletionReason;
  },
): Promise<string> {
  return enqueue(client, input);
}

function retryDelayMs(attempts: number): number {
  const exponent = Math.max(0, attempts - 1);
  return Math.min(RETRY_BASE_MS * 2 ** exponent, RETRY_MAX_MS);
}

function errorMessage(error: unknown): string {
  return error instanceof Error ? error.message : 'Kesalahan penyimpanan tidak diketahui.';
}

/**
 * Satu percobaan penghapusan objek.
 *
 * Urutan: klaim pekerjaan → pastikan objek tidak lagi dirujuk materi aktif →
 * hapus → catat hasil. Kegagalan penyimpanan dijadwalkan ulang; setelah batas
 * percobaan habis pekerjaan ditandai FAILED untuk investigasi operasional,
 * tetapi materi tetap tersembunyi dan tidak pernah muncul kembali.
 */
function outcomeForState(job: ObjectDeletionJob | null): FileDeletionOutcome {
  if (!job) return 'UNKNOWN';
  switch (job.state) {
    case 'COMPLETED':
    case 'ABSENT':
      return 'COMPLETED';
    case 'SKIPPED':
      return 'SKIPPED';
    case 'FAILED':
      return 'FAILED';
    case 'PENDING':
      return 'PENDING';
  }
}

/** Baca status fisik terkini ketika pembaruan bersyarat kalah dari klaim lain. */
async function currentOutcome(jobId: string): Promise<FileDeletionOutcome> {
  return outcomeForState(await findJob(jobId));
}

/**
 * Satu percobaan penghapusan objek.
 *
 * Urutan: klaim pekerjaan → pastikan objek tidak lagi dirujuk materi aktif →
 * hapus → catat hasil. Setiap pencatatan hasil bersyarat pada nomor klaim
 * (`attempts`) sehingga worker dengan lease kedaluwarsa tidak dapat menimpa
 * hasil klaim yang lebih baru. Kegagalan penyimpanan dijadwalkan ulang; setelah
 * batas percobaan habis pekerjaan ditandai FAILED (bukan PENDING) untuk
 * investigasi operasional, tetapi materi tetap tersembunyi.
 */
export async function attemptObjectDeletion(
  jobId: string,
): Promise<FileDeletionOutcome> {
  const job = await findJob(jobId);
  if (!job) {
    // Pekerjaan yang seharusnya ada tidak ditemukan: status fisik tidak pasti.
    logger.error({ jobId }, 'Pekerjaan penghapusan objek tidak ditemukan');
    return 'UNKNOWN';
  }
  if (job.state !== 'PENDING') {
    return outcomeForState(job);
  }

  const claimed = await claimJob(job.id, LEASE_SECONDS);
  if (!claimed) {
    // Proses lain sedang/baru saja menangani pekerjaan ini.
    return currentOutcome(job.id);
  }

  const settle = async (
    recorded: boolean,
    outcome: FileDeletionOutcome,
  ): Promise<FileDeletionOutcome> => {
    if (recorded) return outcome;
    // Klaim ini sudah tidak berwenang (lease habis & diklaim ulang). Jangan
    // mengarang status; laporkan keadaan baris yang sekarang.
    logger.warn(
      { jobId: job.id, attempts: claimed.attempts },
      'Hasil penghapusan objek tidak dicatat: klaim sudah digantikan',
    );
    return currentOutcome(job.id);
  };

  try {
    const protectedByActiveMaterial = await hasActiveObjectReference(
      getPool(),
      claimed.objectKey,
    );
    if (protectedByActiveMaterial) {
      const recorded = await markSkipped(
        claimed.id,
        claimed.attempts,
        'ACTIVE_REFERENCE',
      );
      logger.warn(
        { jobId: claimed.id, objectKey: claimed.objectKey },
        'Penghapusan objek dilewati: masih dirujuk materi aktif',
      );
      return settle(recorded, 'SKIPPED');
    }

    const stat = await objectStat(claimed.objectKey);
    if (stat === null) {
      // Objek sudah tidak ada (mis. percobaan sebelumnya berhasil tetapi proses
      // berhenti sebelum mencatat hasil). Tidak ada aksi fisik yang dibutuhkan.
      const recorded = await markCompleted(claimed.id, claimed.attempts, 'ABSENT');
      return settle(recorded, 'COMPLETED');
    }

    await deleteObject(claimed.objectKey);
    const recorded = await markCompleted(claimed.id, claimed.attempts, 'COMPLETED');
    logger.info(
      { jobId: claimed.id, objectKey: claimed.objectKey, attempts: claimed.attempts },
      'Objek materi dihapus dari penyimpanan',
    );
    return settle(recorded, 'COMPLETED');
  } catch (error) {
    const message = errorMessage(error);
    const maxAttempts = getEnv().STORAGE_DELETION_MAX_ATTEMPTS;
    if (claimed.attempts >= maxAttempts) {
      const recorded = await markFailed(claimed.id, claimed.attempts, message);
      logger.error(
        { err: error, jobId: claimed.id, attempts: claimed.attempts },
        'Penghapusan objek materi gagal setelah batas percobaan',
      );
      return settle(recorded, 'FAILED');
    }
    const delay = retryDelayMs(claimed.attempts);
    const recorded = await markRetry(
      claimed.id,
      claimed.attempts,
      new Date(Date.now() + delay),
      message,
    );
    logger.warn(
      { err: error, jobId: claimed.id, attempts: claimed.attempts, delayMs: delay },
      'Penghapusan objek materi tertunda dan akan dicoba ulang',
    );
    return settle(recorded, 'PENDING');
  }
}

/**
 * Percobaan langsung setelah transaksi commit. Tidak pernah melempar error:
 * kegagalan penyimpanan tidak boleh membatalkan perubahan aplikasi yang sudah
 * tercatat, cukup dilaporkan sebagai penghapusan tertunda.
 */
export async function attemptObjectDeletionAfterCommit(
  jobId: string | null,
): Promise<FileDeletionOutcome> {
  if (!jobId) return 'NONE';
  try {
    return await attemptObjectDeletion(jobId);
  } catch (error) {
    // Kegagalan di luar jalur penyimpanan (mis. database) membuat status fisik
    // tidak dapat dipastikan. Jangan menjanjikan retry yang belum tentu ada.
    logger.error({ err: error, jobId }, 'Percobaan penghapusan objek gagal dijalankan');
    return 'UNKNOWN';
  }
}

export interface DeletionDrainResult {
  processed: number;
  completed: number;
  pending: number;
  skipped: number;
  failed: number;
  unknown: number;
}

/** Jalankan semua pekerjaan yang jatuh tempo; dipakai worker dan test retry. */
export async function processPendingObjectDeletions(
  limit = 25,
): Promise<DeletionDrainResult> {
  const ids = await listDueJobIds(limit);
  const counts = {
    processed: ids.length,
    completed: 0,
    pending: 0,
    skipped: 0,
    failed: 0,
    unknown: 0,
  };
  for (const id of ids) {
    const outcome = await attemptObjectDeletion(id);
    switch (outcome) {
      case 'COMPLETED':
        counts.completed += 1;
        break;
      case 'SKIPPED':
        counts.skipped += 1;
        break;
      case 'FAILED':
        counts.failed += 1;
        break;
      case 'UNKNOWN':
        counts.unknown += 1;
        break;
      default:
        counts.pending += 1;
    }
  }
  return counts;
}

export function describeJob(job: ObjectDeletionJob): Record<string, unknown> {
  return {
    id: job.id,
    objectKey: job.objectKey,
    reason: job.reason,
    state: job.state,
    attempts: job.attempts,
    lastError: job.lastError,
  };
}

/**
 * Worker retry untuk pekerjaan yang masih PENDING. Hanya memproses antrean
 * fitur ini; soft delete lama (backlog) tidak pernah masuk antrean secara
 * otomatis sehingga restart backend tidak memicu pembersihan backlog.
 */
export function startObjectDeletionWorker(): () => void {
  const interval = getEnv().STORAGE_DELETION_INTERVAL_MS;
  if (interval <= 0) return () => undefined;

  let stopped = false;
  let running = false;
  let timer: NodeJS.Timeout | null = null;

  const schedule = (): void => {
    if (stopped) return;
    timer = setTimeout(run, interval);
    timer.unref();
  };

  const run = async (): Promise<void> => {
    if (running) {
      schedule();
      return;
    }
    running = true;
    try {
      const result = await processPendingObjectDeletions();
      if (result.processed > 0) {
        logger.info(result, 'Worker penghapusan objek materi selesai');
      }
    } catch (error) {
      logger.error({ err: error }, 'Worker penghapusan objek materi gagal');
    } finally {
      running = false;
      schedule();
    }
  };

  // Jeda awal satu interval: aktivasi/restart tidak menjalankan pembersihan
  // tambahan di luar antrean pekerjaan yang memang sudah tercatat.
  schedule();
  return () => {
    stopped = true;
    if (timer) clearTimeout(timer);
  };
}
