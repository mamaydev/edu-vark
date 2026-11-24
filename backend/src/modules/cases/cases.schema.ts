import { z } from 'zod';

export const upsertCaseSchema = z.object({
  theory: z.string().max(10000).nullable().optional(),
  instructions: z.string().trim().min(1).max(10000),
  deadlineAt: z.coerce.date(),
});

// C1-04: panjang jawaban dihitung dalam Unicode code point setelah trim.
// Batas panjang TIDAK ditegakkan di zod (batas `max` zod menghitung unit
// UTF-16 sehingga emoji dihitung dobel), melainkan di cases.service setelah
// trim memakai `[...trimmed].length` — sumber kebenaran yang sama dengan
// PostgreSQL `char_length` dan counter Android.
export const submitCaseSchema = z.object({
  answerText: z.string(),
});

export const caseUuidSchema = z.string().uuid();
