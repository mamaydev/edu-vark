import { z } from 'zod';

export const roleSchema = z.enum(['STUDENT', 'LECTURER', 'ADMIN']);
export const statusSchema = z.enum(['ACTIVE', 'INACTIVE']);

export const createUserSchema = z.object({
  loginId: z.string().trim().min(1).max(50),
  fullName: z.string().trim().min(1).max(150),
  role: roleSchema,
  temporaryPassword: z.string().min(6),
});

export const updateUserSchema = z
  .object({
    fullName: z.string().trim().min(1).max(150).optional(),
    role: roleSchema.optional(),
  })
  .refine((value) => value.fullName !== undefined || value.role !== undefined, {
    message: 'Minimal satu field harus diisi.',
  });

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
