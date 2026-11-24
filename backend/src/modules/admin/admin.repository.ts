import type { PoolClient } from 'pg';
import type { UserRole } from '../../types/auth';

export interface AdminUserRow {
  id: string;
  login_id: string;
  full_name: string;
  role: UserRole;
  status: 'ACTIVE' | 'INACTIVE';
  must_change_password: boolean;
  created_at: Date;
  enrollment_id: string | null;
  enrollment_status: 'ACTIVE' | 'LEFT' | null;
  class_code: string | null;
  class_name: string | null;
  teaching_assignment_id: string | null;
  teaching_class_code: string | null;
  teaching_class_name: string | null;
}

export interface EnrollmentRow {
  id: string;
  class_id: string;
  student_id: string;
  status: 'ACTIVE' | 'LEFT';
  enrolled_at: Date;
  left_at: Date | null;
}

const USER_SELECT = `
  SELECT
    u.id,
    u.login_id,
    u.full_name,
    u.role,
    u.status,
    u.must_change_password,
    u.created_at,
    e.id AS enrollment_id,
    e.status AS enrollment_status,
    ec.code AS class_code,
    ec.name AS class_name,
    ta.id AS teaching_assignment_id,
    tc.code AS teaching_class_code,
    tc.name AS teaching_class_name
  FROM users u
  LEFT JOIN LATERAL (
    SELECT e.*
    FROM enrollments e
    WHERE e.student_id = u.id AND u.role = 'STUDENT'
    ORDER BY (e.status = 'ACTIVE') DESC, e.enrolled_at DESC
    LIMIT 1
  ) e ON true
  LEFT JOIN classes ec ON ec.id = e.class_id
  LEFT JOIN LATERAL (
    SELECT ta.*
    FROM teaching_assignments ta
    WHERE ta.lecturer_id = u.id AND u.role = 'LECTURER'
    ORDER BY ta.id
    LIMIT 1
  ) ta ON true
  LEFT JOIN classes tc ON tc.id = ta.class_id
`;

export async function listUsers(
  client: PoolClient,
  filters: { q?: string; role?: UserRole; status?: 'ACTIVE' | 'INACTIVE' },
  page: number,
  pageSize: number,
): Promise<{ rows: AdminUserRow[]; total: number }> {
  const q = filters.q ?? null;
  const role = filters.role ?? null;
  const status = filters.status ?? null;
  const offset = (page - 1) * pageSize;

  const rowsResult = await client.query<AdminUserRow>(
    `
      ${USER_SELECT}
      WHERE ($1::text IS NULL OR u.login_id ILIKE '%' || $1 || '%' OR u.full_name ILIKE '%' || $1 || '%')
        AND ($2::text IS NULL OR u.role = $2)
        AND ($3::text IS NULL OR u.status = $3)
      ORDER BY u.created_at DESC, u.id ASC
      LIMIT $4 OFFSET $5
    `,
    [q, role, status, pageSize, offset],
  );

  const countResult = await client.query<{ count: string }>(
    `
      SELECT count(*)::text AS count
      FROM users u
      WHERE ($1::text IS NULL OR u.login_id ILIKE '%' || $1 || '%' OR u.full_name ILIKE '%' || $1 || '%')
        AND ($2::text IS NULL OR u.role = $2)
        AND ($3::text IS NULL OR u.status = $3)
    `,
    [q, role, status],
  );

  return {
    rows: rowsResult.rows,
    total: Number(countResult.rows[0]?.count ?? '0'),
  };
}

export async function findAdminUserById(
  client: PoolClient,
  userId: string,
): Promise<AdminUserRow | null> {
  const result = await client.query<AdminUserRow>(
    `${USER_SELECT} WHERE u.id = $1`,
    [userId],
  );
  return result.rows[0] ?? null;
}

/**
 * Read the target row under a row lock. Mutation decisions (role transition,
 * last-Admin protection, ID boundary) must be made from this locked snapshot,
 * never from an earlier unlocked read.
 */
export async function findAdminUserByIdForUpdate(
  client: PoolClient,
  userId: string,
): Promise<AdminUserRow | null> {
  const result = await client.query<AdminUserRow>(
    `${USER_SELECT} WHERE u.id = $1 FOR UPDATE OF u`,
    [userId],
  );
  return result.rows[0] ?? null;
}

export async function insertUser(
  client: PoolClient,
  input: {
    loginId: string;
    fullName: string;
    role: UserRole;
    passwordHash: string;
  },
): Promise<string> {
  const result = await client.query<{ id: string }>(
    `
      INSERT INTO users (login_id, password_hash, full_name, role, must_change_password)
      VALUES ($1, $2, $3, $4, true)
      RETURNING id
    `,
    [input.loginId, input.passwordHash, input.fullName, input.role],
  );
  const id = result.rows[0]?.id;
  if (!id) throw new Error('Failed to create user');
  return id;
}

export async function updateUser(
  client: PoolClient,
  userId: string,
  input: { fullName?: string; role?: UserRole; loginId?: string },
): Promise<void> {
  await client.query(
    `
      UPDATE users
      SET full_name = COALESCE($2, full_name),
          role = COALESCE($3, role),
          login_id = COALESCE($4, login_id),
          updated_at = now()
      WHERE id = $1
    `,
    [userId, input.fullName ?? null, input.role ?? null, input.loginId ?? null],
  );
}

export async function updatePassword(
  client: PoolClient,
  userId: string,
  passwordHash: string,
): Promise<void> {
  await client.query(
    `
      UPDATE users
      SET password_hash = $2, must_change_password = true, updated_at = now()
      WHERE id = $1
    `,
    [userId, passwordHash],
  );
}

export async function updateStatus(
  client: PoolClient,
  userId: string,
  status: 'ACTIVE' | 'INACTIVE',
): Promise<void> {
  await client.query(
    'UPDATE users SET status = $2, updated_at = now() WHERE id = $1',
    [userId, status],
  );
}

export async function countActiveAdmins(client: PoolClient): Promise<number> {
  const result = await client.query<{ count: string }>(
    "SELECT count(*)::text AS count FROM users WHERE role = 'ADMIN' AND status = 'ACTIVE'",
  );
  return Number(result.rows[0]?.count ?? '0');
}

/**
 * Lock every active Admin row so concurrent demotions/deactivations cannot
 * both observe "more than one active Admin" and remove the last one.
 */
export async function lockActiveAdmins(client: PoolClient): Promise<void> {
  await client.query(
    "SELECT id FROM users WHERE role = 'ADMIN' AND status = 'ACTIVE' ORDER BY id FOR UPDATE",
  );
}

export async function isLoginIdTaken(client: PoolClient, loginId: string): Promise<boolean> {
  const result = await client.query<{ taken: boolean }>(
    'SELECT EXISTS (SELECT 1 FROM users WHERE login_id = $1) AS taken',
    [loginId],
  );
  return result.rows[0]?.taken === true;
}

export async function findActiveClassId(client: PoolClient): Promise<string | null> {
  const result = await client.query<{ id: string }>(
    'SELECT id FROM classes WHERE is_active = true LIMIT 1',
  );
  return result.rows[0]?.id ?? null;
}

export async function insertEnrollment(
  client: PoolClient,
  classId: string,
  studentId: string,
): Promise<string> {
  const result = await client.query<{ id: string }>(
    `
      INSERT INTO enrollments (class_id, student_id, status)
      VALUES ($1, $2, 'ACTIVE')
      ON CONFLICT (class_id, student_id)
      DO UPDATE SET status = 'ACTIVE', left_at = NULL
      RETURNING id
    `,
    [classId, studentId],
  );
  const id = result.rows[0]?.id;
  if (!id) throw new Error('Failed to create enrollment');
  return id;
}

export async function insertTeachingAssignment(
  client: PoolClient,
  classId: string,
  lecturerId: string,
): Promise<string> {
  const result = await client.query<{ id: string }>(
    `
      INSERT INTO teaching_assignments (class_id, lecturer_id)
      VALUES ($1, $2)
      ON CONFLICT (class_id, lecturer_id) DO NOTHING
      RETURNING id
    `,
    [classId, lecturerId],
  );
  return result.rows[0]?.id ?? '';
}

export async function deactivateEnrollments(
  client: PoolClient,
  userId: string,
): Promise<void> {
  await client.query(
    `
      UPDATE enrollments
      SET status = 'LEFT', left_at = now()
      WHERE student_id = $1 AND status = 'ACTIVE'
    `,
    [userId],
  );
}

export async function removeTeachingAssignments(
  client: PoolClient,
  userId: string,
): Promise<void> {
  await client.query('DELETE FROM teaching_assignments WHERE lecturer_id = $1', [userId]);
}

export async function findEnrollmentById(
  client: PoolClient,
  enrollmentId: string,
): Promise<EnrollmentRow | null> {
  const result = await client.query<EnrollmentRow>(
    `
      SELECT id, class_id, student_id, status, enrolled_at, left_at
      FROM enrollments
      WHERE id = $1
    `,
    [enrollmentId],
  );
  return result.rows[0] ?? null;
}

export async function updateEnrollment(
  client: PoolClient,
  enrollmentId: string,
  status: 'ACTIVE' | 'LEFT',
  leftAt: Date | null,
): Promise<void> {
  await client.query(
    'UPDATE enrollments SET status = $2, left_at = $3 WHERE id = $1',
    [enrollmentId, status, leftAt],
  );
}
