import type { PoolClient } from 'pg';
import type { VarkType } from '../../types/auth';

/**
 * Satu baris gabungan materi + topik + kelas yang dibutuhkan untuk menilai
 * izin unduh pada SETIAP permintaan baru (FR-015, DEC-001).
 */
export interface MaterialAccessRow {
  id: string;
  topic_id: string;
  uploaded_by: string;
  vark_type: VarkType;
  title: string;
  kind: 'FILE' | 'LINK';
  object_key: string | null;
  url: string | null;
  mime: string | null;
  size_bytes: string | null;
  uploaded_at: Date;
  deleted_at: Date | null;
  topic_status: 'DRAFT' | 'PUBLISHED';
  topic_deleted_at: Date | null;
  class_id: string;
  class_is_active: boolean;
}

export async function findMaterialForAccess(
  client: PoolClient,
  materialId: string,
): Promise<MaterialAccessRow | null> {
  const result = await client.query<MaterialAccessRow>(
    `
      SELECT
        m.id, m.topic_id, m.uploaded_by, m.vark_type, m.title, m.kind,
        m.object_key, m.url, m.mime, m.size_bytes, m.uploaded_at, m.deleted_at,
        t.status AS topic_status, t.deleted_at AS topic_deleted_at,
        t.class_id, c.is_active AS class_is_active
      FROM materials m
      JOIN topics t ON t.id = m.topic_id
      JOIN classes c ON c.id = t.class_id
      WHERE m.id = $1
    `,
    [materialId],
  );
  return result.rows[0] ?? null;
}

export async function hasActiveEnrollment(
  client: PoolClient,
  classId: string,
  studentId: string,
): Promise<boolean> {
  const result = await client.query<{ allowed: boolean }>(
    `
      SELECT EXISTS (
        SELECT 1
        FROM enrollments e
        JOIN classes c ON c.id = e.class_id
        WHERE e.class_id = $1
          AND e.student_id = $2
          AND e.status = 'ACTIVE'
          AND c.is_active = true
      ) AS allowed
    `,
    [classId, studentId],
  );
  return result.rows[0]?.allowed === true;
}

export async function hasTeachingAssignment(
  client: PoolClient,
  classId: string,
  lecturerId: string,
): Promise<boolean> {
  const result = await client.query<{ allowed: boolean }>(
    `
      SELECT EXISTS (
        SELECT 1
        FROM teaching_assignments ta
        JOIN classes c ON c.id = ta.class_id
        WHERE ta.class_id = $1
          AND ta.lecturer_id = $2
          AND c.is_active = true
      ) AS allowed
    `,
    [classId, lecturerId],
  );
  return result.rows[0]?.allowed === true;
}
