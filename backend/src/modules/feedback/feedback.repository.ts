import type { PoolClient } from 'pg';
import type { VarkType } from '../../types/auth';

export interface FeedbackRow {
  value: number;
  updated_at: Date;
}

export async function findFeedback(
  client: PoolClient,
  studentId: string,
  topicId: string,
  vark: VarkType,
): Promise<FeedbackRow | null> {
  const result = await client.query<FeedbackRow>(
    `
      SELECT value, updated_at
      FROM feedback
      WHERE student_id = $1 AND topic_id = $2 AND vark_type = $3
    `,
    [studentId, topicId, vark],
  );
  return result.rows[0] ?? null;
}

export async function upsertFeedback(
  client: PoolClient,
  input: {
    studentId: string;
    topicId: string;
    vark: VarkType;
    value: number;
  },
): Promise<FeedbackRow> {
  const result = await client.query<FeedbackRow>(
    `
      INSERT INTO feedback (student_id, topic_id, vark_type, value)
      VALUES ($1, $2, $3, $4)
      ON CONFLICT (student_id, topic_id, vark_type)
      DO UPDATE SET value = EXCLUDED.value, updated_at = now()
      RETURNING value, updated_at
    `,
    [input.studentId, input.topicId, input.vark, input.value],
  );
  return result.rows[0]!;
}
