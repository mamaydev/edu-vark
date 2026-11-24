import { z } from 'zod';
import { closePool } from './client';
import { logger } from '../lib/logger';
import { seedAdmin } from './seed-admin-core';

const SeedSchema = z.object({
  // Legacy variable kept so existing .env/deployments do not fail validation;
  // its value is ignored because Admin identities are system-issued (FR-036).
  ADMIN_LOGIN_ID: z.string().min(1).optional(),
  ADMIN_PASSWORD: z.string().min(6),
  ADMIN_FULL_NAME: z.string().min(1).default('Administrator'),
});

async function main(): Promise<void> {
  const parsed = SeedSchema.safeParse(process.env);
  if (!parsed.success) {
    throw new Error(
      'Invalid seed environment: ' + JSON.stringify(parsed.error.flatten().fieldErrors),
    );
  }

  const { ADMIN_PASSWORD, ADMIN_FULL_NAME } = parsed.data;
  const result = await seedAdmin({
    fullName: ADMIN_FULL_NAME,
    password: ADMIN_PASSWORD,
    legacyLoginId: parsed.data.ADMIN_LOGIN_ID,
  });

  if (result.created) {
    logger.info({ loginId: result.loginId }, 'Admin seeded; password change required');
  } else {
    logger.info({ loginId: result.loginId }, 'Admin already exists; seed skipped');
  }
}

main()
  .then(async () => {
    await closePool();
  })
  .catch(async (error: unknown) => {
    logger.error({ err: error }, 'Admin seed failed');
    await closePool();
    process.exit(1);
  });
