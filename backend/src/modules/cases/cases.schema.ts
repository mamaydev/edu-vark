import { z } from 'zod';

export const upsertCaseSchema = z.object({
  theory: z.string().max(10000).nullable().optional(),
  instructions: z.string().trim().min(1).max(10000),
  deadlineAt: z.coerce.date(),
});

export const submitCaseSchema = z.object({
  answerText: z.string().min(1).max(10000),
});

export const caseUuidSchema = z.string().uuid();
