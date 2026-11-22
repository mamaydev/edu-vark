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

    const migrationsDir = path.join(__dirname, 'migrations');
    const files = fs
      .readdirSync(migrationsDir)
      .filter((file) => file.endsWith('.sql'))
      .sort();

    const appliedResult = await client.query<{ id: string }>(
      'SELECT id FROM schema_migrations',
    );
    const applied = new Set(appliedResult.rows.map((row) => row.id));

    let appliedCount = 0;

    for (const file of files) {
      if (applied.has(file)) {
        logger.info({ file }, 'Migration already applied');
        continue;
      }

      const sql = fs.readFileSync(path.join(migrationsDir, file), 'utf8');
      logger.info({ file }, 'Applying migration');

      await client.query('BEGIN');
      try {
        await client.query(sql);
        await client.query('INSERT INTO schema_migrations (id) VALUES ($1)', [file]);
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
