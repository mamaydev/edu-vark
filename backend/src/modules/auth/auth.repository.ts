import type { PoolClient } from 'pg';
import type { UserRole, VarkType } from '../../types/auth';

export interface UserRow {
  id: string;
  login_id: string;
  password_hash: string;
  full_name: string;
  role: UserRole;
  status: 'ACTIVE' | 'INACTIVE';
  must_change_password: boolean;
}

export interface SessionContextRow {
  session_id: string;
  user_id: string;
  vark_type: VarkType | null;
  last_seen_at: Date;
  expires_at: Date;
  is_active: boolean;
  revoked_at: Date | null;
  role: UserRole;
  status: 'ACTIVE' | 'INACTIVE';
  must_change_password: boolean;
  login_id: string;
  full_name: string;
}

export async function findUserByLoginId(
  client: PoolClient,
  loginId: string,
): Promise<UserRow | null> {
  const result = await client.query<UserRow>(
    `
      SELECT id, login_id, password_hash, full_name, role, status, must_change_password
      FROM users
      WHERE login_id = $1
    `,
    [loginId],
  );
  return result.rows[0] ?? null;
}

export async function findUserById(
  client: PoolClient,
  userId: string,
): Promise<UserRow | null> {
  const result = await client.query<UserRow>(
    `
      SELECT id, login_id, password_hash, full_name, role, status, must_change_password
      FROM users
      WHERE id = $1
    `,
    [userId],
  );
  return result.rows[0] ?? null;
}

export async function findSessionContext(
  client: PoolClient,
  sessionId: string,
): Promise<SessionContextRow | null> {
  const result = await client.query<SessionContextRow>(
    `
      SELECT
        s.id AS session_id,
        s.user_id,
        s.vark_type,
        s.last_seen_at,
        s.expires_at,
        s.is_active,
        s.revoked_at,
        u.role,
        u.status,
        u.must_change_password,
        u.login_id,
        u.full_name
      FROM auth_sessions s
      JOIN users u ON u.id = s.user_id
      WHERE s.id = $1
    `,
    [sessionId],
  );
  return result.rows[0] ?? null;
}

export async function findSessionByRefreshHash(
  client: PoolClient,
  refreshHash: string,
): Promise<SessionContextRow | null> {
  const result = await client.query<SessionContextRow>(
    `
      SELECT
        s.id AS session_id,
        s.user_id,
        s.vark_type,
        s.last_seen_at,
        s.expires_at,
        s.is_active,
        s.revoked_at,
        u.role,
        u.status,
        u.must_change_password,
        u.login_id,
        u.full_name
      FROM auth_sessions s
      JOIN users u ON u.id = s.user_id
      WHERE s.refresh_token_hash = $1
    `,
    [refreshHash],
  );
  return result.rows[0] ?? null;
}

export async function revokeActiveSessions(
  client: PoolClient,
  userId: string,
): Promise<void> {
  await client.query(
    `
      UPDATE auth_sessions
      SET is_active = false, revoked_at = now()
      WHERE user_id = $1 AND is_active = true
    `,
    [userId],
  );
}

export async function createSession(
  client: PoolClient,
  input: {
    id: string;
    userId: string;
    tokenHash: string;
    refreshTokenHash: string;
    expiresAt: Date;
  },
): Promise<void> {
  await client.query(
    `
      INSERT INTO auth_sessions
        (id, user_id, token_hash, refresh_token_hash, vark_type, is_active, created_at, last_seen_at, expires_at)
      VALUES ($1, $2, $3, $4, NULL, true, now(), now(), $5)
    `,
    [input.id, input.userId, input.tokenHash, input.refreshTokenHash, input.expiresAt],
  );
}

export async function updateSessionTokens(
  client: PoolClient,
  sessionId: string,
  tokenHash: string,
  refreshTokenHash: string,
): Promise<void> {
  await client.query(
    `
      UPDATE auth_sessions
      SET token_hash = $2, refresh_token_hash = $3, last_seen_at = now()
      WHERE id = $1
    `,
    [sessionId, tokenHash, refreshTokenHash],
  );
}

export async function touchSession(
  client: PoolClient,
  sessionId: string,
): Promise<void> {
  await client.query('UPDATE auth_sessions SET last_seen_at = now() WHERE id = $1', [
    sessionId,
  ]);
}

export async function revokeSession(
  client: PoolClient,
  sessionId: string,
): Promise<void> {
  await client.query(
    `
      UPDATE auth_sessions
      SET is_active = false, revoked_at = now()
      WHERE id = $1 AND is_active = true
    `,
    [sessionId],
  );
}

export async function revokeAllSessions(
  client: PoolClient,
  userId: string,
): Promise<void> {
  await client.query(
    `
      UPDATE auth_sessions
      SET is_active = false, revoked_at = now()
      WHERE user_id = $1 AND is_active = true
    `,
    [userId],
  );
}

export async function setVark(
  client: PoolClient,
  sessionId: string,
  vark: VarkType,
): Promise<boolean> {
  const result = await client.query(
    `
      UPDATE auth_sessions
      SET vark_type = $2
      WHERE id = $1 AND vark_type IS NULL
      RETURNING id
    `,
    [sessionId, vark],
  );
  return (result.rowCount ?? 0) > 0;
}

export async function updatePassword(
  client: PoolClient,
  userId: string,
  passwordHash: string,
): Promise<void> {
  await client.query(
    `
      UPDATE users
      SET password_hash = $2, must_change_password = false, updated_at = now()
      WHERE id = $1
    `,
    [userId, passwordHash],
  );
}
