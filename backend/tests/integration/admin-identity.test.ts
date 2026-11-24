import fs from 'node:fs';
import path from 'node:path';
import { Client } from 'pg';
import request from 'supertest';
import { afterAll, beforeEach, describe, expect, it } from 'vitest';
import { createApp } from '../../src/app';
import { closePool, getPool } from '../../src/db/client';
import { seedAdmin } from '../../src/db/seed-admin-core';
import { resetLoginThrottle } from '../../src/lib/login-throttle';
import { ADMIN_LOGIN_ID_PATTERN } from '../../src/lib/login-id';
import {
  activeSessionCount,
  closeDb,
  createAdmin,
  createClass,
  createUser,
  resetDb,
  TEST_ADMIN_LOGIN_ID,
  TEST_ADMIN_PASSWORD,
} from '../helpers/db';

const app = createApp();
const describeDb = process.env.DATABASE_URL ? describe : describe.skip;

const STUDENT_PASSWORD = 'StudentPass123';
const LECTURER_PASSWORD = 'LecturerPass123';

function bearer(token: string): string {
  return 'Bearer ' + token;
}

function login(loginId: string, password: string) {
  return request(app).post('/api/v1/auth/login').send({ loginId, password });
}

async function loginOk(loginId: string, password: string) {
  const response = await login(loginId, password).expect(200);
  return response.body.data as {
    accessToken: string;
    user: { id: string; role: string; loginId: string };
  };
}

async function adminSession(): Promise<{ token: string; userId: string }> {
  const userId = await createAdmin();
  const data = await loginOk(TEST_ADMIN_LOGIN_ID, TEST_ADMIN_PASSWORD);
  return { token: data.accessToken, userId };
}

async function createStudentViaApi(
  token: string,
  loginId: string,
  fullName = 'Student ' + loginId,
): Promise<{ id: string; loginId: string }> {
  const response = await request(app)
    .post('/api/v1/admin/users')
    .set('Authorization', bearer(token))
    .send({
      loginId,
      fullName,
      role: 'STUDENT',
      temporaryPassword: STUDENT_PASSWORD,
    })
    .expect(201);
  return response.body.data as { id: string; loginId: string };
}

function patchUser(
  token: string,
  userId: string,
  body: Record<string, unknown>,
) {
  return request(app)
    .patch('/api/v1/admin/users/' + userId)
    .set('Authorization', bearer(token))
    .send(body);
}

async function enrollmentStatus(studentId: string): Promise<string | null> {
  const result = await getPool().query<{ status: string }>(
    'SELECT status FROM enrollments WHERE student_id = $1',
    [studentId],
  );
  return result.rows[0]?.status ?? null;
}

async function assignLecturer(classId: string, lecturerId: string): Promise<void> {
  await getPool().query(
    'INSERT INTO teaching_assignments (class_id, lecturer_id) VALUES ($1, $2)',
    [classId, lecturerId],
  );
}

// ---------------------------------------------------------------------------
// Migration/backfill simulation on a disposable scratch database
// ---------------------------------------------------------------------------

const MIGRATION_001 = '001_init.sql';
const MIGRATION_002 = '002_admin_login_ids.sql';

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

interface LegacyFixture {
  classId: string;
  adminId: string;
  studentId: string;
  lecturerId: string;
  collisionStudentId: string;
  enrollmentId: string;
  teachingAssignmentId: string;
  topicId: string;
  caseStudyId: string;
  submissionId: string;
  assessmentId: string;
  sessionId: string;
  auditId: string;
}

/**
 * Pre-002 world: a legacy `admin` username plus one academic account that
 * already occupies Admin-shaped identifier 01401400001 (collision), and rows
 * that reference the Admin UUID (session, audit log).
 */
async function createLegacyFixture(client: Client): Promise<LegacyFixture> {
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
     VALUES ('2025000001', 'legacy-hash', 'Legacy Student', 'STUDENT') RETURNING id`,
  );
  const studentId = student.rows[0]!.id;

  const lecturer = await client.query<{ id: string }>(
    `INSERT INTO users (login_id, password_hash, full_name, role)
     VALUES ('1990010001', 'legacy-hash', 'Legacy Lecturer', 'LECTURER') RETURNING id`,
  );
  const lecturerId = lecturer.rows[0]!.id;

  const collision = await client.query<{ id: string }>(
    `INSERT INTO users (login_id, password_hash, full_name, role)
     VALUES ('01401400001', 'legacy-hash', 'Collision Student', 'STUDENT') RETURNING id`,
  );
  const collisionStudentId = collision.rows[0]!.id;

  const enrollment = await client.query<{ id: string }>(
    `INSERT INTO enrollments (class_id, student_id) VALUES ($1, $2) RETURNING id`,
    [classId, studentId],
  );
  const teaching = await client.query<{ id: string }>(
    `INSERT INTO teaching_assignments (class_id, lecturer_id) VALUES ($1, $2) RETURNING id`,
    [classId, lecturerId],
  );
  const topic = await client.query<{ id: string }>(
    `INSERT INTO topics (class_id, created_by, title, status)
     VALUES ($1, $2, 'Legacy Topic', 'PUBLISHED') RETURNING id`,
    [classId, lecturerId],
  );
  const caseStudy = await client.query<{ id: string }>(
    `INSERT INTO case_studies (topic_id, created_by, instructions, deadline_at)
     VALUES ($1, $2, 'Legacy case', now() + interval '1 day') RETURNING id`,
    [topic.rows[0]!.id, lecturerId],
  );
  const submission = await client.query<{ id: string }>(
    `INSERT INTO submissions (case_study_id, student_id, answer_text)
     VALUES ($1, $2, 'Legacy answer') RETURNING id`,
    [caseStudy.rows[0]!.id, studentId],
  );
  const assessment = await client.query<{ id: string }>(
    `INSERT INTO assessments (submission_id, assessed_by, score)
     VALUES ($1, $2, 80) RETURNING id`,
    [submission.rows[0]!.id, lecturerId],
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
    lecturerId,
    collisionStudentId,
    enrollmentId: enrollment.rows[0]!.id,
    teachingAssignmentId: teaching.rows[0]!.id,
    topicId: topic.rows[0]!.id,
    caseStudyId: caseStudy.rows[0]!.id,
    submissionId: submission.rows[0]!.id,
    assessmentId: assessment.rows[0]!.id,
    sessionId: session.rows[0]!.id,
    auditId: audit.rows[0]!.id,
  };
}

async function usersSnapshot(
  client: Client,
): Promise<string> {
  const result = await client.query(
    `SELECT login_id, role, status, must_change_password, password_hash
     FROM users ORDER BY login_id`,
  );
  return JSON.stringify(result.rows);
}

describeDb('Admin identity — login format, allocation, migration', () => {
  beforeEach(async () => {
    await resetDb();
    resetLoginThrottle();
  });

  afterAll(async () => {
    await closeDb();
  });

  // -------------------------------------------------------------------
  // Login validation per role
  // -------------------------------------------------------------------
  describe('login identifier validation', () => {
    it('accepts 10 and 11 digit academic logins', async () => {
      await createUser({
        loginId: '2025000001',
        password: STUDENT_PASSWORD,
        role: 'STUDENT',
      });
      await createUser({
        loginId: '20250000011',
        password: STUDENT_PASSWORD,
        role: 'STUDENT',
      });

      await login('2025000001', STUDENT_PASSWORD).expect(200);
      await login('20250000011', STUDENT_PASSWORD).expect(200);
    });

    it('rejects 9 digit, 12 digit, and non-numeric login identifiers', async () => {
      await createUser({
        loginId: '2025000001',
        password: STUDENT_PASSWORD,
        role: 'STUDENT',
      });

      for (const value of ['202500001', '202500000112', '202500AB01', '2025-0001']) {
        const response = await login(value, STUDENT_PASSWORD).expect(401);
        expect(response.body.error.code).toBe('INVALID_CREDENTIALS');
      }
    });

    it('rejects the retired legacy admin username even for a matching row', async () => {
      // A STUDENT row shaped like the legacy username proves the role-bound
      // format check rejects it before any session is created.
      await createUser({
        loginId: 'admin',
        password: STUDENT_PASSWORD,
        role: 'STUDENT',
      });

      const response = await login('admin', STUDENT_PASSWORD).expect(401);
      expect(response.body.error.code).toBe('INVALID_CREDENTIALS');
    });

    it('rejects legacy usernames for Admin accounts', async () => {
      await createAdmin();
      const response = await login('admin', TEST_ADMIN_PASSWORD).expect(401);
      expect(response.body.error.code).toBe('INVALID_CREDENTIALS');
    });

    it('accepts the allocated Admin identifier and rejects near-miss patterns', async () => {
      await createAdmin();
      const ok = await login(TEST_ADMIN_LOGIN_ID, TEST_ADMIN_PASSWORD).expect(200);
      expect(ok.body.data.user.role).toBe('ADMIN');

      for (const value of ['0140140000', '014014000001', '01401500001', '01401A00001']) {
        const response = await login(value, TEST_ADMIN_PASSWORD).expect(401);
        expect(response.body.error.code).toBe('INVALID_CREDENTIALS');
      }
    });
  });

  // -------------------------------------------------------------------
  // Create: academic IDs validated, Admin IDs always allocated
  // -------------------------------------------------------------------
  describe('creating accounts', () => {
    it('validates academic NIM/NIDN on create', async () => {
      const admin = await adminSession();
      await createClass();

      for (const bad of ['202500001', '202500000112', '2025AB0001']) {
        const response = await request(app)
          .post('/api/v1/admin/users')
          .set('Authorization', bearer(admin.token))
          .send({
            loginId: bad,
            fullName: 'Bad ID',
            role: 'STUDENT',
            temporaryPassword: STUDENT_PASSWORD,
          })
          .expect(422);
        expect(response.body.error.code).toBe('VALIDATION_ERROR');
      }

      const missing = await request(app)
        .post('/api/v1/admin/users')
        .set('Authorization', bearer(admin.token))
        .send({
          fullName: 'No ID',
          role: 'LECTURER',
          temporaryPassword: LECTURER_PASSWORD,
        })
        .expect(422);
      expect(missing.body.error.code).toBe('VALIDATION_ERROR');
    });

    it('ignores caller-supplied identifiers for Admin accounts', async () => {
      const admin = await adminSession();
      await createClass();

      for (const supplied of ['2025000099', 'admin', undefined]) {
        const response = await request(app)
          .post('/api/v1/admin/users')
          .set('Authorization', bearer(admin.token))
          .send({
            loginId: supplied,
            fullName: 'Auto Admin',
            role: 'ADMIN',
            temporaryPassword: 'AdminPass9876',
          })
          .expect(201);

        expect(ADMIN_LOGIN_ID_PATTERN.test(response.body.data.loginId)).toBe(true);
        if (supplied) expect(response.body.data.loginId).not.toBe(supplied);
      }
    });

    it('skips Admin-shaped identifiers already used by academic accounts', async () => {
      const admin = await adminSession(); // occupies 01401400001
      await createClass();
      await createUser({
        loginId: '01401400002',
        password: STUDENT_PASSWORD,
        role: 'STUDENT',
      });

      const response = await request(app)
        .post('/api/v1/admin/users')
        .set('Authorization', bearer(admin.token))
        .send({
          fullName: 'Next Admin',
          role: 'ADMIN',
          temporaryPassword: 'AdminPass9876',
        })
        .expect(201);

      expect(response.body.data.loginId).toBe('01401400003');
    });

    it('rejects duplicate academic identifiers', async () => {
      const admin = await adminSession();
      await createClass();
      await createStudentViaApi(admin.token, '2025000001');

      const response = await request(app)
        .post('/api/v1/admin/users')
        .set('Authorization', bearer(admin.token))
        .send({
          loginId: '2025000001',
          fullName: 'Duplicate',
          role: 'STUDENT',
          temporaryPassword: STUDENT_PASSWORD,
        })
        .expect(409);
      expect(response.body.error.code).toBe('LOGIN_ID_TAKEN');
    });

    it('allocates distinct Admin identifiers under concurrent creation', async () => {
      const admin = await adminSession();
      await createClass();

      const create = (name: string) =>
        request(app)
          .post('/api/v1/admin/users')
          .set('Authorization', bearer(admin.token))
          .send({
            fullName: name,
            role: 'ADMIN',
            temporaryPassword: 'AdminPass9876',
          });

      const [first, second] = await Promise.all([
        create('Concurrent One'),
        create('Concurrent Two'),
      ]);

      expect(first.status).toBe(201);
      expect(second.status).toBe(201);
      expect(first.body.data.loginId).not.toBe(second.body.data.loginId);
      expect(ADMIN_LOGIN_ID_PATTERN.test(first.body.data.loginId)).toBe(true);
      expect(ADMIN_LOGIN_ID_PATTERN.test(second.body.data.loginId)).toBe(true);

      const rows = await getPool().query<{ count: string }>(
        "SELECT count(DISTINCT login_id)::text AS count FROM users WHERE role = 'ADMIN'",
      );
      expect(Number(rows.rows[0]!.count)).toBe(3); // actor + 2 new
    });

    it(
      'fails clearly when the Admin number space is exhausted',
      async () => {
        const admin = await adminSession();

        await getPool().query(
          `INSERT INTO users (login_id, password_hash, full_name, role, must_change_password)
           SELECT '014014' || lpad(g::text, 5, '0'), 'x', 'Filler ' || g, 'STUDENT', false
           FROM generate_series(2, 99999) AS g`,
        );

        const response = await request(app)
          .post('/api/v1/admin/users')
          .set('Authorization', bearer(admin.token))
          .send({
            fullName: 'No Space',
            role: 'ADMIN',
            temporaryPassword: 'AdminPass9876',
          })
          .expect(409);

        expect(response.body.error.code).toBe('ADMIN_ID_EXHAUSTED');
      },
      60_000,
    );
  });

  // -------------------------------------------------------------------
  // Update: atomic role + identity transitions
  // -------------------------------------------------------------------
  describe('role changes and identity transitions', () => {
    async function twoAdmins() {
      const admin = await adminSession(); // 01401400001
      const second = await createUser({
        loginId: '01401400002',
        password: 'AdminPass1234',
        role: 'ADMIN',
      });
      return { admin, second };
    }

    it('updates the full name without touching the identity', async () => {
      const admin = await adminSession();
      const response = await patchUser(admin.token, admin.userId, {
        fullName: 'Renamed Admin',
      }).expect(200);
      expect(response.body.data.fullName).toBe('Renamed Admin');
      expect(response.body.data.loginId).toBe(TEST_ADMIN_LOGIN_ID);
    });

    it('rejects an Admin → academic transition without a valid target NIM/NIDN', async () => {
      const { admin, second } = await twoAdmins();
      await createClass();
      await loginOk('01401400002', 'AdminPass1234');
      const sessionsBefore = await activeSessionCount(second);

      for (const body of [
        { role: 'STUDENT' },
        { role: 'STUDENT', loginId: '202500001' },
        { role: 'LECTURER', loginId: 'LECTURER1' },
      ]) {
        const response = await patchUser(admin.token, second, body).expect(422);
        expect(response.body.error.code).toBe('INVALID_LOGIN_ID');
      }

      const row = await getPool().query<{ login_id: string; role: string }>(
        'SELECT login_id, role FROM users WHERE id = $1',
        [second],
      );
      expect(row.rows[0]!.role).toBe('ADMIN');
      expect(row.rows[0]!.login_id).toBe('01401400002');
      expect(await activeSessionCount(second)).toBe(sessionsBefore);
    });

    it('rolls back completely when the target NIM is already used', async () => {
      const { admin, second } = await twoAdmins();
      await createClass();
      await createStudentViaApi(admin.token, '2025000001');
      await loginOk('01401400002', 'AdminPass1234');

      const response = await patchUser(admin.token, second, {
        role: 'STUDENT',
        loginId: '2025000001',
      }).expect(409);
      expect(response.body.error.code).toBe('LOGIN_ID_TAKEN');

      const row = await getPool().query<{ login_id: string; role: string }>(
        'SELECT login_id, role FROM users WHERE id = $1',
        [second],
      );
      expect(row.rows[0]!).toEqual({ login_id: '01401400002', role: 'ADMIN' });
      expect(await enrollmentStatus(second)).toBeNull();
      expect(await activeSessionCount(second)).toBe(1);
    });

    it('moves ADMIN → STUDENT with the supplied NIM, enrollment, and session revocation', async () => {
      const { admin, second } = await twoAdmins();
      const classId = await createClass();
      await loginOk('01401400002', 'AdminPass1234');

      const response = await patchUser(admin.token, second, {
        role: 'STUDENT',
        loginId: '2025000001',
      }).expect(200);

      expect(response.body.data.role).toBe('STUDENT');
      expect(response.body.data.loginId).toBe('2025000001');
      expect(response.body.data.enrollmentStatus).toBe('ACTIVE');
      expect(await activeSessionCount(second)).toBe(0);

      const enrollment = await getPool().query<{ class_id: string; status: string }>(
        'SELECT class_id, status FROM enrollments WHERE student_id = $1',
        [second],
      );
      expect(enrollment.rows[0]!).toEqual({ class_id: classId, status: 'ACTIVE' });

      // Old Admin identity no longer authenticates; new NIM does.
      await login('01401400002', 'AdminPass1234').expect(401);
      const fresh = await login('2025000001', 'AdminPass1234').expect(200);
      expect(fresh.body.data.user.role).toBe('STUDENT');
    });

    it('moves ADMIN → LECTURER and creates the teaching assignment', async () => {
      const { admin, second } = await twoAdmins();
      const classId = await createClass();

      const response = await patchUser(admin.token, second, {
        role: 'LECTURER',
        loginId: '1990010001',
      }).expect(200);
      expect(response.body.data.loginId).toBe('1990010001');
      expect(response.body.data.teachingAssignmentId).toBeTruthy();

      const assignment = await getPool().query<{ class_id: string }>(
        'SELECT class_id FROM teaching_assignments WHERE lecturer_id = $1',
        [second],
      );
      expect(assignment.rows[0]!.class_id).toBe(classId);
      await login('1990010001', 'AdminPass1234').expect(200);
    });

    it('moves STUDENT → ADMIN with an auto-issued identity and deactivates enrollment', async () => {
      const admin = await adminSession();
      await createClass();
      const studentId = await createStudentViaApi(admin.token, '2025000001');

      const response = await patchUser(admin.token, studentId.id, {
        role: 'ADMIN',
        // supplied academic id must be ignored for the Admin target
        loginId: '2025000002',
      }).expect(200);

      expect(response.body.data.role).toBe('ADMIN');
      expect(ADMIN_LOGIN_ID_PATTERN.test(response.body.data.loginId)).toBe(true);
      expect(response.body.data.loginId).not.toBe('2025000002');

      const enrollment = await getPool().query<{ status: string; left_at: Date | null }>(
        'SELECT status, left_at FROM enrollments WHERE student_id = $1',
        [studentId.id],
      );
      expect(enrollment.rows[0]!.status).toBe('LEFT');
      expect(enrollment.rows[0]!.left_at).not.toBeNull();

      // Supplied academic NIM was not applied; the allocated Admin id works.
      await login('2025000002', STUDENT_PASSWORD).expect(401);
      await login(response.body.data.loginId, STUDENT_PASSWORD).expect(200);
    });

    it('moves LECTURER → ADMIN and removes teaching assignments', async () => {
      const admin = await adminSession();
      const classId = await createClass();
      const lecturerId = await createUser({
        loginId: '1990010001',
        password: LECTURER_PASSWORD,
        role: 'LECTURER',
      });
      await assignLecturer(classId, lecturerId);

      const response = await patchUser(admin.token, lecturerId, {
        role: 'ADMIN',
      }).expect(200);
      expect(response.body.data.role).toBe('ADMIN');
      expect(ADMIN_LOGIN_ID_PATTERN.test(response.body.data.loginId)).toBe(true);

      const assignments = await getPool().query<{ count: string }>(
        'SELECT count(*)::text AS count FROM teaching_assignments WHERE lecturer_id = $1',
        [lecturerId],
      );
      expect(Number(assignments.rows[0]!.count)).toBe(0);
    });

    it('moves STUDENT → LECTURER while keeping the NIM/NIDN', async () => {
      const admin = await adminSession();
      const classId = await createClass();
      const studentId = await createStudentViaApi(admin.token, '1990010001');

      const response = await patchUser(admin.token, studentId.id, {
        role: 'LECTURER',
      }).expect(200);

      expect(response.body.data.role).toBe('LECTURER');
      expect(response.body.data.loginId).toBe('1990010001');
      const assignment = await getPool().query<{ class_id: string }>(
        'SELECT class_id FROM teaching_assignments WHERE lecturer_id = $1',
        [studentId.id],
      );
      expect(assignment.rows[0]!.class_id).toBe(classId);
    });

    it('keeps self-demotion and last-active-admin protections', async () => {
      const admin = await adminSession();
      await createClass();

      const self = await patchUser(admin.token, admin.userId, {
        role: 'STUDENT',
        loginId: '2025000001',
      }).expect(409);
      expect(self.body.error.code).toBe('LAST_ADMIN_PROTECTED');

      const last = await request(app)
        .post('/api/v1/admin/users/' + admin.userId + '/deactivate')
        .set('Authorization', bearer(admin.token))
        .expect(409);
      expect(last.body.error.code).toBe('LAST_ADMIN_PROTECTED');
    });
  });

  // -------------------------------------------------------------------
  // Idempotent seed with automatic identity allocation
  // -------------------------------------------------------------------
  describe('seed-admin', () => {
    it('allocates once and is idempotent on reseed', async () => {
      const first = await seedAdmin({
        fullName: 'Administrator',
        password: 'ChangeMe123456',
      });
      expect(first.created).toBe(true);
      expect(first.loginId).toBe('01401400001');

      const second = await seedAdmin({
        fullName: 'Administrator',
        password: 'ChangeMe123456',
      });
      expect(second.created).toBe(false);
      expect(second.loginId).toBe('01401400001');

      const count = await getPool().query<{ count: string }>(
        "SELECT count(*)::text AS count FROM users WHERE role = 'ADMIN'",
      );
      expect(Number(count.rows[0]!.count)).toBe(1);
    });

    it('reuses gaps instead of colliding with existing identities', async () => {
      await createUser({
        loginId: '01401400001',
        password: 'Someone12345',
        role: 'STUDENT',
      });
      await createUser({
        loginId: '01401400003',
        password: 'Someone12345',
        role: 'STUDENT',
      });

      const seeded = await seedAdmin({
        fullName: 'Administrator',
        password: 'ChangeMe123456',
      });
      expect(seeded.created).toBe(true);
      expect(seeded.loginId).toBe('01401400002');
    });
  });

  // -------------------------------------------------------------------
  // Migration 002: backfill, constraints, and rollback simulation
  // -------------------------------------------------------------------
  describe('migration 002 on a disposable database', () => {
    const scratchName = databaseName() + '_scratch';

    afterAll(async () => {
      await closePool();
      await dropDatabase(scratchName);
    });

    it(
      'backfills legacy Admins, skips collisions, and preserves UUIDs/relations; rollback restores pre-002 state',
      async () => {
        await recreateDatabase(scratchName);
        const client = new Client({ connectionString: connectionStringFor(scratchName) });
        await client.connect();
        let before: string | undefined;

        try {
          await client.query(migrationSql(MIGRATION_001));
          const fixture = await createLegacyFixture(client);
          before = await usersSnapshot(client);

          await client.query(migrationSql(MIGRATION_002));

          const admin = await client.query<{ login_id: string; id: string }>(
            'SELECT id, login_id FROM users WHERE id = $1',
            [fixture.adminId],
          );
          // 01401400001 is taken by the academic collision fixture, so the
          // first legacy Admin gets the next free number.
          expect(admin.rows[0]!.id).toBe(fixture.adminId);
          expect(admin.rows[0]!.login_id).toBe('01401400002');
          expect(ADMIN_LOGIN_ID_PATTERN.test(admin.rows[0]!.login_id)).toBe(true);

          // Deterministic ordering: no other Admin rows existed.
          const adminCount = await client.query<{ count: string }>(
            "SELECT count(*)::text AS count FROM users WHERE role = 'ADMIN'",
          );
          expect(Number(adminCount.rows[0]!.count)).toBe(1);

          // Relations still point at the same UUIDs.
          const relations = await client.query<{
            enrollment_student: string;
            teaching_lecturer: string;
            topic_created_by: string;
            case_created_by: string;
            submission_student: string;
            assessment_by: string;
            session_user: string;
            audit_actor: string;
          }>(
            `SELECT e.student_id::text AS enrollment_student,
                    t.lecturer_id::text AS teaching_lecturer,
                    tp.created_by::text AS topic_created_by,
                    cs.created_by::text AS case_created_by,
                    s.student_id::text AS submission_student,
                    a.assessed_by::text AS assessment_by,
                    ses.user_id::text AS session_user,
                    al.actor_id::text AS audit_actor
             FROM enrollments e,
                  teaching_assignments t,
                  topics tp,
                  case_studies cs,
                  submissions s,
                  assessments a,
                  auth_sessions ses,
                  audit_logs al
             WHERE e.id = $1 AND t.id = $2 AND tp.id = $3 AND cs.id = $4
               AND s.id = $5 AND a.id = $6 AND ses.id = $7 AND al.id = $8`,
            [
              fixture.enrollmentId,
              fixture.teachingAssignmentId,
              fixture.topicId,
              fixture.caseStudyId,
              fixture.submissionId,
              fixture.assessmentId,
              fixture.sessionId,
              fixture.auditId,
            ],
          );
          const row = relations.rows[0]!;
          expect(row.enrollment_student).toBe(fixture.studentId);
          expect(row.teaching_lecturer).toBe(fixture.lecturerId);
          expect(row.topic_created_by).toBe(fixture.lecturerId);
          expect(row.case_created_by).toBe(fixture.lecturerId);
          expect(row.submission_student).toBe(fixture.studentId);
          expect(row.assessment_by).toBe(fixture.lecturerId);
          expect(row.session_user).toBe(fixture.adminId);
          expect(row.audit_actor).toBe(fixture.adminId);

          // Only the legacy Admin identifier changed; all other logins stay.
          const untouched = await client.query<{ count: string }>(
            `SELECT count(*)::text AS count FROM users
             WHERE id <> $1 AND login_id IN
               ('2025000001', '1990010001', '01401400001')`,
            [fixture.adminId],
          );
          expect(Number(untouched.rows[0]!.count)).toBe(3);

          // Constraint blocks Admin-shaped violations but allows academic ids.
          await expect(
            client.query(
              `INSERT INTO users (login_id, password_hash, full_name, role)
               VALUES ('legacy-admin', 'x', 'Bad', 'ADMIN')`,
            ),
          ).rejects.toThrow(/ck_users_admin_login_id/);
          await client.query(
            `INSERT INTO users (login_id, password_hash, full_name, role)
             VALUES ('2025000002', 'x', 'Fine', 'STUDENT')`,
          );
        } finally {
          await client.end();
        }

        // --- Rollback simulation ---------------------------------------
        // Snapshot pre-002 exists (`before`); drop the migrated database,
        // recreate it, apply only migration 001 and the same fixture, and
        // prove the original state is back.
        await recreateDatabase(scratchName);
        const restored = new Client({
          connectionString: connectionStringFor(scratchName),
        });
        await restored.connect();
        try {
          await restored.query(migrationSql(MIGRATION_001));
          await createLegacyFixture(restored);

          const after = await usersSnapshot(restored);
          expect(after).toBe(before);

          const legacy = await restored.query<{ login_id: string }>(
            "SELECT login_id FROM users WHERE role = 'ADMIN'",
          );
          expect(legacy.rows[0]!.login_id).toBe('admin');

          const constraint = await restored.query<{ count: string }>(
            `SELECT count(*)::text AS count FROM pg_constraint
             WHERE conname = 'ck_users_admin_login_id'`,
          );
          expect(Number(constraint.rows[0]!.count)).toBe(0);

          // Pre-002 schema also allows academic ids and old admin usernames.
          await restored.query(
            `INSERT INTO users (login_id, password_hash, full_name, role)
             VALUES ('admin2', 'x', 'Legacy Again', 'ADMIN')`,
          );
        } finally {
          await restored.end();
        }
      },
      120_000,
    );
  });
});
