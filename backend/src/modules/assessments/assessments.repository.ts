import type { PoolClient } from 'pg';

export interface CaseAccessRow {
  id: string;
  topic_id: string;
  class_id: string;
}

export interface CaseStudentRow {
  student_id: string;
  full_name: string;
  login_id: string;
  submission_id: string | null;
  submitted_at: Date | null;
  score: number | null;
  before_deadline: boolean;
}

export interface SubmissionDetailRow {
  id: string;
  case_study_id: string;
  topic_id: string;
  class_id: string;
  student_id: string;
  student_name: string;
  enrollment_status: 'ACTIVE' | 'LEFT' | null;
  answer_text: string;
  submitted_at: Date;
  assessment_id: string | null;
  score: number | null;
  comment: string | null;
  assessed_at: Date | null;
}

export async function findCaseById(
  client: PoolClient,
  caseId: string,
): Promise<CaseAccessRow | null> {
  const result = await client.query<CaseAccessRow>(
    `
      SELECT cs.id, cs.topic_id, t.class_id
      FROM case_studies cs
      JOIN topics t ON t.id = cs.topic_id AND t.deleted_at IS NULL
      WHERE cs.id = $1 AND cs.deleted_at IS NULL
    `,
    [caseId],
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

export async function listCaseStudents(
  client: PoolClient,
  caseId: string,
): Promise<CaseStudentRow[]> {
  const result = await client.query<CaseStudentRow>(
    `
      WITH target AS (
        SELECT
          cs.id AS case_id,
          t.class_id,
          (now() <= cs.deadline_at) AS before_deadline
        FROM case_studies cs
        JOIN topics t ON t.id = cs.topic_id AND t.deleted_at IS NULL
        WHERE cs.id = $1 AND cs.deleted_at IS NULL
      ),
      -- Every student that has a stored answer must always be listed, even when
      -- the enrollment, account or class changed after the submission was made.
      -- Otherwise the aggregate count and this list disagree.
      candidates AS (
        SELECT e.student_id
        FROM target tg
        JOIN enrollments e ON e.class_id = tg.class_id
        UNION
        SELECT s.student_id
        FROM target tg
        JOIN submissions s ON s.case_study_id = tg.case_id
      )
      SELECT
        u.id AS student_id,
        u.full_name,
        u.login_id,
        s.id AS submission_id,
        s.submitted_at,
        a.score,
        tg.before_deadline
      FROM target tg
      JOIN candidates cand ON TRUE
      JOIN users u ON u.id = cand.student_id
      LEFT JOIN enrollments e
        ON e.class_id = tg.class_id
       AND e.student_id = u.id
      LEFT JOIN submissions s
        ON s.case_study_id = tg.case_id
       AND s.student_id = u.id
      LEFT JOIN assessments a ON a.submission_id = s.id
      WHERE (
          -- A student who already submitted always stays visible, regardless of
          -- a revoked Kinesthetic session or a later inactive enrollment.
          s.id IS NOT NULL
          OR (
            e.status = 'ACTIVE'
            AND u.status = 'ACTIVE'
            AND EXISTS (
              SELECT 1
              FROM classes c
              WHERE c.id = tg.class_id AND c.is_active = true
            )
            AND EXISTS (
              SELECT 1
              FROM auth_sessions sess
              WHERE sess.user_id = u.id
                AND sess.is_active = true
                AND sess.vark_type = 'KINESTHETIC'
            )
          )
        )
      ORDER BY u.full_name
    `,
    [caseId],
  );
  return result.rows;
}

export async function findSubmissionDetail(
  client: PoolClient,
  submissionId: string,
): Promise<SubmissionDetailRow | null> {
  const result = await client.query<SubmissionDetailRow>(
    `
      SELECT
        s.id,
        s.case_study_id,
        cs.topic_id,
        t.class_id,
        s.student_id,
        u.full_name AS student_name,
        (SELECT e.status
           FROM enrollments e
          WHERE e.class_id = t.class_id
            AND e.student_id = s.student_id
          LIMIT 1) AS enrollment_status,
        s.answer_text,
        s.submitted_at,
        a.id AS assessment_id,
        a.score,
        a.comment,
        a.assessed_at
      FROM submissions s
      JOIN case_studies cs ON cs.id = s.case_study_id AND cs.deleted_at IS NULL
      JOIN topics t ON t.id = cs.topic_id AND t.deleted_at IS NULL
      JOIN users u ON u.id = s.student_id
      LEFT JOIN assessments a ON a.submission_id = s.id
      WHERE s.id = $1
    `,
    [submissionId],
  );
  return result.rows[0] ?? null;
}

export async function insertAssessment(
  client: PoolClient,
  input: {
    submissionId: string;
    lecturerId: string;
    score: number;
    comment: string | null;
  },
): Promise<{ id: string; assessed_at: Date }> {
  const result = await client.query<{ id: string; assessed_at: Date }>(
    `
      INSERT INTO assessments (submission_id, assessed_by, score, comment)
      VALUES ($1, $2, $3, $4)
      RETURNING id, assessed_at
    `,
    [input.submissionId, input.lecturerId, input.score, input.comment],
  );
  return result.rows[0]!;
}
