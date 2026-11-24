import argon2 from 'argon2';
import type { PoolClient } from 'pg';
import { getPool } from './client';
import { allocateAdminLoginId } from '../lib/login-id';
import { logger } from '../lib/logger';

/**
 * Advisory lock used to serialise concurrent seed processes. Distinct from the
 * migration lock (812_345_678) and the Admin allocation lock (812_345_679).
 */
export const ADMIN_SEED_LOCK_ID = 812_345_680;

/** Audit action used as the stable bootstrap marker (survives profile rename). */
export const ADMIN_SEED_AUDIT_ACTION = 'ADMIN_SEEDED';

export interface SeedAdminInput {
  fullName: string;
  password: string;
  /** Legacy variable: accepted but ignored, Admin identities are auto-issued. */
  legacyLoginId?: string;
}

export interface SeedAdminResult {
  created: boolean;
  loginId: string | null;
}

interface AdminRow {
  id: string;
  login_id: string;
}

/**
 * Idempotent Admin seed.
 *
 * The bootstrap account is identified by a stable audit marker (not by its
 * mutable full name), so renaming the profile does not make a later seed insert
 * a duplicate Admin. Concurrent seed processes are serialised with an advisory
 * transaction lock: exactly one creates, the other observes the marker and
 * reuses the account. For installations seeded before the marker existed, the
 * configured name is tried first and a sole existing Admin is adopted.
 *
 * The legacy `ADMIN_LOGIN_ID` value is ignored (FR-036); the identity is always
 * allocated from the 01401400001–01401499999 range.
 */
export async function seedAdmin(input: SeedAdminInput): Promise<SeedAdminResult> {
  const pool = getPool();
  const client = await pool.connect();

  try {
    await client.query('BEGIN');
    await client.query('SELECT pg_advisory_xact_lock($1)', [ADMIN_SEED_LOCK_ID]);
    await ensureActiveClass(client);

    const existing = await findBootstrapAdmin(client, input.fullName);

    if (existing) {
      await writeSeedMarker(client, existing.id, false);
      await client.query('COMMIT');
      return { created: false, loginId: existing.login_id };
    }

    const loginId = await allocateAdminLoginId(client);
    const passwordHash = await argon2.hash(input.password, { type: argon2.argon2id });
    const inserted = await client.query<AdminRow>(
      `
        INSERT INTO users (login_id, password_hash, full_name, role, must_change_password)
        VALUES ($1, $2, $3, 'ADMIN', true)
        RETURNING id, login_id
      `,
      [loginId, passwordHash, input.fullName],
    );
    const created = inserted.rows[0];
    if (!created) throw new Error('Seed Admin gagal: baris tidak terbentuk.');

    await writeSeedMarker(client, created.id, true);
    await client.query('COMMIT');
    return { created: true, loginId: created.login_id };
  } catch (error) {
    await client.query('ROLLBACK');
    throw error;
  } finally {
    client.release();
  }
}

async function findBootstrapAdmin(
  client: PoolClient,
  fullName: string,
): Promise<AdminRow | null> {
  // 1) Marker written by an earlier seed run: stable across profile renames.
  const marked = await client.query<AdminRow>(
    `
      SELECT u.id, u.login_id
      FROM audit_logs a
      JOIN users u ON u.id = a.entity_id
      WHERE a.action = $1
        AND a.entity = 'USER'
        AND u.role = 'ADMIN'
      ORDER BY a.created_at DESC
      LIMIT 1
    `,
    [ADMIN_SEED_AUDIT_ACTION],
  );
  if (marked.rows[0]) return marked.rows[0];

  // 2) Installations seeded before markers existed: same configured name.
  const named = await client.query<AdminRow>(
    `
      SELECT id, login_id
      FROM users
      WHERE role = 'ADMIN' AND full_name = $1
      ORDER BY created_at ASC, id ASC
      LIMIT 1
    `,
    [fullName],
  );
  if (named.rows[0]) return named.rows[0];

  // 3) A sole Admin is the bootstrap account even if its name was edited
  //    before this marker existed.
  const admins = await client.query<AdminRow>(
    `
      SELECT id, login_id
      FROM users
      WHERE role = 'ADMIN'
      ORDER BY created_at ASC, id ASC
    `,
  );
  return admins.rows.length === 1 ? admins.rows[0] : null;
}

async function writeSeedMarker(
  client: PoolClient,
  userId: string,
  created: boolean,
): Promise<void> {
  await client.query(
    `
      INSERT INTO audit_logs (actor_id, action, entity, entity_id, metadata)
      VALUES (NULL, $1, 'USER', $2, $3::jsonb)
    `,
    [ADMIN_SEED_AUDIT_ACTION, userId, JSON.stringify({ bootstrap: true, created })],
  );
}

async function ensureActiveClass(client: PoolClient): Promise<void> {
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
}
