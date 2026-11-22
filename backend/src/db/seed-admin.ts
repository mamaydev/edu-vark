import argon2 from 'argon2';
import { z } from 'zod';
import { closePool, getPool } from './client';
import { logger } from '../lib/logger';

const SeedSchema = z.object({
  ADMIN_LOGIN_ID: z.string().min(1),
  ADMIN_PASSWORD: z.string().min(6),
  ADMIN_FULL_NAME: z.string().min(1).default('Administrator'),
});

async function main(): Promise<void> {
  const parsed = SeedSchema.safeParse(process.env);
  if (!parsed.success) {
    throw new Error(
      'Invalid seed environment: ' + JSON.stringify(parsed.error.flatten().fieldErrors),
    );
  }

  const { ADMIN_LOGIN_ID, ADMIN_PASSWORD, ADMIN_FULL_NAME } = parsed.data;
  const passwordHash = await argon2.hash(ADMIN_PASSWORD, { type: argon2.argon2id });

  const pool = getPool();
  const client = await pool.connect();

  try {
    const activeClass = await client.query<{ id: string }>(
      'SELECT id FROM classes WHERE is_active = true LIMIT 1',
    );

    if (!activeClass.rows[0]) {
      await client.query(
        `
          INSERT INTO classes (code, name, semester, is_active)
          VALUES ('IBD-01', 'Implementasi Basis Data', '2026-1', true)
          ON CONFLICT (code) DO UPDATE SET is_active = true
        `,
      );
      logger.info('Default active class seeded');
    }

    const result = await client.query<{ id: string }>(
      `
        INSERT INTO users (login_id, password_hash, full_name, role, must_change_password)
        VALUES ($1, $2, $3, 'ADMIN', true)
        ON CONFLICT (login_id) DO NOTHING
        RETURNING id
      `,
      [ADMIN_LOGIN_ID, passwordHash, ADMIN_FULL_NAME],
    );

    if (result.rowCount === 0) {
      logger.info({ loginId: ADMIN_LOGIN_ID }, 'Admin already exists; seed skipped');
    } else {
      logger.info({ loginId: ADMIN_LOGIN_ID }, 'Admin seeded; password change required');
    }
  } finally {
    client.release();
    await closePool();
  }
}

main().catch(async (error: unknown) => {
  logger.error({ err: error }, 'Admin seed failed');
  await closePool();
  process.exit(1);
});
