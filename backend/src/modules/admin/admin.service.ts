import { withTransaction } from '../../db/transaction';
import { conflict, notFound, unprocessable } from '../../lib/errors';
import {
  ACADEMIC_LOGIN_ID_MESSAGE,
  allocateAdminLoginId,
  isAcademicLoginId,
} from '../../lib/login-id';
import { assertStrongPassword, hashPassword } from '../../lib/password';
import { writeAudit } from '../../lib/audit';
import { revokeAllSessions } from '../auth/auth.repository';
import type { UserRole } from '../../types/auth';
import * as repo from './admin.repository';

export interface AdminUserResponse {
  id: string;
  loginId: string;
  fullName: string;
  role: UserRole;
  status: 'ACTIVE' | 'INACTIVE';
  mustChangePassword: boolean;
  createdAt: string;
  enrollmentId: string | null;
  enrollmentStatus: 'ACTIVE' | 'LEFT' | null;
  classCode: string | null;
  className: string | null;
  teachingAssignmentId: string | null;
  teachingClassCode: string | null;
  teachingClassName: string | null;
}

function toAdminUser(row: repo.AdminUserRow): AdminUserResponse {
  return {
    id: row.id,
    loginId: row.login_id,
    fullName: row.full_name,
    role: row.role,
    status: row.status,
    mustChangePassword: row.must_change_password,
    createdAt: row.created_at.toISOString(),
    enrollmentId: row.enrollment_id,
    enrollmentStatus: row.enrollment_status,
    classCode: row.class_code,
    className: row.class_name,
    teachingAssignmentId: row.teaching_assignment_id,
    teachingClassCode: row.teaching_class_code,
    teachingClassName: row.teaching_class_name,
  };
}

function isUniqueViolation(error: unknown): boolean {
  return (
    typeof error === 'object' &&
    error !== null &&
    'code' in error &&
    (error as { code?: string }).code === '23505'
  );
}

function isLoginIdUniqueViolation(error: unknown): boolean {
  return (
    isUniqueViolation(error) &&
    (error as { constraint?: string }).constraint === 'ux_users_login'
  );
}

/** Number of times an automatic Admin identity allocation may be retried. */
const ADMIN_ALLOCATION_ATTEMPTS = 10;

type TxClient = Parameters<typeof repo.findActiveClassId>[0];

async function requireActiveClassId(
  client: Parameters<typeof repo.findActiveClassId>[0],
): Promise<string> {
  const classId = await repo.findActiveClassId(client);
  if (!classId) {
    throw conflict(
      'NO_ACTIVE_CLASS',
      'Belum ada kelas aktif. Buat/seed kelas terlebih dahulu.',
    );
  }
  return classId;
}

async function ensureRoleAssignment(
  client: Parameters<typeof repo.findActiveClassId>[0],
  userId: string,
  role: UserRole,
): Promise<void> {
  if (role === 'STUDENT') {
    const classId = await requireActiveClassId(client);
    await repo.insertEnrollment(client, classId, userId);
  } else if (role === 'LECTURER') {
    const classId = await requireActiveClassId(client);
    await repo.insertTeachingAssignment(client, classId, userId);
  }
}

async function getAdminUser(
  client: Parameters<typeof repo.findAdminUserById>[0],
  userId: string,
): Promise<AdminUserResponse> {
  const row = await repo.findAdminUserById(client, userId);
  if (!row) throw notFound('USER_NOT_FOUND', 'Akun tidak ditemukan.');
  return toAdminUser(row);
}

export async function listUsers(input: {
  q?: string;
  role?: UserRole;
  status?: 'ACTIVE' | 'INACTIVE';
  page: number;
  pageSize: number;
}): Promise<{ items: AdminUserResponse[]; total: number; page: number; pageSize: number }> {
  return withTransaction(async (client) => {
    const { rows, total } = await repo.listUsers(client, input, input.page, input.pageSize);
    return {
      items: rows.map(toAdminUser),
      total,
      page: input.page,
      pageSize: input.pageSize,
    };
  });
}

export async function getUser(userId: string): Promise<AdminUserResponse> {
  return withTransaction((client) => getAdminUser(client, userId));
}

export async function createUser(
  input: {
    loginId?: string;
    fullName: string;
    role: UserRole;
    temporaryPassword: string;
  },
  actorId: string,
): Promise<AdminUserResponse> {
  assertStrongPassword(input.temporaryPassword);
  const passwordHash = await hashPassword(input.temporaryPassword);

  if (input.role === 'ADMIN') {
    // Admin identities are always system-issued: any caller-supplied loginId
    // is ignored. Retry if a concurrent academic insert wins the same number.
    for (let attempt = 1; attempt <= ADMIN_ALLOCATION_ATTEMPTS; attempt += 1) {
      try {
        return await withTransaction(async (client) => {
          const loginId = await allocateAdminLoginId(client);
          return insertAccount(client, {
            loginId,
            fullName: input.fullName,
            role: 'ADMIN',
            passwordHash,
            actorId,
          });
        });
      } catch (error) {
        if (isLoginIdUniqueViolation(error) && attempt < ADMIN_ALLOCATION_ATTEMPTS) {
          continue;
        }
        if (isLoginIdUniqueViolation(error)) {
          throw conflict(
            'ADMIN_ID_ALLOCATION_CONFLICT',
            'ID Admin otomatis bentrok. Ulangi permintaan.',
          );
        }
        throw error;
      }
    }
    throw conflict(
      'ADMIN_ID_ALLOCATION_CONFLICT',
      'ID Admin otomatis bentrok. Ulangi permintaan.',
    );
  }

  const loginId = input.loginId?.trim() ?? '';
  if (!isAcademicLoginId(loginId)) {
    throw unprocessable('INVALID_LOGIN_ID', ACADEMIC_LOGIN_ID_MESSAGE);
  }

  try {
    return await withTransaction((client) =>
      insertAccount(client, {
        loginId,
        fullName: input.fullName,
        role: input.role,
        passwordHash,
        actorId,
      }),
    );
  } catch (error) {
    if (isUniqueViolation(error)) {
      throw conflict('LOGIN_ID_TAKEN', 'NIM/NIP/NIDN sudah digunakan.');
    }
    throw error;
  }
}

async function insertAccount(
  client: TxClient,
  input: {
    loginId: string;
    fullName: string;
    role: UserRole;
    passwordHash: string;
    actorId: string;
  },
): Promise<AdminUserResponse> {
  const userId = await repo.insertUser(client, {
    loginId: input.loginId,
    fullName: input.fullName,
    role: input.role,
    passwordHash: input.passwordHash,
  });

  await ensureRoleAssignment(client, userId, input.role);

  await writeAudit(client, {
    actorId: input.actorId,
    action: 'ACCOUNT_CREATED',
    entity: 'USER',
    entityId: userId,
    metadata: { role: input.role },
  });

  return getAdminUser(client, userId);
}

async function resolveAcademicTargetId(
  client: TxClient,
  rawLoginId: string | undefined,
): Promise<string> {
  const loginId = rawLoginId?.trim() ?? '';
  if (!isAcademicLoginId(loginId)) {
    throw unprocessable('INVALID_LOGIN_ID', ACADEMIC_LOGIN_ID_MESSAGE);
  }
  if (await repo.isLoginIdTaken(client, loginId)) {
    throw conflict('LOGIN_ID_TAKEN', 'NIM/NIP/NIDN tujuan sudah digunakan akun lain.');
  }
  return loginId;
}

export async function updateUser(
  userId: string,
  input: { fullName?: string; role?: UserRole; loginId?: string },
  actorId: string,
): Promise<AdminUserResponse> {
  const needsAllocationProbe = input.role === 'ADMIN';

  for (let attempt = 1; attempt <= ADMIN_ALLOCATION_ATTEMPTS; attempt += 1) {
    try {
      return await withTransaction(async (client) => {
        // Lock order is always the same: active Admin set first (ordered by
        // id), then the target row. Concurrent mutations then serialise on
        // one lock set, and every role/status decision below reads the
        // locked snapshot rather than a stale pre-lock read.
        await repo.lockActiveAdmins(client);
        const target = await repo.findAdminUserByIdForUpdate(client, userId);
        if (!target) throw notFound('USER_NOT_FOUND', 'Akun tidak ditemukan.');

        const nextRole = input.role ?? target.role;
        const crossesAdmin =
          (target.role === 'ADMIN') !== (nextRole === 'ADMIN');

        // Guard the last active Admin before allocating or mutating anything.
        if (target.role === 'ADMIN' && nextRole !== 'ADMIN') {
          if (target.id === actorId) {
            throw conflict(
              'LAST_ADMIN_PROTECTED',
              'Tidak dapat menurunkan role admin sendiri.',
            );
          }
          const admins = await repo.countActiveAdmins(client);
          if (admins <= 1) {
            throw conflict(
              'LAST_ADMIN_PROTECTED',
              'Admin aktif terakhir tidak dapat diturunkan.',
            );
          }
        }

        let nextLoginId: string | undefined;
        if (crossesAdmin) {
          nextLoginId =
            nextRole === 'ADMIN'
              ? await allocateAdminLoginId(client)
              : await resolveAcademicTargetId(client, input.loginId);
        }
        // Academic-to-academic role changes keep the owner's NIM/NIDN and
        // ignore any supplied loginId (FR-034 only mandates role-boundary
        // identity changes).

        await repo.updateUser(client, userId, {
          fullName: input.fullName,
          role: input.role,
          loginId: nextLoginId,
        });

        if (nextRole !== target.role) {
          if (nextRole !== 'STUDENT') {
            // Preserve academic history: enrollment rows become LEFT instead
            // of being deleted when the account stops being a student.
            await repo.deactivateEnrollments(client, userId);
          }
          if (nextRole !== 'LECTURER') {
            // Teaching assignments are access grants; the aggregate itself is
            // not history and is removed when the lecturer loses that role.
            await repo.removeTeachingAssignments(client, userId);
          }
          await ensureRoleAssignment(client, userId, nextRole);
        }

        if (nextRole !== target.role || nextLoginId !== undefined) {
          await revokeAllSessions(client, userId);
        }

        await writeAudit(client, {
          actorId,
          action: 'ACCOUNT_UPDATED',
          entity: 'USER',
          entityId: userId,
          metadata: {
            fields: Object.keys(input),
            previousRole: target.role,
            role: nextRole,
            loginIdChanged: nextLoginId !== undefined,
          },
        });

        return getAdminUser(client, userId);
      });
    } catch (error) {
      if (
        needsAllocationProbe &&
        isLoginIdUniqueViolation(error) &&
        attempt < ADMIN_ALLOCATION_ATTEMPTS
      ) {
        continue;
      }
      if (isUniqueViolation(error)) {
        throw conflict('LOGIN_ID_TAKEN', 'NIM/NIP/NIDN atau ID login sudah digunakan.');
      }
      throw error;
    }
  }

  throw conflict(
    'ADMIN_ID_ALLOCATION_CONFLICT',
    'ID Admin otomatis bentrok. Ulangi permintaan.',
  );
}

export async function resetPassword(
  userId: string,
  temporaryPassword: string,
  actorId: string,
): Promise<void> {
  assertStrongPassword(temporaryPassword);
  const passwordHash = await hashPassword(temporaryPassword);

  await withTransaction(async (client) => {
    const target = await repo.findAdminUserById(client, userId);
    if (!target) throw notFound('USER_NOT_FOUND', 'Akun tidak ditemukan.');

    await repo.updatePassword(client, userId, passwordHash);
    await revokeAllSessions(client, userId);
    await writeAudit(client, {
      actorId,
      action: 'PASSWORD_RESET',
      entity: 'USER',
      entityId: userId,
      metadata: {},
    });
  });
}

export async function deactivateUser(userId: string, actorId: string): Promise<void> {
  await withTransaction(async (client) => {
    // Same lock order as updateUser: active Admin set first, target row after.
    await repo.lockActiveAdmins(client);
    const target = await repo.findAdminUserByIdForUpdate(client, userId);
    if (!target) throw notFound('USER_NOT_FOUND', 'Akun tidak ditemukan.');

    if (target.id === actorId) {
      throw conflict('LAST_ADMIN_PROTECTED', 'Tidak dapat menonaktifkan akun sendiri.');
    }

    if (target.role === 'ADMIN' && target.status === 'ACTIVE') {
      // Serialise with demotions: read the active Admin set under lock before
      // deciding, otherwise two concurrent deactivations could each observe
      // two active Admins and together remove both.
      const admins = await repo.countActiveAdmins(client);
      if (admins <= 1) {
        throw conflict('LAST_ADMIN_PROTECTED', 'Admin aktif terakhir tidak dapat dinonaktifkan.');
      }
    }

    await repo.updateStatus(client, userId, 'INACTIVE');
    await revokeAllSessions(client, userId);
    await writeAudit(client, {
      actorId,
      action: 'ACCOUNT_DEACTIVATED',
      entity: 'USER',
      entityId: userId,
      metadata: {},
    });
  });
}

export async function leaveEnrollment(
  enrollmentId: string,
  input: { status: 'LEFT'; leftAt?: Date } | { status: 'ACTIVE' },
  actorId: string,
): Promise<{ id: string; status: 'ACTIVE' | 'LEFT'; leftAt: string | null }> {
  return withTransaction(async (client) => {
    const enrollment = await repo.findEnrollmentById(client, enrollmentId);
    if (!enrollment) throw notFound('ENROLLMENT_NOT_FOUND', 'Enrollment tidak ditemukan.');

    const leftAt = input.status === 'LEFT' ? (input.leftAt ?? new Date()) : null;
    await repo.updateEnrollment(client, enrollmentId, input.status, leftAt);
    await writeAudit(client, {
      actorId,
      action: input.status === 'LEFT' ? 'ENROLLMENT_LEFT' : 'ENROLLMENT_REACTIVATED',
      entity: 'ENROLLMENT',
      entityId: enrollmentId,
      metadata: { studentId: enrollment.student_id, classId: enrollment.class_id },
    });

    return {
      id: enrollmentId,
      status: input.status,
      leftAt: leftAt ? leftAt.toISOString() : null,
    };
  });
}
