import { z } from 'zod';

export const loginSchema = z.object({
  loginId: z.string().trim().min(1).max(50),
  password: z.string().min(1),
});

export const refreshSchema = z.object({
  refreshToken: z.string().min(20),
});

export const changePasswordSchema = z
  .object({
    currentPassword: z.string().min(1),
    newPassword: z.string().min(6).max(200),
    confirmPassword: z.string().min(6).max(200),
  })
  .refine((value) => value.newPassword === value.confirmPassword, {
    message: 'Konfirmasi password baru tidak sama.',
    path: ['confirmPassword'],
  });

export const varkSchema = z.object({
  vark: z.enum(['VISUAL', 'AURAL', 'READ_WRITE', 'KINESTHETIC']),
});
