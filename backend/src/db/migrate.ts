import { createHash } from 'node:crypto';
import fs from 'node:fs';
import path from 'node:path';
import { closePool, getPool } from './client';
import { logger } from '../lib/logger';

const MIGRATION_LOCK_ID = 812_345_678;

async function main(): Promise<void> {
  const pool = getPool();
  const client = await pool.connect();

  try {
    // Serialise migrations across processes/containers.
    await client.query('SELECT pg_advisory_lock($1)', [MIGRATION_LOCK_ID]);
    await client.query(`
      CREATE TABLE IF NOT EXISTS schema_migrations (
        id text PRIMARY KEY,
        applied_at timestamptz NOT NULL DEFAULT now()
      )
    `);
    // Checksums are optional history: older installations predate this column
    // and are backfilled instead of being rejected. A non-null checksum that
    // no longer matches the file means an already-applied migration was edited,
    // which would leave environments on divergent schemas — fail fast instead.
    await client.query(
      'ALTER TABLE schema_migrations ADD COLUMN IF NOT EXISTS checksum text',
    );

    const migrationsDir = path.join(__dirname, 'migrations');
    const files = fs
      .readdirSync(migrationsDir)
      .filter((file) => file.endsWith('.sql'))
      .sort();

    const appliedResult = await client.query<{ id: string; checksum: string | null }>(
      'SELECT id, checksum FROM schema_migrations',
    );
    const applied = new Map(
      appliedResult.rows.map((row) => [row.id, row.checksum] as const),
    );

    let appliedCount = 0;

    for (const file of files) {
      const sql = fs.readFileSync(path.join(migrationsDir, file), 'utf8');
      const checksum = createHash('sha256').update(sql).digest('hex');

      if (applied.has(file)) {
        const recorded = applied.get(file);
        if (recorded === null) {
          // Legacy record from before checksums existed: adopt the current
          // file content instead of failing the installation.
          await client.query(
            'UPDATE schema_migrations SET checksum = $2 WHERE id = $1 AND checksum IS NULL',
            [file, checksum],
          );
          logger.info({ file }, 'Migration already applied (checksum backfilled)');
          continue;
        }
        if (recorded !== checksum) {
          throw new Error(
            'Migration ' +
              file +
              ' sudah tercatat applied tetapi isi file berubah ' +
              '(checksum tidak cocok). Buat migration baru, jangan edit file lama.',
          );
        }
        logger.info({ file }, 'Migration already applied');
        continue;
      }

      logger.info({ file }, 'Applying migration');

      await client.query('BEGIN');
      try {
        await client.query(sql);
        await client.query(
          'INSERT INTO schema_migrations (id, checksum) VALUES ($1, $2)',
          [file, checksum],
        );
        await client.query('COMMIT');
        appliedCount += 1;
      } catch (error) {
        await client.query('ROLLBACK');
        throw error;
      }
    }

    logger.info({ appliedCount }, 'Migrations complete');
  } finally {
    try {
      await client.query('SELECT pg_advisory_unlock($1)', [MIGRATION_LOCK_ID]);
    } finally {
      client.release();
      await closePool();
    }
  }
}

main().catch(async (error: unknown) => {
  logger.error({ err: error }, 'Migration failed');
  await closePool();
  process.exit(1);
});
