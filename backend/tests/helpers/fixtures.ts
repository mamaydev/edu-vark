import { getPool } from '../../src/db/client';

export async function createTeachingAssignment(
  classId: string,
  lecturerId: string,
): Promise<string> {
  const result = await getPool().query<{ id: string }>(
    `
      INSERT INTO teaching_assignments (class_id, lecturer_id)
      VALUES ($1, $2)
      RETURNING id
    `,
    [classId, lecturerId],
  );
  return result.rows[0]?.id ?? '';
}

export async function createEnrollment(
  classId: string,
  studentId: string,
): Promise<string> {
  const result = await getPool().query<{ id: string }>(
    `
      INSERT INTO enrollments (class_id, student_id, status)
      VALUES ($1, $2, 'ACTIVE')
      RETURNING id
    `,
    [classId, studentId],
  );
  return result.rows[0]?.id ?? '';
}

export async function createTopic(input: {
  classId: string;
  createdBy: string;
  title: string;
  status?: 'DRAFT' | 'PUBLISHED';
  sortOrder?: number;
}): Promise<string> {
  const result = await getPool().query<{ id: string }>(
    `
      INSERT INTO topics (class_id, created_by, title, sort_order, status)
      VALUES ($1, $2, $3, $4, $5)
      RETURNING id
    `,
    [
      input.classId,
      input.createdBy,
      input.title,
      input.sortOrder ?? 0,
      input.status ?? 'DRAFT',
    ],
  );
  return result.rows[0]?.id ?? '';
}

export async function createMaterialLink(input: {
  topicId: string;
  uploadedBy: string;
  vark: 'VISUAL' | 'AURAL' | 'READ_WRITE' | 'KINESTHETIC';
  title: string;
  url: string;
}): Promise<string> {
  const result = await getPool().query<{ id: string }>(
    `
      INSERT INTO materials (topic_id, uploaded_by, vark_type, title, kind, url)
      VALUES ($1, $2, $3, $4, 'LINK', $5)
      RETURNING id
    `,
    [input.topicId, input.uploadedBy, input.vark, input.title, input.url],
  );
  return result.rows[0]?.id ?? '';
}

export async function createCaseWithSubmission(input: {
  topicId: string;
  lecturerId: string;
  studentId: string;
}): Promise<string> {
  const caseResult = await getPool().query<{ id: string }>(
    `
      INSERT INTO case_studies (topic_id, created_by, instructions, deadline_at)
      VALUES ($1, $2, 'Kerjakan studi kasus', now() + interval '1 day')
      RETURNING id
    `,
    [input.topicId, input.lecturerId],
  );
  const caseId = caseResult.rows[0]?.id;
  if (!caseId) throw new Error('Failed to create case study');

  await getPool().query(
    `
      INSERT INTO submissions (case_study_id, student_id, answer_text)
      VALUES ($1, $2, 'Jawaban uji')
    `,
    [caseId, input.studentId],
  );

  return caseId;
}
