import 'dotenv/config';
import { z } from 'zod';

const emptyToUndefined = (value: unknown): unknown =>
  value === '' ? undefined : value;

const optionalString = z.preprocess(emptyToUndefined, z.string().optional());
const optionalUrl = z.preprocess(emptyToUndefined, z.string().url().optional());
const optionalSecret = z.preprocess(
  emptyToUndefined,
  z.string().min(32).optional(),
);

const EnvSchema = z
  .object({
  NODE_ENV: z.enum(['development', 'test', 'production']).default('development'),
  PORT: z.coerce.number().int().positive().default(3000),
  DATABASE_URL: z.string().min(1),
  JWT_ACCESS_SECRET: optionalSecret,
  JWT_REFRESH_SECRET: optionalSecret,
  ACCESS_TOKEN_TTL: z.string().default('15m'),
  REFRESH_TOKEN_TTL: z.string().default('12h'),
  SESSION_IDLE_TTL: z.string().default('8h'),
  SESSION_ABSOLUTE_TTL: z.string().default('12h'),
  R2_ENDPOINT: optionalUrl,
  R2_ACCESS_KEY_ID: optionalString,
  R2_SECRET_ACCESS_KEY: optionalString,
  R2_BUCKET: optionalString,
  R2_SIGNED_URL_TTL_SECONDS: z.coerce.number().int().positive().default(300),
  PUBLIC_API_BASE_URL: optionalUrl,
  CORS_ORIGINS: optionalString,
  LOCAL_STORAGE_DIR: optionalString,
  MAX_UPLOAD_BYTES: z.coerce.number().int().positive().max(104857600).default(104857600),
  // Worker penghapusan objek penyimpanan. Interval 0 mematikan worker (dipakai
  // test); percobaan pertama tetap dijalankan langsung setelah commit.
  STORAGE_DELETION_INTERVAL_MS: z.coerce.number().int().min(0).default(60_000),
  STORAGE_DELETION_MAX_ATTEMPTS: z.coerce.number().int().positive().default(10),
  // Lease niat Ganti File. Harus lebih panjang dari durasi unggah normal agar
  // rekonsiliasi tidak menyerobot unggahan yang masih berjalan; setelah proses
  // mati, lease habis dan worker menyelesaikan berdasarkan state database.
  FILE_REPLACEMENT_LEASE_MS: z.coerce.number().int().positive().default(600_000),
  CLIENT_COMPATIBILITY_ENFORCE: z.enum(['0', '1']).optional(),
  CLIENT_MIN_VERSION: z.coerce.number().int().positive().default(2),
    LOG_LEVEL: z.enum(['fatal', 'error', 'warn', 'info', 'debug', 'trace', 'silent']).default('info'),
  })
  .superRefine((env, ctx) => {
    if (env.NODE_ENV !== 'production') return;

    if (!env.JWT_ACCESS_SECRET) {
      ctx.addIssue({
        code: z.ZodIssueCode.custom,
        path: ['JWT_ACCESS_SECRET'],
        message: 'wajib diisi pada NODE_ENV=production',
      });
    }
    if (!env.JWT_REFRESH_SECRET) {
      ctx.addIssue({
        code: z.ZodIssueCode.custom,
        path: ['JWT_REFRESH_SECRET'],
        message: 'wajib diisi pada NODE_ENV=production',
      });
    }
    if (
      env.JWT_ACCESS_SECRET &&
      env.JWT_REFRESH_SECRET &&
      env.JWT_ACCESS_SECRET === env.JWT_REFRESH_SECRET
    ) {
      ctx.addIssue({
        code: z.ZodIssueCode.custom,
        path: ['JWT_REFRESH_SECRET'],
        message: 'harus berbeda dari JWT_ACCESS_SECRET',
      });
    }

    const r2Values = [
      env.R2_ENDPOINT,
      env.R2_ACCESS_KEY_ID,
      env.R2_SECRET_ACCESS_KEY,
      env.R2_BUCKET,
    ];
    const filled = r2Values.filter(Boolean).length;
    if (filled > 0 && filled < r2Values.length) {
      ctx.addIssue({
        code: z.ZodIssueCode.custom,
        path: ['R2_BUCKET'],
        message: 'konfigurasi R2 harus lengkap atau dikosongkan seluruhnya',
      });
    }
  });

export type Env = z.infer<typeof EnvSchema>;

export function parseEnv(input: NodeJS.ProcessEnv): Env {
  const parsed = EnvSchema.safeParse(input);
  if (!parsed.success) {
    throw new Error(
      'Invalid environment: ' + JSON.stringify(parsed.error.flatten().fieldErrors),
    );
  }
  return parsed.data;
}

let cached: Env | null = null;

export function getEnv(): Env {
  cached ??= parseEnv(process.env);
  return cached;
}
