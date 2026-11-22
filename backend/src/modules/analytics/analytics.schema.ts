import { z } from 'zod';

export const analyticsQuerySchema = z.object({
  category: z.enum(['VISUAL', 'AURAL', 'READ_WRITE']).optional(),
  studentId: z.string().uuid().optional(),
});

export const analyticsUuidSchema = z.string().uuid();
