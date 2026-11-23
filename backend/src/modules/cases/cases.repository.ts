import type { PoolClient } from 'pg';

export interface CaseRow {
  id: string;
  topic_id: string;
  theory: string | null;
  instructions: string;
  deadline_at: Date;
  created_at: Date;
  updated_at: Date;
  before_deadline: boolean;
  submission_id: string | null;
  submitted_at: Date | null;
  score: number | null;
  comment: string | null;
  answer_text: string | null;
  assessed_at: Date | null;
  assessed_by_name: string | null;
}

export async function findCaseForStudent(
  client: PoolClient,
  topicId: string,
  studentId: string,
): Promise<CaseRow | null> {
  const result = await client.query<CaseRow>(
    `
      SELECT
        cs.id, cs.topic_id, cs.theory, cs.instructions, cs.deadline_at,
        cs.created_at, cs.updated_at,
        (now() <= cs.deadline_at) AS before_deadline,
        s.id AS submission_id,
        s.submitted_at,
        s.answer_text,
        a.score,
        a.comment,
        a.assessed_at,
        grader.full_name AS assessed_by_name
      FROM case_studies cs
      JOIN topics t
        ON t.id = cs.topic_id
       AND t.status = 'PUBLISHED'
       AND t.deleted_at IS NULL
      JOIN classes c ON c.id = t.class_id AND c.is_active = true
      JOIN enrollments e
        ON e.class_id = t.class_id
       AND e.student_id = $2
       AND e.status = 'ACTIVE'
      LEFT JOIN submissions s
        ON s.case_study_id = cs.id
       AND s.student_id = $2
      LEFT JOIN assessments a ON a.submission_id = s.id
      LEFT JOIN users grader ON grader.id = a.assessed_by
      WHERE cs.topic_id = $1 AND cs.deleted_at IS NULL
      LIMIT 1
    `,
    [topicId, studentId],
  );
  return result.rows[0] ?? null;
}

export async function findCaseForLecturer(
  client: PoolClient,
  topicId: string,
  lecturerId: string,
): Promise<CaseRow | null> {
  const result = await client.query<CaseRow>(
    `
      SELECT
        cs.id, cs.topic_id, cs.theory, cs.instructions, cs.deadline_at,
        cs.created_at, cs.updated_at,
        (now() <= cs.deadline_at) AS before_deadline,
        NULL::uuid AS submission_id,
        NULL::timestamptz AS submitted_at,
        NULL::integer AS score,
        NULL::text AS comment,
        NULL::text AS answer_text,
        NULL::timestamptz AS assessed_at,
        NULL::text AS assessed_by_name
      FROM case_studies cs
      JOIN topics t ON t.id = cs.topic_id AND t.deleted_at IS NULL
      JOIN teaching_assignments ta
        ON ta.class_id = t.class_id AND ta.lecturer_id = $2
      JOIN classes c ON c.id = t.class_id AND c.is_active = true
      WHERE cs.topic_id = $1 AND cs.deleted_at IS NULL
      LIMIT 1
    `,
    [topicId, lecturerId],
  );
  return result.rows[0] ?? null;
}

export async function findCaseByTopic(
  client: PoolClient,
  topicId: string,
): Promise<CaseRow | null> {
  const result = await client.query<CaseRow>(
    `
      SELECT
        cs.id, cs.topic_id, cs.theory, cs.instructions, cs.deadline_at,
        cs.created_at, cs.updated_at,
        (now() <= cs.deadline_at) AS before_deadline,
        NULL::uuid AS submission_id,
        NULL::timestamptz AS submitted_at,
        NULL::integer AS score,
        NULL::text AS comment,
        NULL::text AS answer_text,
        NULL::timestamptz AS assessed_at,
        NULL::text AS assessed_by_name
      FROM case_studies cs
      WHERE cs.topic_id = $1 AND cs.deleted_at IS NULL
      LIMIT 1
    `,
    [topicId],
  );
  return result.rows[0] ?? null;
}

export async function insertCase(
  client: PoolClient,
  input: {
    topicId: string;
    createdBy: string;
    theory: string | null;
    instructions: string;
    deadlineAt: Date;
  },
): Promise<CaseRow> {
  const result = await client.query<CaseRow>(
    `
      INSERT INTO case_studies (topic_id, created_by, theory, instructions, deadline_at)
      VALUES ($1, $2, $3, $4, $5)
      RETURNING id, topic_id, theory, instructions, deadline_at, created_at, updated_at,
                (now() <= deadline_at) AS before_deadline,
                NULL::uuid AS submission_id, NULL::timestamptz AS submitted_at,
                NULL::integer AS score, NULL::text AS comment
    `,
    [input.topicId, input.createdBy, input.theory, input.instructions, input.deadlineAt],
  );
  return result.rows[0]!;
}

export async function updateCase(
  client: PoolClient,
  caseId: string,
  input: {
    theory: string | null;
    instructions: string;
    deadlineAt: Date;
  },
): Promise<CaseRow> {
  const result = await client.query<CaseRow>(
    `
      UPDATE case_studies
      SET theory = $2, instructions = $3, deadline_at = $4, updated_at = now()
      WHERE id = $1 AND deleted_at IS NULL
      RETURNING id, topic_id, theory, instructions, deadline_at, created_at, updated_at,
                (now() <= deadline_at) AS before_deadline,
                NULL::uuid AS submission_id, NULL::timestamptz AS submitted_at,
                NULL::integer AS score, NULL::text AS comment
    `,
    [caseId, input.theory, input.instructions, input.deadlineAt],
  );
  return result.rows[0]!;
}

export async function countSubmissions(
  client: PoolClient,
  caseId: string,
): Promise<number> {
  const result = await client.query<{ count: string }>(
    'SELECT count(*)::text AS count FROM submissions WHERE case_study_id = $1',
    [caseId],
  );
  return Number(result.rows[0]?.count ?? '0');
}

export async function lockCaseForSubmission(
  client: PoolClient,
  topicId: string,
  studentId: string,
): Promise<{ caseId: string; allowed: boolean; deadlineAt: Date } | null> {
  const result = await client.query<{
    id: string;
    allowed: boolean;
    deadline_at: Date;
  }>(
    `
      SELECT cs.id, (now() <= cs.deadline_at) AS allowed, cs.deadline_at
      FROM case_studies cs
      JOIN topics t
        ON t.id = cs.topic_id
       AND t.status = 'PUBLISHED'
       AND t.deleted_at IS NULL
      JOIN classes c ON c.id = t.class_id AND c.is_active = true
      JOIN enrollments e
        ON e.class_id = t.class_id
       AND e.student_id = $2
       AND e.status = 'ACTIVE'
      WHERE cs.topic_id = $1 AND cs.deleted_at IS NULL
      FOR UPDATE OF cs
    `,
    [topicId, studentId],
  );
  const row = result.rows[0];
  if (!row) return null;
  return { caseId: row.id, allowed: row.allowed, deadlineAt: row.deadline_at };
}

export async function findSubmission(
  client: PoolClient,
  caseId: string,
  studentId: string,
): Promise<{ id: string; submitted_at: Date } | null> {
  const result = await client.query<{ id: string; submitted_at: Date }>(
    `
      SELECT id, submitted_at
      FROM submissions
      WHERE case_study_id = $1 AND student_id = $2
    `,
    [caseId, studentId],
  );
  return result.rows[0] ?? null;
}

export async function insertSubmission(
  client: PoolClient,
  input: { caseId: string; studentId: string; answerText: string },
): Promise<{ id: string; submitted_at: Date }> {
  const result = await client.query<{ id: string; submitted_at: Date }>(
    `
      INSERT INTO submissions (case_study_id, student_id, answer_text)
      VALUES ($1, $2, $3)
      RETURNING id, submitted_at
    `,
    [input.caseId, input.studentId, input.answerText],
  );
  return result.rows[0]!;
}
