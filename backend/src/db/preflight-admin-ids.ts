import { createHash } from 'node:crypto';
import { closePool, getPool } from './client';
import {
  ADMIN_LOGIN_ID_PREFIX,
  ADMIN_LOGIN_ID_SEQUENCE_MAX,
  ADMIN_LOGIN_ID_SEQUENCE_MIN,
} from '../lib/login-id';
import { logger } from '../lib/logger';

/**
 * Read-only preflight for the Admin identity migrations (002/003). The valid
 * range is 01401400001–01401499999: the out-of-range 01401400000 is treated as
 * not on pattern everywhere (it is rejected by the 003 constraint).
 *
 * It reports collision and capacity information without exposing personal
 * data: account names, NIM/NIDN, and full login identifiers are never printed.
 * Admin-shaped identifiers are reduced to the fixed `014014` prefix plus a
 * short SHA-256 fingerprint so distinct examples remain distinguishable.
 */
interface CountsRow {
  admin_total: string;
  admin_on_pattern: string;
  admin_legacy: string;
  academic_collisions: string;
  pattern_used: string;
}

function maskLoginId(loginId: string): string {
  const fingerprint = createHash('sha256').update(loginId).digest('hex').slice(0, 6);
  return `${ADMIN_LOGIN_ID_PREFIX}-#${fingerprint}`;
}

async function main(): Promise<void> {
  const pool = getPool();
  const client = await pool.connect();

  try {
    const pattern = `^${ADMIN_LOGIN_ID_PREFIX}[0-9]{5}$`;
    const outOfRange = `${ADMIN_LOGIN_ID_PREFIX}00000`;
    const counts = await client.query<CountsRow>(
      `
        SELECT
          count(*) FILTER (WHERE role = 'ADMIN')::text AS admin_total,
          count(*) FILTER (WHERE role = 'ADMIN'
                             AND login_id ~ $1 AND login_id <> $2)::text
            AS admin_on_pattern,
          count(*) FILTER (WHERE role = 'ADMIN'
                             AND (login_id !~ $1 OR login_id = $2))::text
            AS admin_legacy,
          count(*) FILTER (WHERE role <> 'ADMIN'
                             AND login_id ~ $1 AND login_id <> $2)::text
            AS academic_collisions,
          count(*) FILTER (WHERE login_id ~ $1 AND login_id <> $2)::text
            AS pattern_used
        FROM users
      `,
      [pattern, outOfRange],
    );
    const row = counts.rows[0];

    const collisionSamples = await client.query<{ login_id: string }>(
      `
        SELECT login_id FROM users
        WHERE role <> 'ADMIN' AND login_id ~ $1 AND login_id <> $2
        ORDER BY login_id
        LIMIT 5
      `,
      [pattern, outOfRange],
    );

    const legacySamples = await client.query<{ login_id: string }>(
      `
        SELECT login_id FROM users
        WHERE role = 'ADMIN' AND (login_id !~ $1 OR login_id = $2)
        ORDER BY created_at ASC, id ASC
        LIMIT 5
      `,
      [pattern, outOfRange],
    );

    const nextFree = await client.query<{ candidate: string | null }>(
      `
        SELECT candidate FROM (
          SELECT $1::text || lpad(g::text, 5, '0') AS candidate, g
          FROM generate_series($2::int, $3::int) AS g
        ) candidates
        WHERE NOT EXISTS (SELECT 1 FROM users u WHERE u.login_id = candidates.candidate)
        ORDER BY g
        LIMIT 1
      `,
      [ADMIN_LOGIN_ID_PREFIX, ADMIN_LOGIN_ID_SEQUENCE_MIN, ADMIN_LOGIN_ID_SEQUENCE_MAX],
    );

    const adminTotal = Number(row.admin_total);
    const adminOnPattern = Number(row.admin_on_pattern);
    const adminLegacy = Number(row.admin_legacy);
    const academicCollisions = Number(row.academic_collisions);
    const patternUsed = Number(row.pattern_used);
    const capacity = ADMIN_LOGIN_ID_SEQUENCE_MAX - ADMIN_LOGIN_ID_SEQUENCE_MIN + 1;
    const remaining = capacity - patternUsed;
    const nextCandidate = nextFree.rows[0]?.candidate ?? null;

    const report = {
      generatedAt: new Date().toISOString(),
      adminPattern: `${ADMIN_LOGIN_ID_PREFIX}[0-9]{5} (00001–99999)`,
      capacity,
      adminAccounts: {
        total: adminTotal,
        onPattern: adminOnPattern,
        needsBackfill: adminLegacy,
        maskedNeedsBackfillExamples: legacySamples.rows.map((r) => maskLoginId(r.login_id)),
      },
      collisions: {
        academicAccountsWithAdminShapedId: academicCollisions,
        maskedExamples: collisionSamples.rows.map((r) => maskLoginId(r.login_id)),
      },
      capacityStatus: {
        numbersUsed: patternUsed,
        numbersRemaining: remaining,
        nextAvailableCandidate: nextCandidate,
        exhausted: nextCandidate === null,
      },
      backfillFeasible: remaining >= adminLegacy,
    };

    console.log(JSON.stringify(report, null, 2));

    if (!report.backfillFeasible) {
      logger.error('Kapasitas ID Admin tidak cukup untuk backfill.');
      process.exitCode = 2;
    }
  } finally {
    client.release();
    await closePool();
  }
}

main().catch(async (error: unknown) => {
  logger.error({ err: error }, 'Preflight ID Admin gagal');
  await closePool();
  process.exit(1);
});
