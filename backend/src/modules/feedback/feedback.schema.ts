import { z } from 'zod';

export const saveFeedbackSchema = z.object({
  value: z.coerce.number().int().min(0).max(100),
});

export const feedbackUuidSchema = z.string().uuid();
