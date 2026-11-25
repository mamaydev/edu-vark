import { randomUUID } from 'node:crypto';
import type { PoolClient } from 'pg';
import { getEnv } from '../../config/env';
import { withTransaction } from '../../db/transaction';
import { logger } from '../../lib/logger';
import { findMaterialById, type MaterialRow } from '../topics/topics.repository';
import {
  attemptObjectDeletionAfterCommit,
  enqueueObjectDeletion,
} from './object-deletions.service';
import {
  beginReplacement,
  claimExpiredReplacement,
  listExpiredReplacementIds,
  lockReplacement,
  markReplacementAbandoned,
  markReplacementActive,
  recordOldDeletionJob,
  type FileReplacementRow,
} from './file-replacements.repository';

/**
 * Kompensasi Ganti File yang tahan restart.
 *
 * Alur normal:
 *   1. catat niat (PENDING) SEBELUM objek pengganti ditulis;
 *   2. tulis objek pengganti;
 *   3. dalam satu transaksi: kunci niat, ubah metadata materi, tulis audit,
 *      catat pekerjaan hapus objek lama, tandai niat ACTIVE;
 *   4. setelah commit: coba hapus objek lama (boleh tertunda).
 *
 * Bila langkah mana pun gagal/ambigu, keputusan diambil dari state database
 * yang tersimpan, bukan dari respons yang hilang:
 *   - metadata menunjuk objek baru  -> ACTIVE: objek baru dilindungi, objek
 *     lama tetap punya pekerjaan hapus;
 *   - metadata masih menunjuk objek lama -> ABANDONED: objek baru dicatat ke
 *     antrean pembersihan yang idempoten dan dapat diulang;
 *   - database belum dapat dibaca -> PENDING: objek dipertahankan, lease
 *     habis lalu worker rekonsiliasi menyelesaikan.
 */

export type ReplacementResolution =
  | { status: 'ACTIVE'; material: MaterialRow; oldDeletionJobId: string | null }
  | { status: 'ABANDONED'; cleanupJobId: string | null }
  | { status: 'UNKNOWN' };

const RETRY_UNKNOWN_LOG =
  'Rekonsiliasi Ganti File tidak dapat memastikan hasil; objek dipertahankan';

function leaseMs(): number {
  return getEnv().FILE_REPLACEMENT_LEASE_MS;
}

/** Catat niat penggantian sebelum objek pengganti ditulis. */
export async function beginFileReplacement(input: {
  materialId: string;
  requestedBy: string;
  oldObjectKey: string;
  newObjectKey: string;
}): Promise<FileReplacementRow> {
  const leaseToken = randomUUID();
  return beginReplacement({
    materialId: input.materialId,
    requestedBy: input.requestedBy,
    oldObjectKey: input.oldObjectKey,
    newObjectKey: input.newObjectKey,
    leaseToken,
    leaseMs: leaseMs(),
  });
}

/**
 * Kunci niat di dalam transaksi metadata. Dipanggil SEBELUM baris materi
 * diperbarui agar urutan kunci konsisten dengan rekonsiliasi (niat lalu
 * materi) dan tidak menimbulkan deadlock. Mengembalikan `false` bila niat
 * sudah diambil alih penyelesai lain sehingga transaksi harus dibatalkan.
 */
export async function lockPendingFileReplacement(
  client: PoolClient,
  replacementId: string,
  leaseToken: string,
): Promise<boolean> {
  const row = await lockReplacement(client, replacementId);
  if (!row || row.state !== 'PENDING' || row.leaseToken !== leaseToken) {
    return false;
  }
  return true;
}

/** Tandai niat ACTIVE di transaksi yang sama dengan perubahan metadata. */
export async function markFileReplacementActive(
  client: PoolClient,
  replacementId: string,
  leaseToken: string,
  oldDeletionJobId: string,
): Promise<boolean> {
  return markReplacementActive(client, replacementId, leaseToken, oldDeletionJobId);
}

/** Pastikan pekerjaan hapus objek lama tercatat; idempoten. */
async function ensureOldDeletionJob(
  client: PoolClient,
  intent: FileReplacementRow,
): Promise<string> {
  if (intent.oldDeletionJobId) return intent.oldDeletionJobId;
  const jobId = await enqueueObjectDeletion(client, {
    objectKey: intent.oldObjectKey,
    materialId: intent.materialId,
    requestedBy: intent.requestedBy,
    reason: 'FILE_REPLACED',
  });
  await recordOldDeletionJob(client, intent.id, jobId);
  return jobId;
}

/**
 * Selesaikan niat berdasarkan keadaan database yang sebenarnya.
 *
 * `leaseToken` adalah klaim milik pemanggil. Bila klaim lain masih aktif,
 * hasilnya UNKNOWN supaya pemanggil tidak menebak; worker pemegang klaim akan
 * menyelesaikannya. Rekonsiliasi hanya menyentuh kunci yang tercatat pada baris
 * niat itu sendiri.
 */
export async function resolveFileReplacement(
  replacementId: string,
  leaseToken: string,
): Promise<ReplacementResolution> {
  return withTransaction(async (client) => {
    const intent = await lockReplacement(client, replacementId);
    if (!intent) {
      logger.error({ replacementId }, 'Niat Ganti File tidak ditemukan');
      return { status: 'UNKNOWN' } as const;
    }

    if (intent.state === 'ACTIVE') {
      const material = await findMaterialById(client, intent.materialId, {
        forUpdate: true,
      });
      if (!material) {
        logger.error(
          { replacementId, materialId: intent.materialId },
          'Materi untuk niat Ganti File ACTIVE tidak ditemukan',
        );
        return { status: 'UNKNOWN' } as const;
      }
      const oldDeletionJobId = await ensureOldDeletionJob(client, intent);
      return { status: 'ACTIVE', material, oldDeletionJobId } as const;
    }

    if (intent.state === 'ABANDONED') {
      return { status: 'ABANDONED', cleanupJobId: intent.cleanupJobId } as const;
    }

    // state PENDING.
    const leaseExpired =
      intent.leaseExpiresAt === null ||
      intent.leaseExpiresAt.getTime() <= Date.now();
    if (intent.leaseToken !== leaseToken && !leaseExpired) {
      // Worker lain sedang memegang klaim; jangan menebak dan jangan menghapus.
      return { status: 'UNKNOWN' } as const;
    }
    const effectiveToken = intent.leaseToken ?? leaseToken;

    const material = await findMaterialById(client, intent.materialId, {
      forUpdate: true,
    });

    if (material && material.object_key === intent.newObjectKey) {
      // Record yang tersimpan sudah menunjuk objek pengganti: itu bukti commit
      // metadata berhasil. Lindungi objek baru dan pastikan objek lama tetap
      // punya pekerjaan hapus yang tercatat.
      const oldDeletionJobId = await ensureOldDeletionJob(client, intent);
      const recorded = await markReplacementActive(
        client,
        intent.id,
        effectiveToken,
        oldDeletionJobId,
      );
      if (!recorded) {
        logger.warn(
          { replacementId, materialId: intent.materialId },
          'Niat Ganti File ACTIVE tidak dapat dicatat: klaim sudah digantikan',
        );
        return { status: 'UNKNOWN' } as const;
      }
      logger.warn(
        { replacementId, materialId: intent.materialId },
        'Rekonsiliasi menemukan objek pengganti aktif; objek dilindungi',
      );
      return { status: 'ACTIVE', material, oldDeletionJobId } as const;
    }

    // Metadata tidak menunjuk objek pengganti (rollback, gagal unggah, atau
    // operasi lain menang). Jangan hapus langsung: catat pekerjaan bersih yang
    // memeriksa referensi aktif dan dapat diulang setelah kegagalan storage.
    const cleanupJobId = await enqueueObjectDeletion(client, {
      objectKey: intent.newObjectKey,
      materialId: intent.materialId,
      requestedBy: intent.requestedBy,
      reason: 'REPLACEMENT_ABANDONED',
    });
    const recorded = await markReplacementAbandoned(
      client,
      intent.id,
      effectiveToken,
      cleanupJobId,
      'REPLACEMENT_DID_NOT_BECOME_ACTIVE',
    );
    if (!recorded) {
      logger.warn(
        { replacementId, materialId: intent.materialId },
        'Niat Ganti File ABANDONED tidak dapat dicatat: klaim sudah digantikan',
      );
      return { status: 'UNKNOWN' } as const;
    }
    logger.info(
      { replacementId, materialId: intent.materialId, cleanupJobId },
      'Niat Ganti File dibatalkan; objek pengganti masuk antrean pembersihan',
    );
    return { status: 'ABANDONED', cleanupJobId } as const;
  });
}

/**
 * Dipanggil dari jalur gagal `replaceMaterialFile`. Tidak pernah melempar
 * error database ke pemanggil: hasil tak pasti dilaporkan sebagai UNKNOWN agar
 * pemanggil mempertahankan objek dan membiarkan worker merekonsiliasi.
 */
export async function resolveFileReplacementAfterFailure(
  replacementId: string,
  leaseToken: string,
): Promise<ReplacementResolution> {
  try {
    return await resolveFileReplacement(replacementId, leaseToken);
  } catch (error) {
    logger.error(
      { err: error, replacementId },
      'Gagal merekonsiliasi niat Ganti File; objek pengganti dipertahankan',
    );
    return { status: 'UNKNOWN' };
  }
}

export interface ReplacementReconcileResult {
  processed: number;
  active: number;
  abandoned: number;
  skipped: number;
  unknown: number;
}

/**
 * Worker rekonsiliasi: ambil alih niat PENDING yang lease-nya habis, lalu
 * selesaikan berdasarkan state database. Aman dijalankan berulang; setiap
 * langkah memakai klaim bersyarat sehingga worker dengan lease kedaluwarsa
 * tidak dapat menimpa hasil yang lebih baru.
 */
export async function reconcileFileReplacements(
  limit = 25,
): Promise<ReplacementReconcileResult> {
  const ids = await listExpiredReplacementIds(limit);
  const counts: ReplacementReconcileResult = {
    processed: ids.length,
    active: 0,
    abandoned: 0,
    skipped: 0,
    unknown: 0,
  };

  for (const id of ids) {
    const claimToken = randomUUID();
    const claimed = await claimExpiredReplacement(id, claimToken, leaseMs());
    if (!claimed) {
      counts.skipped += 1;
      continue;
    }

    let resolution: ReplacementResolution;
    try {
      resolution = await resolveFileReplacement(id, claimToken);
    } catch (error) {
      logger.error({ err: error, replacementId: id }, RETRY_UNKNOWN_LOG);
      counts.unknown += 1;
      continue;
    }

    if (resolution.status === 'ACTIVE') {
      counts.active += 1;
      await attemptObjectDeletionAfterCommit(resolution.oldDeletionJobId);
      continue;
    }
    if (resolution.status === 'ABANDONED') {
      counts.abandoned += 1;
      await attemptObjectDeletionAfterCommit(resolution.cleanupJobId);
      continue;
    }
    counts.unknown += 1;
  }

  return counts;
}

/**
 * Worker periodik untuk niat Ganti File yang tertinggal (mis. proses backend
 * berhenti setelah unggahan tetapi sebelum metadata commit). Interval 0
 * mematikan worker (dipakai test); penyelesaian tetap bisa dipicu langsung.
 */
export function startFileReplacementReconciliationWorker(): () => void {
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
      const result = await reconcileFileReplacements();
      if (result.processed > 0) {
        logger.info(result, 'Worker rekonsiliasi Ganti File selesai');
      }
    } catch (error) {
      logger.error({ err: error }, 'Worker rekonsiliasi Ganti File gagal');
    } finally {
      running = false;
      schedule();
    }
  };

  schedule();
  return () => {
    stopped = true;
    if (timer) clearTimeout(timer);
  };
}

