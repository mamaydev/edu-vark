import { z } from 'zod';

export const createAssessmentSchema = z.object({
  score: z.number(),
  comment: z.string().nullable().optional(),
});

export const assessmentUuidSchema = z.string().uuid();
