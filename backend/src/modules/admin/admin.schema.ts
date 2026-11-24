import { z } from 'zod';
import {
  ACADEMIC_LOGIN_ID_MESSAGE,
  isAcademicLoginId,
} from '../../lib/login-id';

export const roleSchema = z.enum(['STUDENT', 'LECTURER', 'ADMIN']);
export const statusSchema = z.enum(['ACTIVE', 'INACTIVE']);

/**
 * Admin identities are system-issued: the caller never supplies one. Academic
 * accounts (students/lecturers) still require a 10–11 digit NIM/NIDN.
 */
export const createUserSchema = z
  .object({
    loginId: z.string().trim().max(50).optional(),
    fullName: z.string().trim().min(1).max(150),
    role: roleSchema,
    temporaryPassword: z.string().min(6),
  })
  .superRefine((value, context) => {
    if (value.role === 'ADMIN') {
      // Any caller-supplied loginId is ignored; the service allocates one.
      return;
    }
    if (!value.loginId || !isAcademicLoginId(value.loginId)) {
      context.addIssue({
        code: 'custom',
        path: ['loginId'],
        message: ACADEMIC_LOGIN_ID_MESSAGE,
      });
    }
  });

export const updateUserSchema = z
  .object({
    fullName: z.string().trim().min(1).max(150).optional(),
    role: roleSchema.optional(),
    // Required when moving from ADMIN to an academic role; ignored when the
    // target role is ADMIN because that identity is allocated automatically.
    loginId: z.string().trim().max(50).optional(),
  })
  .refine(
    (value) =>
      value.fullName !== undefined ||
      value.role !== undefined ||
      value.loginId !== undefined,
    {
      message: 'Minimal satu field harus diisi.',
    },
  );

export const resetPasswordSchema = z.object({
  temporaryPassword: z.string().min(6),
});

export const listUsersQuerySchema = z.object({
  q: z.string().trim().max(100).optional(),
  role: roleSchema.optional(),
  status: statusSchema.optional(),
  page: z.coerce.number().int().positive().default(1),
  pageSize: z.coerce.number().int().positive().max(100).default(20),
});

export const leaveEnrollmentSchema = z.discriminatedUnion('status', [
  z.object({
    status: z.literal('LEFT'),
    leftAt: z.coerce.date().optional(),
  }),
  z.object({
    status: z.literal('ACTIVE'),
  }),
]);

export const uuidSchema = z.string().uuid();
