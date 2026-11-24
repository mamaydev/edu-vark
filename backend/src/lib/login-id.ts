import type { PoolClient } from 'pg';
import { AppError, conflict } from './errors';
import type { UserRole } from '../types/auth';

/**
 * Academic login identifiers are UMY student NIMs and lecturer NIP/NIDN
 * numbers: 10–11 ASCII digits. Admin identities are system-issued 11 digit
 * numbers with the fixed `014014` prefix followed by a five digit sequence
 * (`01401400001` … `01401499999`). Identifiers are stored as text so leading
 * zeros survive, and they are unique across every role.
 */
export const ACADEMIC_LOGIN_ID_PATTERN = /^[0-9]{10,11}$/;

export const ADMIN_LOGIN_ID_PREFIX = '014014';

/**
 * The five-digit sequence is constrained to 00001–99999: `01401400000` is not
 * a valid identity, so it is excluded here and by the database constraint
 * added in migration 003. Every other five-digit value is in range.
 */
export const ADMIN_LOGIN_ID_PATTERN = /^014014(?!00000)[0-9]{5}$/;

export const ADMIN_LOGIN_ID_SEQUENCE_MIN = 1;

export const ADMIN_LOGIN_ID_SEQUENCE_MAX = 99999;

/**
 * Advisory lock key used to serialise Admin identity allocation inside a
 * transaction. Distinct from the migration advisory lock (812_345_678).
 */
export const ADMIN_LOGIN_ID_LOCK_ID = 812_345_679;

export const ADMIN_ID_EXHAUSTED = 'ADMIN_ID_EXHAUSTED';

export const ACADEMIC_LOGIN_ID_MESSAGE =
  'NIM/NIDN harus berupa 10–11 digit angka.';

export const ADMIN_LOGIN_ID_MESSAGE =
  'ID Admin harus berupa 11 digit angka dengan awalan 014014 dan nomor urut 00001–99999.';

/** Legacy `admin` username is retired; kept only so old rows/tests are explicit. */
export const LEGACY_ADMIN_LOGIN_ID = 'admin';

export function isAcademicLoginId(value: string): boolean {
  return ACADEMIC_LOGIN_ID_PATTERN.test(value.trim());
}

export function isAdminLoginId(value: string): boolean {
  return ADMIN_LOGIN_ID_PATTERN.test(value.trim());
}

export function isLegacyAdminLoginId(value: string): boolean {
  return value.trim().toLowerCase() === LEGACY_ADMIN_LOGIN_ID;
}

export function isValidLoginIdForRole(role: UserRole, value: string): boolean {
  return role === 'ADMIN' ? isAdminLoginId(value) : isAcademicLoginId(value);
}

/** Numeric sequence encoded in an Admin login id, or `null` when not an Admin id. */
export function adminLoginIdSequence(value: string): number | null {
  if (!isAdminLoginId(value)) return null;
  return Number(value.trim().slice(ADMIN_LOGIN_ID_PREFIX.length));
}

export function formatAdminLoginId(sequence: number): string {
  if (
    !Number.isInteger(sequence) ||
    sequence < ADMIN_LOGIN_ID_SEQUENCE_MIN ||
    sequence > ADMIN_LOGIN_ID_SEQUENCE_MAX
  ) {
    throw new AppError(
      ADMIN_ID_EXHAUSTED,
      409,
      'Nomor urut ID Admin berada di luar rentang 00001–99999.',
    );
  }
  return ADMIN_LOGIN_ID_PREFIX + String(sequence).padStart(5, '0');
}

/**
 * Return the lowest unused Admin identity in the 01401400001–01401499999
 * block. Used inside the caller's transaction: the transaction-scoped
 * advisory lock serialises concurrent allocators and the unique index on
 * `users.login_id` remains the final authority. Academic accounts that happen
 * to occupy an Admin-shaped identifier are skipped because the lookup checks
 * every role.
 */
export async function allocateAdminLoginId(client: PoolClient): Promise<string> {
  await client.query('SELECT pg_advisory_xact_lock($1)', [ADMIN_LOGIN_ID_LOCK_ID]);

  const result = await client.query<{ candidate: string }>(
    `
      SELECT candidate
      FROM (
        SELECT $1::text || lpad(g::text, 5, '0') AS candidate, g
        FROM generate_series($2::int, $3::int) AS g
      ) candidates
      WHERE NOT EXISTS (
        SELECT 1 FROM users u WHERE u.login_id = candidates.candidate
      )
      ORDER BY g
      LIMIT 1
    `,
    [ADMIN_LOGIN_ID_PREFIX, ADMIN_LOGIN_ID_SEQUENCE_MIN, ADMIN_LOGIN_ID_SEQUENCE_MAX],
  );

  const candidate = result.rows[0]?.candidate;
  if (!candidate) {
    throw conflict(
      ADMIN_ID_EXHAUSTED,
      'ID Admin otomatis sudah habis (01401400001–01401499999).',
    );
  }
  return candidate;
}
