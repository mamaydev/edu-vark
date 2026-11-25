import { createApp } from './app';
import { getEnv } from './config/env';
import { closePool } from './db/client';
import { logger } from './lib/logger';
import { startFileReplacementReconciliationWorker } from './modules/materials/file-replacements.service';
import { startObjectDeletionWorker } from './modules/materials/object-deletions.service';

async function main(): Promise<void> {
  const env = getEnv();
  const app = createApp();

  const server = app.listen(env.PORT, () => {
    logger.info({ port: env.PORT, env: env.NODE_ENV }, 'EduVARK backend listening');
  });

  // Worker hanya mengolah antrean penghapusan objek yang tercatat oleh fitur
  // hapus/ganti berkas. Soft delete lama (backlog) tidak pernah dimasukkan ke
  // antrean ini, sehingga restart/aktivasi backend tidak memicu pembersihan
  // backlog secara otomatis.
  const stopDeletionWorker = startObjectDeletionWorker();
  // Niat Ganti File yang tertinggal (mis. proses berhenti setelah unggahan
  // tetapi sebelum metadata commit) diselesaikan dari state database, bukan
  // dari respons yang hilang.
  const stopReplacementWorker = startFileReplacementReconciliationWorker();

  const shutdown = (signal: string): void => {
    logger.info({ signal }, 'Shutting down');
    stopDeletionWorker();
    stopReplacementWorker();
    server.close(() => {
      void closePool().finally(() => process.exit(0));
    });
    setTimeout(() => process.exit(1), 10_000).unref();
  };

  process.on('SIGINT', () => shutdown('SIGINT'));
  process.on('SIGTERM', () => shutdown('SIGTERM'));
}

main().catch((error: unknown) => {
  logger.error({ err: error }, 'Failed to start backend');
  process.exit(1);
});
