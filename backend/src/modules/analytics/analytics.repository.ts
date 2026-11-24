import type { PoolClient } from 'pg';

export type FeedbackCategory = 'VISUAL' | 'AURAL' | 'READ_WRITE';

export interface TopicClassRow {
  class_id: string;
}

export interface ClassStudentRow {
  student_id: string;
  full_name: string;
  login_id: string;
}

export interface StoredFeedbackRow {
  student_id: string;
  vark_type: FeedbackCategory;
  value: number;
  updated_at: Date;
}

export interface KinestheticScoreRow {
  score: number;
}

export async function findTopicClass(
  client: PoolClient,
  topicId: string,
): Promise<TopicClassRow | null> {
  const result = await client.query<TopicClassRow>(
    `
      SELECT class_id
      FROM topics
      WHERE id = $1 AND deleted_at IS NULL
    `,
    [topicId],
  );
  return result.rows[0] ?? null;
}

export async function isLecturerForClass(
  client: PoolClient,
  classId: string,
  lecturerId: string,
): Promise<boolean> {
  const result = await client.query(
    `
      SELECT 1
      FROM teaching_assignments
      WHERE class_id = $1 AND lecturer_id = $2
      LIMIT 1
    `,
    [classId, lecturerId],
  );
  return (result.rowCount ?? 0) > 0;
}

/**
 * Populasi kelas untuk daftar feedback dan field kompatibilitas
 * `notRespondedCount`: mahasiswa dengan enrollment ACTIVE dan akun ACTIVE.
 *
 * Status sesi/VARK login terakhir sengaja tidak dipakai lagi. FR-030 meminta
 * feedback ditampilkan menurut kategori asal nilai tersimpan, sehingga mahasiswa
 * yang sedang logout atau login dengan VARK lain tetap harus muncul pada filter
 * historisnya.
 */
export async function listClassStudents(
  client: PoolClient,
  input: { classId: string; studentId: string | null },
): Promise<ClassStudentRow[]> {
  const result = await client.query<ClassStudentRow>(
    `
      SELECT
        u.id AS student_id,
        u.full_name,
        u.login_id
      FROM enrollments e
      JOIN users u ON u.id = e.student_id AND u.status = 'ACTIVE'
      WHERE e.class_id = $1
        AND e.status = 'ACTIVE'
        AND ($2::uuid IS NULL OR u.id = $2)
      ORDER BY u.full_name, u.id
    `,
    [input.classId, input.studentId],
  );
  return result.rows;
}

/**
 * Seluruh feedback tersimpan pada topik, tanpa memandang kategori VARK pada sesi
 * login terakhir. Nilai per kategori asal dipakai untuk filter historis FR-030.
 */
export async function listStoredFeedback(
  client: PoolClient,
  input: { topicId: string; studentId: string | null },
): Promise<StoredFeedbackRow[]> {
  const result = await client.query<StoredFeedbackRow>(
    `
      SELECT
        f.student_id,
        f.vark_type,
        f.value,
        f.updated_at
      FROM feedback f
      WHERE f.topic_id = $1
        AND ($2::uuid IS NULL OR f.student_id = $2)
      ORDER BY f.student_id, f.updated_at, f.vark_type
    `,
    [input.topicId, input.studentId],
  );
  return result.rows;
}

export interface LegacyPopulationRow {
  student_id: string;
}

/**
 * Populasi field kompatibilitas `notRespondedCount` dengan definisi existing
 * (sebelum FR-030): mahasiswa dengan enrollment ACTIVE di kelas yang memiliki
 * nilai feedback pada topik untuk filter ATAU sesi aktif terakhir ber-VARK
 * Visual/Audio/Read-Write yang cocok dengan filter. Mahasiswa dengan sesi
 * Kinesthetic tidak masuk populasi ini. Query terpisah agar statistik kategori
 * asal FR-029–033 (yang memakai seluruh populasi kelas) tidak berubah.
 */
export async function listLegacyFeedbackPopulation(
  client: PoolClient,
  input: {
    topicId: string;
    classId: string;
    category: FeedbackCategory | null;
    studentId: string | null;
  },
): Promise<LegacyPopulationRow[]> {
  const result = await client.query<LegacyPopulationRow>(
    `
      SELECT DISTINCT u.id AS student_id
      FROM enrollments e
      JOIN users u ON u.id = e.student_id AND u.status = 'ACTIVE'
      LEFT JOIN LATERAL (
        SELECT s.vark_type
        FROM auth_sessions s
        WHERE s.user_id = u.id
          AND s.is_active = true
          AND s.revoked_at IS NULL
          AND s.vark_type IS NOT NULL
        ORDER BY s.last_seen_at DESC
        LIMIT 1
      ) sess ON true
      LEFT JOIN feedback f
        ON f.student_id = u.id
       AND f.topic_id = $1
       AND ($3::text IS NULL OR f.vark_type = $3)
      WHERE e.class_id = $2
        AND e.status = 'ACTIVE'
        AND (
          f.value IS NOT NULL
          OR (
            sess.vark_type IN ('VISUAL', 'AURAL', 'READ_WRITE')
            AND ($3::text IS NULL OR sess.vark_type = $3)
          )
        )
        AND ($4::uuid IS NULL OR u.id = $4)
    `,
    [input.topicId, input.classId, input.category, input.studentId],
  );
  return result.rows;
}

/**
 * Seluruh nilai final Kinesthetic pada studi kasus topik. Sumbernya join
 * `submissions` + `assessments` sehingga agregat tidak bergantung pada
 * enrollment ACTIVE maupun sesi VARK Kinesthetic yang sedang aktif
 * (FR-032/DEC-011). Otorisasi dosen per kelas tetap diperiksa di service.
 */
export async function listKinestheticScores(
  client: PoolClient,
  topicId: string,
): Promise<KinestheticScoreRow[]> {
  const result = await client.query<KinestheticScoreRow>(
    `
      SELECT a.score
      FROM case_studies cs
      JOIN submissions sub ON sub.case_study_id = cs.id
      JOIN assessments a ON a.submission_id = sub.id
      WHERE cs.topic_id = $1
        AND cs.deleted_at IS NULL
      ORDER BY a.assessed_at, a.id
    `,
    [topicId],
  );
  return result.rows;
}

/**
 * Field kompatibilitas API `notSubmittedCount`. Definisi dipertahankan seperti
 * implementasi sebelumnya: mahasiswa dengan enrollment ACTIVE dan sesi
 * Kinesthetic aktif yang belum mengirim jawaban studi kasus topik ini.
 * Field ini di luar metrik resmi dosen (C1-05) dan terpisah dari status
 * individu FR-048.
 */
export async function countKinestheticNotSubmitted(
  client: PoolClient,
  input: { topicId: string; classId: string },
): Promise<number> {
  const result = await client.query<{ count: string }>(
    `
      SELECT count(*)::text AS count
      FROM enrollments e
      JOIN users u ON u.id = e.student_id AND u.status = 'ACTIVE'
      LEFT JOIN LATERAL (
        SELECT s.vark_type
        FROM auth_sessions s
        WHERE s.user_id = u.id
          AND s.is_active = true
          AND s.revoked_at IS NULL
          AND s.vark_type = 'KINESTHETIC'
        ORDER BY s.last_seen_at DESC
        LIMIT 1
      ) sess ON true
      LEFT JOIN case_studies cs
        ON cs.topic_id = $1
       AND cs.deleted_at IS NULL
      LEFT JOIN submissions sub
        ON sub.case_study_id = cs.id
       AND sub.student_id = u.id
      WHERE e.class_id = $2
        AND e.status = 'ACTIVE'
        AND sess.vark_type = 'KINESTHETIC'
        AND sub.id IS NULL
    `,
    [input.topicId, input.classId],
  );
  return Number(result.rows[0]?.count ?? '0');
}
