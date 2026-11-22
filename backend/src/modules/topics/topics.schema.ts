import { z } from 'zod';

export const varkSchema = z.enum(['VISUAL', 'AURAL', 'READ_WRITE', 'KINESTHETIC']);

export const createTopicSchema = z.object({
  title: z.string().trim().min(1).max(200),
  description: z.string().max(5000).optional(),
  sortOrder: z.coerce.number().int().min(0).optional(),
});

export const updateTopicSchema = z
  .object({
    title: z.string().trim().min(1).max(200).optional(),
    description: z.string().max(5000).nullable().optional(),
  })
  .refine((value) => value.title !== undefined || value.description !== undefined, {
    message: 'Minimal satu field harus diisi.',
  });

export const reorderTopicsSchema = z.object({
  topicIds: z
    .array(z.string().uuid())
    .min(1)
    .refine((ids) => new Set(ids).size === ids.length, {
      message: 'topicIds tidak boleh duplikat.',
    }),
});

export const listQuerySchema = z.object({
  page: z.coerce.number().int().positive().default(1),
  pageSize: z.coerce.number().int().positive().max(100).default(20),
});

export const linkMaterialSchema = z.object({
  title: z.string().trim().min(1).max(200),
  vark: varkSchema,
  url: z
    .string()
    .url()
    .refine((value) => value.startsWith('https://'), {
      message: 'URL materi harus menggunakan HTTPS.',
    }),
});

export const fileMaterialFieldsSchema = z.object({
  title: z.string().trim().min(1).max(200),
  vark: varkSchema,
});

export const updateMaterialSchema = z
  .object({
    title: z.string().trim().min(1).max(200).optional(),
    vark: varkSchema.optional(),
  })
  .refine((value) => value.title !== undefined || value.vark !== undefined, {
    message: 'Minimal satu field harus diisi.',
  });

export const uuidSchema = z.string().uuid();
