import { spawn, spawnSync, type ChildProcess } from 'node:child_process';
import { createHash, randomUUID } from 'node:crypto';
import fs from 'node:fs';
import net from 'node:net';
import path from 'node:path';
import { Client } from 'pg';
import request from 'supertest';
import { afterAll, beforeEach, describe, expect, it } from 'vitest';
import { createApp } from '../../src/app';
import { closePool, getPool } from '../../src/db/client';
import { seedAdmin } from '../../src/db/seed-admin-core';
import { resetLoginThrottle } from '../../src/lib/login-throttle';
import { hashPassword } from '../../src/lib/password';
import { generateRefreshToken, hashToken, signAccessToken } from '../../src/lib/tokens';
import {
  activeSessionCount,
  closeDb,
  createAdmin,
  createClass,
  createUser,
  resetDb,
  TEST_ADMIN_PASSWORD,
} from '../helpers/db';

const app = createApp();
const describeDb = process.env.DATABASE_URL ? describe : describe.skip;

const STUDENT_PASSWORD = 'StudentPass123';
const ADMIN_PASSWORD = TEST_ADMIN_PASSWORD;
const LOGIN_01401400001 = '01401400001';
const LOGIN_01401400002 = '01401400002';
const LOGIN_01401499999 = '01401499999';

function bearer(token: string): string {
  return 'Bearer ' + token;
}

function login(loginId: string, password: string) {
  return request(app).post('/api/v1/auth/login').send({ loginId, password });
}

interface LoginResult {
  accessToken: string;
  refreshToken: string;
  user: { id: string; loginId: string; role: string; status: string };
}

async function loginOk(loginId: string, password: string): Promise<LoginResult> {
  const response = await login(loginId, password).expect(200);
  return response.body.data as LoginResult;
}

function patchUser(token: string, userId: string, body: Record<string, unknown>) {
  return request(app)
    .patch('/api/v1/admin/users/' + userId)
    .set('Authorization', bearer(token))
    .send(body);
}

function deactivate(token: string, userId: string) {
  return request(app)
    .post('/api/v1/admin/users/' + userId + '/deactivate')
    .set('Authorization', bearer(token));
}

async function activeAdminCount(): Promise<number> {
  const result = await getPool().query<{ count: string }>(
    "SELECT count(*)::text AS count FROM users WHERE role = 'ADMIN' AND status = 'ACTIVE'",
  );
  return Number(result.rows[0]?.count ?? '0');
}

// ---------------------------------------------------------------------------
// Disposable database helpers for the migration 003 tests
// ---------------------------------------------------------------------------

const MIGRATION_001 = '001_init.sql';
const MIGRATION_002 = '002_admin_login_ids.sql';
const MIGRATION_003 = '003_admin_session_revoke_and_range.sql';
const MIGRATION_004 = '004_material_object_deletions.sql';
const MIGRATION_005 = '005_material_file_replacements.sql';

function databaseName(): string {
  const url = new URL(process.env.DATABASE_URL as string);
  return url.pathname.replace(/^\//, '');
}

function connectionStringFor(database: string): string {
  const url = new URL(process.env.DATABASE_URL as string);
  url.pathname = '/' + database;
  return url.toString();
}

async function withMaintenanceClient<T>(
  work: (client: Client) => Promise<T>,
): Promise<T> {
  const url = new URL(process.env.DATABASE_URL as string);
  url.pathname = '/postgres';
  const client = new Client({ connectionString: url.toString() });
  await client.connect();
  try {
    return await work(client);
  } finally {
    await client.end();
  }
}

async function recreateDatabase(name: string): Promise<void> {
  await withMaintenanceClient(async (client) => {
    await client.query(
      `SELECT pg_terminate_backend(pid) FROM pg_stat_activity
       WHERE datname = $1 AND pid <> pg_backend_pid()`,
      [name],
    );
    await client.query(`DROP DATABASE IF EXISTS ${quoteIdent(name)}`);
    await client.query(`CREATE DATABASE ${quoteIdent(name)}`);
  });
}

async function dropDatabase(name: string): Promise<void> {
  await withMaintenanceClient(async (client) => {
    await client.query(
      `SELECT pg_terminate_backend(pid) FROM pg_stat_activity
       WHERE datname = $1 AND pid <> pg_backend_pid()`,
      [name],
    );
    await client.query(`DROP DATABASE IF EXISTS ${quoteIdent(name)}`);
  });
}

function quoteIdent(value: string): string {
  return '"' + value.replace(/"/g, '""') + '"';
}

function migrationSql(file: string): string {
  return fs.readFileSync(
    path.join(process.cwd(), 'src', 'db', 'migrations', file),
    'utf8',
  );
}

function runMigrate(
  database: string,
  logLevel = 'silent',
): {
  status: number | null;
  stdout: string;
  stderr: string;
} {
  const tsx = path.join(process.cwd(), 'node_modules', '.bin', 'tsx');
  const result = spawnSync(tsx, ['src/db/migrate.ts'], {
    cwd: process.cwd(),
    env: {
      ...process.env,
      DATABASE_URL: connectionStringFor(database),
      NODE_ENV: 'test',
      LOG_LEVEL: logLevel,
    },
    encoding: 'utf8',
    timeout: 60_000,
  });
  return {
    status: result.status,
    stdout: result.stdout ?? '',
    stderr: result.stderr ?? '',
  };
}

function freePort(): Promise<number> {
  return new Promise((resolve, reject) => {
    const server = net.createServer();
    server.once('error', reject);
    server.listen(0, '127.0.0.1', () => {
      const address = server.address();
      const port = typeof address === 'object' && address ? address.port : 0;
      server.close(() => resolve(port));
    });
  });
}

async function waitForHealth(baseUrl: string, child: ChildProcess): Promise<void> {
  let stderr = '';
  child.stderr?.on('data', (chunk: Buffer) => {
    stderr += chunk.toString();
  });
  for (let attempt = 0; attempt < 80; attempt += 1) {
    if (child.exitCode !== null) {
      throw new Error('Server exited before health check: ' + stderr);
    }
    try {
      const response = await fetch(baseUrl + '/health');
      if (response.ok) return;
    } catch {
      // Server not accepting connections yet.
    }
    await new Promise((resolve) => setTimeout(resolve, 100));
  }
  throw new Error('Server did not become healthy: ' + stderr);
}

interface RangeFixture {
  classId: string;
  adminId: string;
  studentId: string;
  sessionId: string;
  auditId: string;
}

/**
 * Pre-002 world: a legacy `admin` username with an active session and an audit
 * reference, plus an academic account occupying 01401400001 (collision).
 */
async function createRangeFixture(client: Client): Promise<RangeFixture> {
  const classRow = await client.query<{ id: string }>(
    `INSERT INTO classes (code, name, semester, is_active)
     VALUES ('IBD-01', 'Implementasi Basis Data', '2026-1', true) RETURNING id`,
  );
  const classId = classRow.rows[0]!.id;

  const admin = await client.query<{ id: string }>(
    `INSERT INTO users (login_id, password_hash, full_name, role, must_change_password)
     VALUES ('admin', 'legacy-hash', 'Administrator', 'ADMIN', true) RETURNING id`,
  );
  const adminId = admin.rows[0]!.id;

  const student = await client.query<{ id: string }>(
    `INSERT INTO users (login_id, password_hash, full_name, role)
     VALUES ('01401400001', 'legacy-hash', 'Collision Student', 'STUDENT') RETURNING id`,
  );
  const studentId = student.rows[0]!.id;

  await client.query(
    `INSERT INTO enrollments (class_id, student_id) VALUES ($1, $2)`,
    [classId, studentId],
  );

  const session = await client.query<{ id: string }>(
    `INSERT INTO auth_sessions (user_id, token_hash, refresh_token_hash, expires_at)
     VALUES ($1, 'legacy-access', 'legacy-refresh', now() + interval '1 day')
     RETURNING id`,
    [adminId],
  );
  const audit = await client.query<{ id: string }>(
    `INSERT INTO audit_logs (actor_id, action, entity) VALUES ($1, 'LEGACY_SEED', 'USER') RETURNING id`,
    [adminId],
  );

  return {
    classId,
    adminId,
    studentId,
    sessionId: session.rows[0]!.id,
    auditId: audit.rows[0]!.id,
  };
}

async function identitySnapshot(client: Client): Promise<string> {
  const result = await client.query(
    `SELECT id, password_hash, full_name, role, status, must_change_password
     FROM users ORDER BY id`,
  );
  return JSON.stringify(result.rows);
}

async function sessionState(
  client: Client,
  sessionId: string,
): Promise<{ is_active: boolean; revoked_at: string | null; rows: number }> {
  const result = await client.query<{
    is_active: boolean;
    revoked_at: string | null;
  }>('SELECT is_active, revoked_at FROM auth_sessions WHERE id = $1', [sessionId]);
  const count = await client.query<{ count: string }>(
    'SELECT count(*)::text AS count FROM auth_sessions',
  );
  return {
    is_active: result.rows[0]!.is_active,
    revoked_at: result.rows[0]!.revoked_at,
    rows: Number(count.rows[0]!.count),
  };
}

describeDb('Admin identity remediation — sessions, range, locks, seed, migrations', () => {
  beforeEach(async () => {
    await resetDb();
    resetLoginThrottle();
  });

  afterAll(async () => {
    await closeDb();
  });

  // -------------------------------------------------------------------
  // 1. Admin identity range 00001–99999
  // -------------------------------------------------------------------
  describe('Admin identity range', () => {
    it('logs in with 00001/99999 and rejects 00000 and out-of-pattern values', async () => {
      await createAdmin({ loginId: LOGIN_01401400001 });
      await createAdmin({ loginId: LOGIN_01401499999 });
      await createUser({
        loginId: '2025000001',
        password: STUDENT_PASSWORD,
        role: 'STUDENT',
      });

      await login(LOGIN_01401400001, ADMIN_PASSWORD).expect(200);
      await login(LOGIN_01401499999, ADMIN_PASSWORD).expect(200);

      for (const invalid of ['01401400000', '014014100000', '0140140000']) {
        const response = await login(invalid, ADMIN_PASSWORD).expect(401);
        expect(response.body.error.code).toBe('INVALID_CREDENTIALS');
      }
    });

    it('database constraint rejects 01401400000 for Admins and keeps 99999 valid', async () => {
      await getPool().query(
        `INSERT INTO users (login_id, password_hash, full_name, role)
         VALUES ('01401499999', 'hash', 'Boundary Admin', 'ADMIN')`,
      );
      await expect(
        getPool().query(
          `INSERT INTO users (login_id, password_hash, full_name, role)
           VALUES ('01401400000', 'hash', 'Out Of Range Admin', 'ADMIN')`,
        ),
      ).rejects.toMatchObject({ constraint: 'ck_users_admin_login_id' });
      await expect(
        getPool().query(
          `INSERT INTO users (login_id, password_hash, full_name, role)
           VALUES ('014014100000', 'hash', 'Long Admin', 'ADMIN')`,
        ),
      ).rejects.toMatchObject({ constraint: 'ck_users_admin_login_id' });
    });
  });

  // -------------------------------------------------------------------
  // 2. Re-identification revokes access + refresh tokens, preserves data
  // -------------------------------------------------------------------
  describe('session revocation after re-identification', () => {
    it('rejects old access/refresh tokens, logs in with the new ID, and keeps the account identity intact', async () => {
      const classId = await createClass();
      expect(classId).toBeTruthy();
      const adminId = await createAdmin({ loginId: LOGIN_01401400001 });
      const actorId = await createAdmin({
        loginId: LOGIN_01401400002,
        fullName: 'Actor Admin',
      });
      const actor = await loginOk(LOGIN_01401400002, ADMIN_PASSWORD);
      const target = await loginOk(LOGIN_01401400001, ADMIN_PASSWORD);
      expect(target.user.id).toBe(adminId);

      const before = await getPool().query<{
        password_hash: string;
        status: string;
        must_change_password: boolean;
      }>(
        'SELECT password_hash, status, must_change_password FROM users WHERE id = $1',
        [adminId],
      );
      expect(await activeSessionCount(adminId)).toBe(1);

      const updated = await patchUser(actor.accessToken, adminId, {
        role: 'STUDENT',
        loginId: '2025000123',
      }).expect(200);
      expect(updated.body.data.loginId).toBe('2025000123');
      expect(updated.body.data.id).toBe(adminId);

      // Old access token no longer authenticates (session revoked, row kept).
      const me = await request(app)
        .get('/api/v1/auth/me')
        .set('Authorization', bearer(target.accessToken))
        .expect(401);
      expect(me.body.error.code).toBe('SESSION_REVOKED');

      // Old refresh token is rejected.
      const refreshed = await request(app)
        .post('/api/v1/auth/refresh')
        .send({ refreshToken: target.refreshToken })
        .expect(401);
      expect(refreshed.body.error.code).toBe('REFRESH_INVALID');

      // Same password, new identity works.
      const relogin = await loginOk('2025000123', ADMIN_PASSWORD);
      expect(relogin.user.id).toBe(adminId);
      expect(relogin.user.loginId).toBe('2025000123');
      expect(relogin.user.status).toBe('ACTIVE');

      // The retired Admin identity is no longer accepted.
      await login(LOGIN_01401400001, ADMIN_PASSWORD).expect(401);

      const after = await getPool().query<{
        password_hash: string;
        status: string;
        must_change_password: boolean;
      }>(
        'SELECT password_hash, status, must_change_password FROM users WHERE id = $1',
        [adminId],
      );
      expect(after.rows[0]!.password_hash).toBe(before.rows[0]!.password_hash);
      expect(after.rows[0]!.status).toBe('ACTIVE');
      expect(after.rows[0]!.must_change_password).toBe(
        before.rows[0]!.must_change_password,
      );

      // Sessions are retained as history: the pre-change session is revoked,
      // and the fresh login above added one new active session.
      const sessions = await getPool().query<{
        count: string;
        active: string;
        revoked: string;
      }>(
        `SELECT count(*)::text AS count,
                count(*) FILTER (WHERE is_active)::text AS active,
                count(*) FILTER (WHERE revoked_at IS NOT NULL)::text AS revoked
         FROM auth_sessions WHERE user_id = $1`,
        [adminId],
      );
      expect(Number(sessions.rows[0]!.count)).toBe(2);
      expect(Number(sessions.rows[0]!.active)).toBe(1);
      expect(Number(sessions.rows[0]!.revoked)).toBe(1);
      const enrollment = await getPool().query<{ count: string }>(
        `SELECT count(*)::text AS count FROM enrollments WHERE student_id = $1`,
        [adminId],
      );
      expect(Number(enrollment.rows[0]!.count)).toBe(1);

      // Actor is untouched.
      expect(await activeSessionCount(actorId)).toBe(1);
    });
  });

  // -------------------------------------------------------------------
  // 3. Concurrent demotion/deactivation never removes the last Admin
  // -------------------------------------------------------------------
  describe('last active Admin invariant under concurrency', () => {
    async function twoAdmins(): Promise<{
      a: { id: string; session: LoginResult };
      b: { id: string; session: LoginResult };
    }> {
      const aId = await createAdmin({ loginId: LOGIN_01401400001, fullName: 'Admin A' });
      const bId = await createAdmin({ loginId: LOGIN_01401400002, fullName: 'Admin B' });
      return {
        a: { id: aId, session: await loginOk(LOGIN_01401400001, ADMIN_PASSWORD) },
        b: { id: bId, session: await loginOk(LOGIN_01401400002, ADMIN_PASSWORD) },
      };
    }

    it('two concurrent demotions leave at least one active Admin', async () => {
      await createClass();
      const { a, b } = await twoAdmins();
      const results = await Promise.all([
        patchUser(a.session.accessToken, b.id, {
          role: 'STUDENT',
          loginId: '2025000201',
        }),
        patchUser(b.session.accessToken, a.id, {
          role: 'STUDENT',
          loginId: '2025000202',
        }),
      ]);
      const statuses = results.map((r) => r.status).sort();
      expect(statuses).toEqual([200, 409]);
      expect(await activeAdminCount()).toBe(1);
      const conflict = results.find((r) => r.status === 409);
      expect(conflict!.body.error.code).toBe('LAST_ADMIN_PROTECTED');
    });

    it('two concurrent deactivations leave at least one active Admin', async () => {
      const { a, b } = await twoAdmins();
      const results = await Promise.all([
        deactivate(a.session.accessToken, b.id),
        deactivate(b.session.accessToken, a.id),
      ]);
      const statuses = results.map((r) => r.status).sort();
      expect(statuses).toEqual([204, 409]);
      expect(await activeAdminCount()).toBe(1);
    });

    it('concurrent demote plus deactivate leaves at least one active Admin', async () => {
      await createClass();
      const { a, b } = await twoAdmins();
      const results = await Promise.all([
        patchUser(a.session.accessToken, b.id, {
          role: 'STUDENT',
          loginId: '2025000301',
        }),
        deactivate(b.session.accessToken, a.id),
      ]);
      const statuses = results.map((r) => r.status).sort();
      expect(statuses).toEqual([200, 409]);
      expect(await activeAdminCount()).toBe(1);
    });

    it('serialises two concurrent mutations of the same Admin target', async () => {
      await createClass();
      const { a, b } = await twoAdmins();

      // Two different role transitions target the same account at once. The
      // second transaction must see the first one's committed role instead of
      // the stale pre-lock snapshot, so the final state is one coherent role
      // with exactly one matching assignment/enrollment and zero live sessions
      // for the target.
      const results = await Promise.all([
        patchUser(a.session.accessToken, b.id, {
          role: 'STUDENT',
          loginId: '2025000501',
        }),
        patchUser(a.session.accessToken, b.id, {
          role: 'LECTURER',
          loginId: '2025000502',
        }),
      ]);
      expect(results.map((r) => r.status)).toEqual([200, 200]);

      const target = await getPool().query<{
        id: string;
        login_id: string;
        role: string;
      }>('SELECT id, login_id, role FROM users WHERE id = $1', [b.id]);
      expect(target.rows[0]!.id).toBe(b.id);
      expect(target.rows[0]!.role === 'STUDENT' || target.rows[0]!.role === 'LECTURER').toBe(
        true,
      );
      expect(/^[0-9]{10,11}$/.test(target.rows[0]!.login_id)).toBe(true);

      const enrollment = await getPool().query<{
        active: string;
        left: string;
      }>(
        `SELECT count(*) FILTER (WHERE status = 'ACTIVE')::text AS active,
                count(*) FILTER (WHERE status = 'LEFT')::text AS left
         FROM enrollments WHERE student_id = $1`,
        [b.id],
      );
      const assignment = await getPool().query<{ count: string }>(
        'SELECT count(*)::text AS count FROM teaching_assignments WHERE lecturer_id = $1',
        [b.id],
      );
      if (target.rows[0]!.role === 'STUDENT') {
        expect(Number(enrollment.rows[0]!.active)).toBe(1);
        expect(Number(assignment.rows[0]!.count)).toBe(0);
      } else {
        expect(Number(enrollment.rows[0]!.active)).toBe(0);
        expect(Number(assignment.rows[0]!.count)).toBe(1);
      }

      expect(await activeAdminCount()).toBe(1);
      expect(await activeSessionCount(b.id)).toBe(0);
      const oldTargetToken = await request(app)
        .get('/api/v1/auth/me')
        .set('Authorization', bearer(b.session.accessToken))
        .expect(401);
      expect(oldTargetToken.body.error.code).toBe('SESSION_REVOKED');
      expect(await activeSessionCount(a.id)).toBe(1);
    });

    it('keeps self-demotion and self-deactivation rejected', async () => {
      await createClass();
      const adminId = await createAdmin({ loginId: LOGIN_01401400001 });
      const actor = await loginOk(LOGIN_01401400001, ADMIN_PASSWORD);

      const demote = await patchUser(actor.accessToken, adminId, {
        role: 'STUDENT',
        loginId: '2025000401',
      }).expect(409);
      expect(demote.body.error.code).toBe('LAST_ADMIN_PROTECTED');

      const deactivateSelf = await deactivate(actor.accessToken, adminId).expect(409);
      expect(deactivateSelf.body.error.code).toBe('LAST_ADMIN_PROTECTED');
      expect(await activeAdminCount()).toBe(1);
    });
  });

  // -------------------------------------------------------------------
  // 4. Seed idempotency across rename + concurrent runs
  // -------------------------------------------------------------------
  describe('seed-admin stable bootstrap identity', () => {
    it('reuses the same Admin after a profile rename', async () => {
      const first = await seedAdmin({
        fullName: 'Administrator',
        password: 'ChangeMe123456',
      });
      expect(first.created).toBe(true);
      expect(first.loginId).toBe('01401400001');

      await getPool().query(
        `UPDATE users SET full_name = 'Renamed Bootstrap Admin' WHERE login_id = $1`,
        [first.loginId],
      );

      const second = await seedAdmin({
        fullName: 'Administrator',
        password: 'ChangeMe123456',
      });
      expect(second.created).toBe(false);
      expect(second.loginId).toBe(first.loginId);

      const count = await getPool().query<{ count: string }>(
        "SELECT count(*)::text AS count FROM users WHERE role = 'ADMIN'",
      );
      expect(Number(count.rows[0]!.count)).toBe(1);
    });

    it('serialises concurrent seed processes and creates exactly one Admin', async () => {
      const results = await Promise.all([
        seedAdmin({ fullName: 'Administrator', password: 'ChangeMe123456' }),
        seedAdmin({ fullName: 'Administrator', password: 'ChangeMe123456' }),
      ]);
      expect(results.filter((r) => r.created)).toHaveLength(1);
      expect(new Set(results.map((r) => r.loginId)).size).toBe(1);
      expect(results[0]!.loginId).toBe('01401400001');

      const count = await getPool().query<{ count: string }>(
        "SELECT count(*)::text AS count FROM users WHERE role = 'ADMIN'",
      );
      expect(Number(count.rows[0]!.count)).toBe(1);

      const markers = await getPool().query<{ count: string }>(
        "SELECT count(*)::text AS count FROM audit_logs WHERE action = 'ADMIN_SEEDED'",
      );
      expect(Number(markers.rows[0]!.count)).toBeGreaterThanOrEqual(1);
    });
  });

  // -------------------------------------------------------------------
  // 5. Migration 003: clean install and upgrade paths
  // -------------------------------------------------------------------
  describe('migration 003 on disposable databases', () => {
    const cleanName = databaseName() + '_p3r_clean';
    const upgradeName = databaseName() + '_p3r_upgrade';
    const legacyName = databaseName() + '_p3r_legacy';
    const apiName = databaseName() + '_p3r_api';

    afterAll(async () => {
      await closePool();
      await dropDatabase(cleanName);
      await dropDatabase(upgradeName);
      await dropDatabase(legacyName);
      await dropDatabase(apiName);
    });

    it('clean install (001+002+003) revokes only changed Admin sessions and tightens the range', async () => {
      await recreateDatabase(cleanName);
      const client = new Client({ connectionString: connectionStringFor(cleanName) });
      await client.connect();
      try {
        await client.query(migrationSql(MIGRATION_001));
        const fixture = await createRangeFixture(client);
        // Extra pre-002 row holding the out-of-range identity: 002 skips it
        // (it matches the regex), so 003 must normalise it.
        const outOfRange = await client.query<{ id: string }>(
          `INSERT INTO users (login_id, password_hash, full_name, role)
           VALUES ('01401400000', 'legacy-hash', 'Out Of Range Admin', 'ADMIN')
           RETURNING id`,
        );
        const outOfRangeId = outOfRange.rows[0]!.id;
        const outOfRangeSession = await client.query<{ id: string }>(
          `INSERT INTO auth_sessions (user_id, token_hash, refresh_token_hash, expires_at)
           VALUES ($1, 'oor-access', 'oor-refresh', now() + interval '1 day')
           RETURNING id`,
          [outOfRangeId],
        );
        // Legal Admin identity that is NOT re-identified. Its profile changes
        // after the session was issued, so `users.updated_at` is newer than
        // the session; the migration must not revoke this session.
        const legal = await client.query<{ id: string }>(
          `INSERT INTO users (login_id, password_hash, full_name, role)
           VALUES ('01401400003', 'legal-hash', 'Legal Admin', 'ADMIN')
           RETURNING id`,
        );
        const legalId = legal.rows[0]!.id;
        const legalSession = await client.query<{ id: string }>(
          `INSERT INTO auth_sessions
             (user_id, token_hash, refresh_token_hash, created_at, expires_at)
           VALUES ($1, 'legal-access', 'legal-refresh', now() - interval '1 hour',
                   now() + interval '1 day')
           RETURNING id`,
          [legalId],
        );
        await client.query(
          "UPDATE users SET full_name = 'Legal Admin Renamed', updated_at = now() WHERE id = $1",
          [legalId],
        );
        const before = await identitySnapshot(client);

        await client.query(migrationSql(MIGRATION_002));
        const afterBackfill = await sessionState(client, fixture.sessionId);
        // 002 is now atomic: the changed Admin's session is already revoked,
        // before 003 runs.
        expect(afterBackfill.is_active).toBe(false);
        expect(afterBackfill.rows).toBe(3);
        const marker002 = await client.query<{
          old_login_id: string;
          new_login_id: string;
          migration_id: string;
        }>(
          'SELECT old_login_id, new_login_id, migration_id FROM admin_identity_changes WHERE user_id = $1',
          [fixture.adminId],
        );
        expect(marker002.rows).toEqual([
          {
            old_login_id: 'admin',
            new_login_id: '01401400002',
            migration_id: '002_admin_login_ids.sql',
          },
        ]);
        // 01401400000 is skipped by 002 and still has its pre-migration session
        // until 003 handles it; the unchanged legal Admin keeps its session
        // even though its profile update bumped updated_at.
        expect((await sessionState(client, outOfRangeSession.rows[0]!.id)).is_active).toBe(
          true,
        );
        expect((await sessionState(client, legalSession.rows[0]!.id)).is_active).toBe(
          true,
        );

        await client.query(migrationSql(MIGRATION_003));

        const revoked = await sessionState(client, fixture.sessionId);
        expect(revoked.rows).toBe(3);
        expect(revoked.is_active).toBe(false);
        expect(revoked.revoked_at).not.toBeNull();

        // 01401400000 was normalised to a valid identity by 003 and its
        // session was revoked atomically with that change.
        const normalised = await client.query<{ login_id: string }>(
          'SELECT login_id FROM users WHERE id = $1',
          [outOfRangeId],
        );
        expect(normalised.rows[0]!.login_id).not.toBe('01401400000');
        expect(/^014014(?!00000)[0-9]{5}$/.test(normalised.rows[0]!.login_id)).toBe(
          true,
        );
        const oorSession = await sessionState(client, outOfRangeSession.rows[0]!.id);
        expect(oorSession.is_active).toBe(false);
        expect((await sessionState(client, legalSession.rows[0]!.id)).is_active).toBe(
          true,
        );
        const marker003 = await client.query<{
          old_login_id: string;
          new_login_id: string;
          migration_id: string;
        }>(
          'SELECT old_login_id, new_login_id, migration_id FROM admin_identity_changes WHERE user_id = $1',
          [outOfRangeId],
        );
        expect(marker003.rows).toEqual([
          {
            old_login_id: '01401400000',
            new_login_id: normalised.rows[0]!.login_id,
            migration_id: '003_admin_session_revoke_and_range.sql',
          },
        ]);

        // The backfilled Admin keeps UUID/password/status/must_change and its
        // relations (session + audit rows still reference it).
        const admin = await client.query<{ id: string; login_id: string }>(
          'SELECT id, login_id FROM users WHERE id = $1',
          [fixture.adminId],
        );
        expect(admin.rows[0]!.login_id).toBe('01401400002');
        const after = await identitySnapshot(client);
        expect(JSON.parse(after)).toEqual(JSON.parse(before));

        // Audit + enrollment references survive.
        await client.query('SELECT 1 FROM audit_logs WHERE actor_id = $1', [
          fixture.adminId,
        ]);
        await client.query(
          'SELECT 1 FROM enrollments WHERE student_id = $1',
          [fixture.studentId],
        );

        // Range 1..99999 enforced; 00000 and 12-digit IDs rejected.
        await expect(
          client.query(
            `INSERT INTO users (login_id, password_hash, full_name, role)
             VALUES ('01401400000', 'hash', 'Bad', 'ADMIN')`,
          ),
        ).rejects.toMatchObject({ constraint: 'ck_users_admin_login_id' });
        await expect(
          client.query(
            `INSERT INTO users (login_id, password_hash, full_name, role)
             VALUES ('014014100000', 'hash', 'Bad', 'ADMIN')`,
          ),
        ).rejects.toMatchObject({ constraint: 'ck_users_admin_login_id' });
        await client.query(
          `INSERT INTO users (login_id, password_hash, full_name, role)
           VALUES ('01401499999', 'hash', 'Boundary', 'ADMIN')`,
        );
      } finally {
        await client.end();
      }
    });

    it('upgrade from a recorded 002 applies only 003, preserves the revocation, and backfills checksums', async () => {
      await recreateDatabase(upgradeName);
      const client = new Client({ connectionString: connectionStringFor(upgradeName) });
      await client.connect();
      let fixture: RangeFixture;
      try {
        await client.query(migrationSql(MIGRATION_001));
        fixture = await createRangeFixture(client);
        await client.query(migrationSql(MIGRATION_002));
        // Simulate an installation managed by the old runner: name-only table.
        await client.query(`
          CREATE TABLE schema_migrations (
            id text PRIMARY KEY,
            applied_at timestamptz NOT NULL DEFAULT now()
          )
        `);
        await client.query(
          `INSERT INTO schema_migrations (id) VALUES ('001_init.sql'), ('002_admin_login_ids.sql')`,
        );
      } finally {
        await client.end();
      }

      const run = runMigrate(upgradeName);
      expect(run.status).toBe(0);

      const check = new Client({ connectionString: connectionStringFor(upgradeName) });
      await check.connect();
      try {
        const rows = await check.query<{ id: string; checksum: string | null }>(
          'SELECT id, checksum FROM schema_migrations ORDER BY id',
        );
        expect(rows.rows.map((r) => r.id)).toEqual([
          MIGRATION_001,
          MIGRATION_002,
          MIGRATION_003,
          MIGRATION_004,
          MIGRATION_005,
        ]);
        for (const row of rows.rows) {
          expect(row.checksum).toMatch(/^[0-9a-f]{64}$/);
        }
        const revoked = await sessionState(check, fixture.sessionId);
        expect(revoked.is_active).toBe(false);
        expect(revoked.revoked_at).not.toBeNull();

        await expect(
          check.query(
            `INSERT INTO users (login_id, password_hash, full_name, role)
             VALUES ('01401400000', 'hash', 'Bad', 'ADMIN')`,
          ),
        ).rejects.toMatchObject({ constraint: 'ck_users_admin_login_id' });

        // Checksum mismatch on an already-applied migration fails fast.
        await check.query(
          `UPDATE schema_migrations SET checksum = 'deadbeef' WHERE id = '001_init.sql'`,
        );
      } finally {
        await check.end();
      }

      const tampered = runMigrate(upgradeName, 'info');
      expect(tampered.status).not.toBe(0);
      expect(tampered.stderr + tampered.stdout).toMatch(/checksum|berubah/i);

      const restore = new Client({ connectionString: connectionStringFor(upgradeName) });
      await restore.connect();
      try {
        await restore.query(
          `UPDATE schema_migrations SET checksum = NULL WHERE id = '001_init.sql'`,
        );
      } finally {
        await restore.end();
      }
      const legacy = runMigrate(upgradeName);
      expect(legacy.status).toBe(0);

      const verify = new Client({ connectionString: connectionStringFor(upgradeName) });
      await verify.connect();
      try {
        const row = await verify.query<{ checksum: string | null }>(
          `SELECT checksum FROM schema_migrations WHERE id = '001_init.sql'`,
        );
        expect(row.rows[0]!.checksum).toMatch(/^[0-9a-f]{64}$/);
      } finally {
        await verify.end();
      }
    });

    it('fails fast when a historical 002 checksum differs, before 003 can run', async () => {
      await recreateDatabase(legacyName);
      const client = new Client({ connectionString: connectionStringFor(legacyName) });
      await client.connect();
      try {
        await client.query(migrationSql(MIGRATION_001));
        await client.query(`
          CREATE TABLE schema_migrations (
            id text PRIMARY KEY,
            applied_at timestamptz NOT NULL DEFAULT now(),
            checksum text
          )
        `);
        await client.query(
          `INSERT INTO schema_migrations (id, checksum)
           VALUES ('001_init.sql', $1), ('002_admin_login_ids.sql', 'historical-different-checksum')`,
          [createHash('sha256').update(migrationSql(MIGRATION_001)).digest('hex')],
        );
      } finally {
        await client.end();
      }

      const run = runMigrate(legacyName, 'info');
      expect(run.status).not.toBe(0);
      expect(run.stderr + run.stdout).toMatch(/checksum|berubah/i);

      const verify = new Client({ connectionString: connectionStringFor(legacyName) });
      await verify.connect();
      try {
        const rows = await verify.query<{ id: string }>(
          'SELECT id FROM schema_migrations ORDER BY id',
        );
        // 003 was never applied: the deployment gate stops before the service
        // can open traffic on a schema with unresolved Admin session state.
        expect(rows.rows.map((row) => row.id)).toEqual([
          MIGRATION_001,
          MIGRATION_002,
        ]);
      } finally {
        await verify.end();
      }
    });

    it('blocks an upgrade whose historical 002 has no identity marker, before traffic opens', async () => {
      await recreateDatabase(legacyName);
      const client = new Client({ connectionString: connectionStringFor(legacyName) });
      await client.connect();
      try {
        await client.query(migrationSql(MIGRATION_001));
        await createRangeFixture(client);
        await client.query(migrationSql(MIGRATION_002));
        // Simulate the pre-remediation lineage: 002 applied but it never wrote
        // an identity marker. The runner can no longer know which accounts
        // changed, so 003 must refuse to guess instead of leaving old sessions
        // alive or broadly revoking unchanged Admins.
        await client.query('DROP TABLE admin_identity_changes');
        await client.query(`
          CREATE TABLE schema_migrations (
            id text PRIMARY KEY,
            applied_at timestamptz NOT NULL DEFAULT now()
          )
        `);
        await client.query(
          `INSERT INTO schema_migrations (id) VALUES ('001_init.sql'), ('002_admin_login_ids.sql')`,
        );
      } finally {
        await client.end();
      }

      const run = runMigrate(legacyName, 'info');
      expect(run.status).not.toBe(0);
      expect(run.stderr + run.stdout).toMatch(/admin_identity_changes|penanda/i);

      const verify = new Client({ connectionString: connectionStringFor(legacyName) });
      await verify.connect();
      try {
        const rows = await verify.query<{ id: string }>(
          'SELECT id FROM schema_migrations ORDER BY id',
        );
        expect(rows.rows.map((row) => row.id)).toEqual([
          MIGRATION_001,
          MIGRATION_002,
        ]);
      } finally {
        await verify.end();
      }
    });

    it('rejects old access/refresh tokens through the API after migration and accepts the new Admin ID', async () => {
      await recreateDatabase(apiName);
      const setup = new Client({ connectionString: connectionStringFor(apiName) });
      let adminId: string;
      let passwordHash: string;
      try {
        await setup.connect();
        await setup.query(migrationSql(MIGRATION_001));
        await setup.query(`
          CREATE TABLE schema_migrations (
            id text PRIMARY KEY,
            applied_at timestamptz NOT NULL DEFAULT now(),
            checksum text
          )
        `);
        await setup.query(
          'INSERT INTO schema_migrations (id, checksum) VALUES ($1, $2)',
          [
            MIGRATION_001,
            createHash('sha256').update(migrationSql(MIGRATION_001)).digest('hex'),
          ],
        );
        passwordHash = await hashPassword(ADMIN_PASSWORD);
        const admin = await setup.query<{ id: string }>(
          `INSERT INTO users (login_id, password_hash, full_name, role, must_change_password)
           VALUES ('admin', $1, 'Legacy Administrator', 'ADMIN', true)
           RETURNING id`,
          [passwordHash],
        );
        adminId = admin.rows[0]!.id;
      } finally {
        await setup.end();
      }

      const oldSessionId = randomUUID();
      const oldAccessToken = signAccessToken({
        userId: adminId,
        sessionId: oldSessionId,
        role: 'ADMIN',
      });
      const oldRefreshToken = generateRefreshToken();
      const session = new Client({ connectionString: connectionStringFor(apiName) });
      try {
        await session.connect();
        await session.query(
          `INSERT INTO auth_sessions
             (id, user_id, token_hash, refresh_token_hash, expires_at)
           VALUES ($1, $2, $3, $4, now() + interval '1 day')`,
          [
            oldSessionId,
            adminId,
            hashToken(oldAccessToken),
            hashToken(oldRefreshToken),
          ],
        );
      } finally {
        await session.end();
      }

      const migrated = runMigrate(apiName);
      expect(migrated.status).toBe(0);

      const lookup = new Client({ connectionString: connectionStringFor(apiName) });
      let newLoginId: string;
      try {
        await lookup.connect();
        const row = await lookup.query<{ login_id: string }>(
          'SELECT login_id FROM users WHERE id = $1',
          [adminId],
        );
        newLoginId = row.rows[0]!.login_id;
        expect(/^014014(?!00000)[0-9]{5}$/.test(newLoginId)).toBe(true);
      } finally {
        await lookup.end();
      }

      const port = await freePort();
      const baseUrl = 'http://127.0.0.1:' + port;
      const tsx = path.join(process.cwd(), 'node_modules', '.bin', 'tsx');
      const child = spawn(tsx, ['src/server.ts'], {
        cwd: process.cwd(),
        env: {
          ...process.env,
          DATABASE_URL: connectionStringFor(apiName),
          PORT: String(port),
          NODE_ENV: 'test',
          LOG_LEVEL: 'silent',
        },
        stdio: ['ignore', 'ignore', 'pipe'],
      });
      try {
        await waitForHealth(baseUrl, child);

        const oldAccess = await fetch(baseUrl + '/api/v1/auth/me', {
          headers: { Authorization: bearer(oldAccessToken) },
        });
        expect(oldAccess.status).toBe(401);
        expect(
          ((await oldAccess.json()) as { error?: { code?: string } }).error?.code,
        ).toBe('SESSION_REVOKED');

        const oldRefresh = await fetch(baseUrl + '/api/v1/auth/refresh', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ refreshToken: oldRefreshToken }),
        });
        expect(oldRefresh.status).toBe(401);
        expect(
          ((await oldRefresh.json()) as { error?: { code?: string } }).error?.code,
        ).toBe('REFRESH_INVALID');

        const relogin = await fetch(baseUrl + '/api/v1/auth/login', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ loginId: newLoginId, password: ADMIN_PASSWORD }),
        });
        expect(relogin.status).toBe(200);
        const reloginBody = (await relogin.json()) as {
          data?: { user?: { id?: string; loginId?: string; status?: string } };
        };
        expect(reloginBody.data?.user?.id).toBe(adminId);
        expect(reloginBody.data?.user?.loginId).toBe(newLoginId);
        expect(reloginBody.data?.user?.status).toBe('ACTIVE');
      } finally {
        if (child.exitCode === null) {
          child.kill('SIGTERM');
          await new Promise<void>((resolve) => {
            const timeout = setTimeout(() => {
              child.kill('SIGKILL');
              resolve();
            }, 5_000);
            child.once('exit', () => {
              clearTimeout(timeout);
              resolve();
            });
          });
        }
      }
    }, 30_000);
  });

  // -------------------------------------------------------------------
  // 6. Preflight aggregate is range-aware and masks identifiers
  // -------------------------------------------------------------------
  describe('preflight-admin-ids', () => {
    it('reports range-aware collisions/capacity without raw identifiers', async () => {
      await createAdmin({ loginId: LOGIN_01401400001 });
      await createUser({
        loginId: LOGIN_01401400002,
        password: STUDENT_PASSWORD,
        role: 'STUDENT',
      });

      const tsx = path.join(process.cwd(), 'node_modules', '.bin', 'tsx');
      const result = spawnSync(tsx, ['src/db/preflight-admin-ids.ts'], {
        cwd: process.cwd(),
        env: {
          ...process.env,
          DATABASE_URL: process.env.DATABASE_URL as string,
          NODE_ENV: 'test',
          LOG_LEVEL: 'silent',
        },
        encoding: 'utf8',
        timeout: 60_000,
      });
      expect(result.status).toBe(0);

      const report = JSON.parse(result.stdout) as {
        adminPattern: string;
        capacity: number;
        adminAccounts: { total: number; maskedNeedsBackfillExamples: string[] };
        collisions: { academicAccountsWithAdminShapedId: number; maskedExamples: string[] };
        capacityStatus: { numbersRemaining: number; nextAvailableCandidate: string | null };
        backfillFeasible: boolean;
      };
      expect(report.adminPattern).toContain('00001');
      expect(report.capacity).toBe(99999);
      expect(report.collisions.academicAccountsWithAdminShapedId).toBe(1);
      for (const masked of report.collisions.maskedExamples) {
        expect(masked).toMatch(/^014014-#[0-9a-f]{6}$/);
      }
      expect(report.capacityStatus.nextAvailableCandidate).toMatch(
        /^014014(?!00000)[0-9]{5}$/,
      );
      expect(report.backfillFeasible).toBe(true);
      expect(result.stdout).not.toMatch(/2025000/);
    });
  });
});
