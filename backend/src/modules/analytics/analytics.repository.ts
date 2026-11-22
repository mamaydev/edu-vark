import type { PoolClient } from 'pg';

export interface TopicClassRow {
  class_id: string;
}

export interface StudentRow {
  id: string;
  full_name: string;
  login_id: string;
  vark_type: 'VISUAL' | 'AURAL' | 'READ_WRITE' | 'KINESTHETIC';
}

export interface FeedbackAnalyticsRow {
  student_id: string;
  full_name: string;
  login_id: string;
  vark_type: 'VISUAL' | 'AURAL' | 'READ_WRITE';
  feedback_vark: 'VISUAL' | 'AURAL' | 'READ_WRITE' | null;
  value: number | null;
}

export interface KinestheticAnalyticsRow {
  student_id: string;
  submission_id: string | null;
  score: number | null;
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

export async function findStudentInClass(
  client: PoolClient,
  classId: string,
  studentId: string,
): Promise<StudentRow | null> {
  const result = await client.query<StudentRow>(
    `
      SELECT u.id, u.full_name, u.login_id, sess.vark_type
      FROM enrollments e
      JOIN users u ON u.id = e.student_id AND u.status = 'ACTIVE'
      JOIN auth_sessions sess
        ON sess.user_id = u.id
       AND sess.is_active = true
       AND sess.vark_type IS NOT NULL
      WHERE e.class_id = $1
        AND e.student_id = $2
        AND e.status = 'ACTIVE'
      LIMIT 1
    `,
    [classId, studentId],
  );
  return result.rows[0] ?? null;
}

export async function listFeedbackAnalytics(
  client: PoolClient,
  input: {
    topicId: string;
    classId: string;
    category: 'VISUAL' | 'AURAL' | 'READ_WRITE' | null;
    studentId: string | null;
  },
): Promise<FeedbackAnalyticsRow[]> {
  const result = await client.query<FeedbackAnalyticsRow>(
    `
      SELECT
        u.id AS student_id,
        u.full_name,
        u.login_id,
        sess.vark_type,
        f.vark_type AS feedback_vark,
        f.value
      FROM enrollments e
      JOIN users u ON u.id = e.student_id AND u.status = 'ACTIVE'
      JOIN auth_sessions sess
        ON sess.user_id = u.id
       AND sess.is_active = true
       AND sess.vark_type IN ('VISUAL', 'AURAL', 'READ_WRITE')
      LEFT JOIN feedback f
        ON f.student_id = u.id
       AND f.topic_id = $1
      WHERE e.class_id = $2
        AND e.status = 'ACTIVE'
        AND ($3::text IS NULL OR sess.vark_type = $3)
        AND ($4::uuid IS NULL OR u.id = $4)
      ORDER BY u.full_name, f.vark_type
    `,
    [input.topicId, input.classId, input.category, input.studentId],
  );
  return result.rows;
}

export async function listKinestheticAnalytics(
  client: PoolClient,
  input: { topicId: string; classId: string },
): Promise<KinestheticAnalyticsRow[]> {
  const result = await client.query<KinestheticAnalyticsRow>(
    `
      SELECT
        u.id AS student_id,
        sub.id AS submission_id,
        a.score
      FROM enrollments e
      JOIN users u ON u.id = e.student_id AND u.status = 'ACTIVE'
      JOIN auth_sessions sess
        ON sess.user_id = u.id
       AND sess.is_active = true
       AND sess.vark_type = 'KINESTHETIC'
      LEFT JOIN case_studies cs
        ON cs.topic_id = $1
       AND cs.deleted_at IS NULL
      LEFT JOIN submissions sub
        ON sub.case_study_id = cs.id
       AND sub.student_id = u.id
      LEFT JOIN assessments a ON a.submission_id = sub.id
      WHERE e.class_id = $2 AND e.status = 'ACTIVE'
      ORDER BY u.full_name
    `,
    [input.topicId, input.classId],
  );
  return result.rows;
}
