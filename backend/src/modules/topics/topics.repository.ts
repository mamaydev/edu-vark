import type { PoolClient } from 'pg';
import type { VarkType } from '../../types/auth';

export interface TopicRow {
  id: string;
  class_id: string;
  created_by: string;
  title: string;
  description: string | null;
  sort_order: number;
  status: 'DRAFT' | 'PUBLISHED';
  created_at: Date;
  updated_at: Date;
  deleted_at: Date | null;
  material_available?: boolean;
}

export interface MaterialRow {
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
}

export async function findLecturerClassId(
  client: PoolClient,
  lecturerId: string,
): Promise<string | null> {
  const result = await client.query<{ id: string }>(
    `
      SELECT c.id
      FROM classes c
      JOIN teaching_assignments ta ON ta.class_id = c.id
      WHERE c.is_active = true AND ta.lecturer_id = $1
      LIMIT 1
    `,
    [lecturerId],
  );
  return result.rows[0]?.id ?? null;
}

export async function findStudentClassId(
  client: PoolClient,
  studentId: string,
): Promise<string | null> {
  const result = await client.query<{ id: string }>(
    `
      SELECT c.id
      FROM classes c
      JOIN enrollments e ON e.class_id = c.id
      WHERE c.is_active = true
        AND e.student_id = $1
        AND e.status = 'ACTIVE'
      LIMIT 1
    `,
    [studentId],
  );
  return result.rows[0]?.id ?? null;
}

export interface StudentDashboardRow {
  available_materials: number;
  not_completed_materials: number;
}

export async function getStudentDashboard(
  client: PoolClient,
  classId: string,
  studentId: string,
  vark: VarkType,
): Promise<StudentDashboardRow> {
  const result = await client.query<StudentDashboardRow>(
    `
      WITH available AS (
        SELECT t.id
        FROM topics t
        WHERE t.class_id = $1
          AND t.status = 'PUBLISHED'
          AND t.deleted_at IS NULL
          AND EXISTS (
            SELECT 1 FROM materials m
            WHERE m.topic_id = t.id
              AND m.vark_type = $2
              AND m.deleted_at IS NULL
          )
      )
      SELECT
        COUNT(*)::int AS available_materials,
        COUNT(*) FILTER (
          WHERE ($2 = 'KINESTHETIC' AND NOT EXISTS (
            SELECT 1
            FROM submissions s
            JOIN case_studies cs ON cs.id = s.case_study_id AND cs.deleted_at IS NULL
            WHERE cs.topic_id = available.id AND s.student_id = $3
          ))
          OR ($2 <> 'KINESTHETIC' AND NOT EXISTS (
            SELECT 1 FROM feedback f
            WHERE f.topic_id = available.id
              AND f.student_id = $3
              AND f.vark_type = $2
          ))
        )::int AS not_completed_materials
      FROM available
    `,
    [classId, vark, studentId],
  );
  return result.rows[0] ?? { available_materials: 0, not_completed_materials: 0 };
}

export async function listTopicsForStudent(
  client: PoolClient,
  classId: string,
  vark: VarkType,
  limit: number,
  offset: number,
): Promise<TopicRow[]> {
  const result = await client.query<TopicRow>(
    `
      SELECT
        t.id, t.class_id, t.created_by, t.title, t.description,
        t.sort_order, t.status, t.created_at, t.updated_at, t.deleted_at,
        EXISTS (
          SELECT 1 FROM materials m
          WHERE m.topic_id = t.id
            AND m.vark_type = $2
            AND m.deleted_at IS NULL
        ) AS material_available
      FROM topics t
      WHERE t.class_id = $1
        AND t.status = 'PUBLISHED'
        AND t.deleted_at IS NULL
      ORDER BY t.sort_order ASC, t.created_at ASC
      LIMIT $3 OFFSET $4
    `,
    [classId, vark, limit, offset],
  );
  return result.rows;
}

export async function countTopicsForStudent(
  client: PoolClient,
  classId: string,
): Promise<number> {
  const result = await client.query<{ count: string }>(
    `
      SELECT count(*)::text AS count
      FROM topics
      WHERE class_id = $1 AND status = 'PUBLISHED' AND deleted_at IS NULL
    `,
    [classId],
  );
  return Number(result.rows[0]?.count ?? '0');
}

export async function findTopicForStudent(
  client: PoolClient,
  topicId: string,
  studentId: string,
  vark: VarkType,
): Promise<TopicRow | null> {
  const result = await client.query<TopicRow>(
    `
      SELECT
        t.id, t.class_id, t.created_by, t.title, t.description,
        t.sort_order, t.status, t.created_at, t.updated_at, t.deleted_at,
        EXISTS (
          SELECT 1 FROM materials m
          WHERE m.topic_id = t.id
            AND m.vark_type = $3
            AND m.deleted_at IS NULL
        ) AS material_available
      FROM topics t
      WHERE t.id = $1
        AND t.status = 'PUBLISHED'
        AND t.deleted_at IS NULL
        AND EXISTS (
          SELECT 1
          FROM enrollments e
          JOIN classes c ON c.id = e.class_id
          WHERE e.student_id = $2
            AND e.status = 'ACTIVE'
            AND c.is_active = true
            AND e.class_id = t.class_id
        )
    `,
    [topicId, studentId, vark],
  );
  return result.rows[0] ?? null;
}

export async function listTopicsForLecturer(
  client: PoolClient,
  lecturerId: string,
  limit: number,
  offset: number,
): Promise<TopicRow[]> {
  const result = await client.query<TopicRow>(
    `
      SELECT
        t.id, t.class_id, t.created_by, t.title, t.description,
        t.sort_order, t.status, t.created_at, t.updated_at, t.deleted_at,
        false AS material_available
      FROM topics t
      JOIN teaching_assignments ta
        ON ta.class_id = t.class_id AND ta.lecturer_id = $1
      JOIN classes c ON c.id = t.class_id AND c.is_active = true
      WHERE t.deleted_at IS NULL
      ORDER BY t.sort_order ASC, t.created_at ASC
      LIMIT $2 OFFSET $3
    `,
    [lecturerId, limit, offset],
  );
  return result.rows;
}

export async function countTopicsForLecturer(
  client: PoolClient,
  lecturerId: string,
): Promise<number> {
  const result = await client.query<{ count: string }>(
    `
      SELECT count(*)::text AS count
      FROM topics t
      JOIN teaching_assignments ta
        ON ta.class_id = t.class_id AND ta.lecturer_id = $1
      JOIN classes c ON c.id = t.class_id AND c.is_active = true
      WHERE t.deleted_at IS NULL
    `,
    [lecturerId],
  );
  return Number(result.rows[0]?.count ?? '0');
}

export async function findTopicForLecturer(
  client: PoolClient,
  topicId: string,
  lecturerId: string,
): Promise<TopicRow | null> {
  const result = await client.query<TopicRow>(
    `
      SELECT
        t.id, t.class_id, t.created_by, t.title, t.description,
        t.sort_order, t.status, t.created_at, t.updated_at, t.deleted_at,
        false AS material_available
      FROM topics t
      JOIN teaching_assignments ta
        ON ta.class_id = t.class_id AND ta.lecturer_id = $2
      JOIN classes c ON c.id = t.class_id AND c.is_active = true
      WHERE t.id = $1 AND t.deleted_at IS NULL
    `,
    [topicId, lecturerId],
  );
  return result.rows[0] ?? null;
}

export async function insertTopic(
  client: PoolClient,
  input: {
    classId: string;
    createdBy: string;
    title: string;
    description: string | null;
    sortOrder?: number;
  },
): Promise<TopicRow> {
  let sortOrder = input.sortOrder;
  if (sortOrder === undefined) {
    const max = await client.query<{ max: number | null }>(
      'SELECT max(sort_order) AS max FROM topics WHERE class_id = $1 AND deleted_at IS NULL',
      [input.classId],
    );
    sortOrder = (max.rows[0]?.max ?? -1) + 1;
  }

  const result = await client.query<TopicRow>(
    `
      INSERT INTO topics (class_id, created_by, title, description, sort_order, status)
      VALUES ($1, $2, $3, $4, $5, 'DRAFT')
      RETURNING id, class_id, created_by, title, description, sort_order, status, created_at, updated_at, deleted_at, false AS material_available
    `,
    [input.classId, input.createdBy, input.title, input.description, sortOrder],
  );
  return result.rows[0]!;
}

export async function updateTopic(
  client: PoolClient,
  topicId: string,
  input: { title?: string; description?: string | null },
): Promise<TopicRow> {
  const result = await client.query<TopicRow>(
    `
      UPDATE topics
      SET title = COALESCE($2, title),
          description = CASE WHEN $3::boolean THEN $4 ELSE description END,
          updated_at = now()
      WHERE id = $1 AND deleted_at IS NULL
      RETURNING id, class_id, created_by, title, description, sort_order, status, created_at, updated_at, deleted_at, false AS material_available
    `,
    [topicId, input.title ?? null, input.description !== undefined, input.description ?? null],
  );
  return result.rows[0]!;
}

export async function setTopicStatus(
  client: PoolClient,
  topicId: string,
  status: 'DRAFT' | 'PUBLISHED',
): Promise<TopicRow> {
  const result = await client.query<TopicRow>(
    `
      UPDATE topics
      SET status = $2, updated_at = now()
      WHERE id = $1 AND deleted_at IS NULL
      RETURNING id, class_id, created_by, title, description, sort_order, status, created_at, updated_at, deleted_at, false AS material_available
    `,
    [topicId, status],
  );
  return result.rows[0]!;
}

export async function softDeleteTopic(
  client: PoolClient,
  topicId: string,
): Promise<void> {
  await client.query(
    'UPDATE topics SET deleted_at = now(), updated_at = now() WHERE id = $1 AND deleted_at IS NULL',
    [topicId],
  );
}

export async function topicHasAcademicData(
  client: PoolClient,
  topicId: string,
): Promise<boolean> {
  const result = await client.query<{ exists: boolean }>(
    `
      SELECT EXISTS (
        SELECT 1
        FROM case_studies cs
        WHERE cs.topic_id = $1
          AND EXISTS (
            SELECT 1 FROM submissions s WHERE s.case_study_id = cs.id
          )
      ) AS exists
    `,
    [topicId],
  );
  return result.rows[0]?.exists ?? false;
}

export async function countLiveTopics(
  client: PoolClient,
  classId: string,
): Promise<number> {
  const result = await client.query<{ count: string }>(
    'SELECT count(*)::text AS count FROM topics WHERE class_id = $1 AND deleted_at IS NULL',
    [classId],
  );
  return Number(result.rows[0]?.count ?? '0');
}

export async function setTopicSortOrder(
  client: PoolClient,
  topicId: string,
  sortOrder: number,
): Promise<void> {
  await client.query('UPDATE topics SET sort_order = $2 WHERE id = $1', [
    topicId,
    sortOrder,
  ]);
}

export async function listMaterialsForStudent(
  client: PoolClient,
  topicId: string,
  vark: VarkType,
): Promise<MaterialRow[]> {
  const result = await client.query<MaterialRow>(
    `
      SELECT id, topic_id, uploaded_by, vark_type, title, kind,
             object_key, url, mime, size_bytes, uploaded_at, deleted_at
      FROM materials
      WHERE topic_id = $1 AND vark_type = $2 AND deleted_at IS NULL
      ORDER BY uploaded_at ASC
    `,
    [topicId, vark],
  );
  return result.rows;
}

export async function listMaterialsForLecturer(
  client: PoolClient,
  topicId: string,
): Promise<MaterialRow[]> {
  const result = await client.query<MaterialRow>(
    `
      SELECT id, topic_id, uploaded_by, vark_type, title, kind,
             object_key, url, mime, size_bytes, uploaded_at, deleted_at
      FROM materials
      WHERE topic_id = $1 AND deleted_at IS NULL
      ORDER BY vark_type ASC, uploaded_at ASC
    `,
    [topicId],
  );
  return result.rows;
}

export async function insertFileMaterial(
  client: PoolClient,
  input: {
    topicId: string;
    uploadedBy: string;
    vark: VarkType;
    title: string;
    objectKey: string;
    mime: string;
    sizeBytes: number;
  },
): Promise<MaterialRow> {
  const result = await client.query<MaterialRow>(
    `
      INSERT INTO materials
        (topic_id, uploaded_by, vark_type, title, kind, object_key, mime, size_bytes)
      VALUES ($1, $2, $3, $4, 'FILE', $5, $6, $7)
      RETURNING id, topic_id, uploaded_by, vark_type, title, kind,
                object_key, url, mime, size_bytes, uploaded_at, deleted_at
    `,
    [input.topicId, input.uploadedBy, input.vark, input.title, input.objectKey, input.mime, input.sizeBytes],
  );
  return result.rows[0]!;
}

export async function insertLinkMaterial(
  client: PoolClient,
  input: {
    topicId: string;
    uploadedBy: string;
    vark: VarkType;
    title: string;
    url: string;
  },
): Promise<MaterialRow> {
  const result = await client.query<MaterialRow>(
    `
      INSERT INTO materials
        (topic_id, uploaded_by, vark_type, title, kind, url)
      VALUES ($1, $2, $3, $4, 'LINK', $5)
      RETURNING id, topic_id, uploaded_by, vark_type, title, kind,
                object_key, url, mime, size_bytes, uploaded_at, deleted_at
    `,
    [input.topicId, input.uploadedBy, input.vark, input.title, input.url],
  );
  return result.rows[0]!;
}

export async function findMaterialForLecturer(
  client: PoolClient,
  materialId: string,
  lecturerId: string,
): Promise<MaterialRow | null> {
  const result = await client.query<MaterialRow>(
    `
      SELECT m.id, m.topic_id, m.uploaded_by, m.vark_type, m.title, m.kind,
             m.object_key, m.url, m.mime, m.size_bytes, m.uploaded_at, m.deleted_at
      FROM materials m
      JOIN topics t ON t.id = m.topic_id
      JOIN teaching_assignments ta
        ON ta.class_id = t.class_id AND ta.lecturer_id = $2
      JOIN classes c ON c.id = t.class_id AND c.is_active = true
      WHERE m.id = $1 AND m.deleted_at IS NULL AND t.deleted_at IS NULL
    `,
    [materialId, lecturerId],
  );
  return result.rows[0] ?? null;
}

/**
 * Baca materi berdasarkan ID tanpa syarat kepemilikan dosen. Dipakai jalur
 * pemulihan setelah commit ambigu: record yang sudah tersimpan adalah sumber
 * kebenaran, bukan respons yang hilang.
 *
 * `forUpdate` mengambil kunci baris sehingga rekonsiliasi bersaing dengan
 * Ganti File/Hapus lain diserialkan pada baris yang sama.
 */
export async function findMaterialById(
  client: PoolClient,
  materialId: string,
  options: { forUpdate?: boolean } = {},
): Promise<MaterialRow | null> {
  const result = await client.query<MaterialRow>(
    `
      SELECT id, topic_id, uploaded_by, vark_type, title, kind,
             object_key, url, mime, size_bytes, uploaded_at, deleted_at
      FROM materials
      WHERE id = $1
      ${options.forUpdate ? 'FOR UPDATE' : ''}
    `,
    [materialId],
  );
  return result.rows[0] ?? null;
}

export async function updateMaterial(
  client: PoolClient,
  materialId: string,
  input: { title?: string; vark?: VarkType },
): Promise<MaterialRow> {
  const result = await client.query<MaterialRow>(
    `
      UPDATE materials
      SET title = COALESCE($2, title),
          vark_type = COALESCE($3, vark_type)
      WHERE id = $1 AND deleted_at IS NULL
      RETURNING id, topic_id, uploaded_by, vark_type, title, kind,
                object_key, url, mime, size_bytes, uploaded_at, deleted_at
    `,
    [materialId, input.title ?? null, input.vark ?? null],
  );
  return result.rows[0]!;
}

/**
 * Soft delete materi dan kembalikan baris hasil mutasi.
 *
 * `object_key` yang dikembalikan adalah nilai yang benar-benar terlihat oleh
 * UPDATE (di bawah kunci baris), bukan hasil pembacaan sebelumnya. Ini membuat
 * antrean penghapusan objek tetap memakai kunci yang tepat bila Ganti File
 * menyelesaikan commit di antara pembacaan otorisasi dan mutasi ini.
 *
 * `null` berarti baris sudah tidak memenuhi syarat (sudah dihapus permintaan
 * lain) sehingga pemanggil wajib memperlakukan permintaan sebagai 404.
 */
export async function softDeleteMaterial(
  client: PoolClient,
  materialId: string,
): Promise<MaterialRow | null> {
  const result = await client.query<MaterialRow>(
    `
      UPDATE materials
      SET deleted_at = now()
      WHERE id = $1 AND deleted_at IS NULL
      RETURNING id, topic_id, uploaded_by, vark_type, title, kind,
                object_key, url, mime, size_bytes, uploaded_at, deleted_at
    `,
    [materialId],
  );
  return result.rows[0] ?? null;
}

/**
 * Ganti berkas materi pada record yang sama. `expectedObjectKey` adalah kunci
 * objek yang dibaca sebelum unggahan pengganti dimulai; bila materi berubah
 * lebih dulu (mis. dua permintaan ganti berkas bersamaan), update tidak
 * mengenai baris mana pun dan pemanggil wajib mengompensasi objek baru.
 */
export async function replaceMaterialFile(
  client: PoolClient,
  materialId: string,
  input: {
    expectedObjectKey: string;
    objectKey: string;
    mime: string;
    sizeBytes: number;
  },
): Promise<MaterialRow | null> {
  const result = await client.query<MaterialRow>(
    `
      UPDATE materials
      SET object_key = $3, mime = $4, size_bytes = $5
      WHERE id = $1 AND deleted_at IS NULL AND object_key = $2
      RETURNING id, topic_id, uploaded_by, vark_type, title, kind,
                object_key, url, mime, size_bytes, uploaded_at, deleted_at
    `,
    [materialId, input.expectedObjectKey, input.objectKey, input.mime, input.sizeBytes],
  );
  return result.rows[0] ?? null;
}
