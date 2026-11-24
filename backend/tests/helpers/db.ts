import { closePool, getPool } from '../../src/db/client';
import { hashPassword } from '../../src/lib/password';
import type { UserRole } from '../../src/types/auth';

/**
 * Canonical Admin identity for integration tests. Admin identities are
 * system-issued (`014014` + five digits) and the legacy `admin` username is
 * rejected by login, so every package must create its Admin fixture with
 * this identifier.
 */
export const TEST_ADMIN_LOGIN_ID = '01401400001';
export const TEST_ADMIN_PASSWORD = 'AdminPass1234';

function assertTestDatabase(): void {
  const url = process.env.DATABASE_URL;
  if (!url) {
    throw new Error('DATABASE_URL wajib diisi untuk integration test.');
  }
  const database = new URL(url).pathname.replace(/^\//, '');
  const allowed =
    database.endsWith('_test') || process.env.ALLOW_TEST_DB_RESET === '1';
  if (!allowed) {
    throw new Error(
      'Menolak TRUNCATE pada database "' +
        database +
        '". Gunakan database berakhiran _test atau set ALLOW_TEST_DB_RESET=1.',
    );
  }
}

export async function resetDb(): Promise<void> {
  assertTestDatabase();
  const client = await getPool().connect();
  try {
    await client.query(
      `
        TRUNCATE TABLE
          audit_logs,
          assessments,
          submissions,
          feedback,
          materials,
          case_studies,
          topics,
          teaching_assignments,
          enrollments,
          auth_sessions,
          classes,
          users
        RESTART IDENTITY CASCADE
      `,
    );
  } finally {
    client.release();
  }
}

export async function closeDb(): Promise<void> {
  await closePool();
}

export async function createClass(code = 'IBD-01'): Promise<string> {
  const result = await getPool().query<{ id: string }>(
    `
      INSERT INTO classes (code, name, semester, is_active)
      VALUES ($1, 'Implementasi Basis Data', '2026-1', true)
      RETURNING id
    `,
    [code],
  );
  const id = result.rows[0]?.id;
  if (!id) throw new Error('Failed to create class');
  return id;
}

export async function createUser(input: {
  loginId: string;
  password: string;
  role: UserRole;
  fullName?: string;
  status?: 'ACTIVE' | 'INACTIVE';
  mustChangePassword?: boolean;
}): Promise<string> {
  const passwordHash = await hashPassword(input.password);
  const result = await getPool().query<{ id: string }>(
    `
      INSERT INTO users
        (login_id, password_hash, full_name, role, status, must_change_password)
      VALUES ($1, $2, $3, $4, $5, $6)
      RETURNING id
    `,
    [
      input.loginId,
      passwordHash,
      input.fullName ?? 'User ' + input.loginId,
      input.role,
      input.status ?? 'ACTIVE',
      input.mustChangePassword ?? false,
    ],
  );
  const id = result.rows[0]?.id;
  if (!id) throw new Error('Failed to create user');
  return id;
}

export async function createAdmin(
  input: { loginId?: string; password?: string; fullName?: string } = {},
): Promise<string> {
  return createUser({
    loginId: input.loginId ?? TEST_ADMIN_LOGIN_ID,
    password: input.password ?? TEST_ADMIN_PASSWORD,
    role: 'ADMIN',
    fullName: input.fullName,
  });
}

export async function activeSessionCount(userId: string): Promise<number> {
  const result = await getPool().query<{ count: string }>(
    'SELECT count(*)::text AS count FROM auth_sessions WHERE user_id = $1 AND is_active = true',
    [userId],
  );
  return Number(result.rows[0]?.count ?? '0');
}
