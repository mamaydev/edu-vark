import { withTransaction } from '../../db/transaction';
import { conflict, notFound } from '../../lib/errors';
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
    loginId: string;
    fullName: string;
    role: UserRole;
    temporaryPassword: string;
  },
  actorId: string,
): Promise<AdminUserResponse> {
  assertStrongPassword(input.temporaryPassword);
  const passwordHash = await hashPassword(input.temporaryPassword);

  try {
    return await withTransaction(async (client) => {
      const userId = await repo.insertUser(client, {
        loginId: input.loginId,
        fullName: input.fullName,
        role: input.role,
        passwordHash,
      });

      await ensureRoleAssignment(client, userId, input.role);

      await writeAudit(client, {
        actorId,
        action: 'ACCOUNT_CREATED',
        entity: 'USER',
        entityId: userId,
        metadata: { role: input.role },
      });

      return getAdminUser(client, userId);
    });
  } catch (error) {
    if (isUniqueViolation(error)) {
      throw conflict('LOGIN_ID_TAKEN', 'NIM/NIP/NIDN sudah digunakan.');
    }
    throw error;
  }
}

export async function updateUser(
  userId: string,
  input: { fullName?: string; role?: UserRole },
  actorId: string,
): Promise<AdminUserResponse> {
  return withTransaction(async (client) => {
    const target = await repo.findAdminUserById(client, userId);
    if (!target) throw notFound('USER_NOT_FOUND', 'Akun tidak ditemukan.');

    if (target.role === 'ADMIN' && input.role && input.role !== 'ADMIN') {
      if (target.id === actorId) {
        throw conflict('LAST_ADMIN_PROTECTED', 'Tidak dapat menurunkan role admin sendiri.');
      }
      const admins = await repo.countActiveAdmins(client);
      if (admins <= 1) {
        throw conflict('LAST_ADMIN_PROTECTED', 'Admin aktif terakhir tidak dapat diturunkan.');
      }
    }

    await repo.updateUser(client, userId, input);

    if (input.role && input.role !== target.role) {
      if (input.role !== 'STUDENT') {
        await repo.deactivateEnrollments(client, userId);
      }
      if (input.role !== 'LECTURER') {
        await repo.removeTeachingAssignments(client, userId);
      }
      await ensureRoleAssignment(client, userId, input.role);
      await revokeAllSessions(client, userId);
    }

    await writeAudit(client, {
      actorId,
      action: 'ACCOUNT_UPDATED',
      entity: 'USER',
      entityId: userId,
      metadata: { fields: Object.keys(input), role: input.role ?? null },
    });

    return getAdminUser(client, userId);
  });
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
    const target = await repo.findAdminUserById(client, userId);
    if (!target) throw notFound('USER_NOT_FOUND', 'Akun tidak ditemukan.');

    if (target.id === actorId) {
      throw conflict('LAST_ADMIN_PROTECTED', 'Tidak dapat menonaktifkan akun sendiri.');
    }

    if (target.role === 'ADMIN') {
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
