import { randomUUID } from 'node:crypto';
import { withTransaction } from '../../db/transaction';
import { getEnv } from '../../config/env';
import { conflict, forbidden, notFound, unprocessable } from '../../lib/errors';
import { AppError } from '../../lib/errors';
import { MATERIAL_NOT_AVAILABLE_MESSAGE } from '../../lib/messages';
import { writeAudit } from '../../lib/audit';
import { validateUploadedFile, type UploadedFileInput } from '../../lib/file-validation';
import { deleteObject, getSignedObjectUrl, putObject } from '../../lib/storage';
import type { VarkType } from '../../types/auth';
import * as repo from './topics.repository';



export interface TopicResponse {
  id: string;
  title: string;
  description: string | null;
  sortOrder: number;
  status: 'DRAFT' | 'PUBLISHED';
  materialAvailable: boolean;
  createdAt: string;
  updatedAt: string;
}

export interface StudentDashboardResponse {
  vark: VarkType;
  availableMaterials: number;
  notCompletedMaterials: number;
}

export interface MaterialResponse {
  id: string;
  topicId: string;
  vark: VarkType;
  title: string;
  kind: 'FILE' | 'LINK';
  mime: string | null;
  sizeBytes: number | null;
  url: string | null;
  signedUrl: string | null;
  expiresAt: string | null;
  uploadedAt: string;
}

function toTopic(row: repo.TopicRow): TopicResponse {
  return {
    id: row.id,
    title: row.title,
    description: row.description,
    sortOrder: row.sort_order,
    status: row.status,
    materialAvailable: row.material_available ?? false,
    createdAt: row.created_at.toISOString(),
    updatedAt: row.updated_at.toISOString(),
  };
}

async function toMaterial(row: repo.MaterialRow): Promise<MaterialResponse> {
  let signedUrl: string | null = null;
  let expiresAt: string | null = null;

  if (row.kind === 'FILE' && row.object_key) {
    signedUrl = await getSignedObjectUrl(row.object_key);
    const ttl = getEnv().R2_SIGNED_URL_TTL_SECONDS;
    expiresAt = new Date(Date.now() + ttl * 1000).toISOString();
  }

  return {
    id: row.id,
    topicId: row.topic_id,
    vark: row.vark_type,
    title: row.title,
    kind: row.kind,
    mime: row.mime,
    sizeBytes: row.size_bytes === null ? null : Number(row.size_bytes),
    url: row.url,
    signedUrl,
    expiresAt,
    uploadedAt: row.uploaded_at.toISOString(),
  };
}

function requireLecturerClass(classId: string | null): string {
  if (!classId) {
    throw forbidden('CLASS_FORBIDDEN', 'Dosen belum ditugaskan ke kelas aktif.');
  }
  return classId;
}

function requireStudentClass(classId: string | null): string {
  if (!classId) {
    throw forbidden('NOT_ENROLLED', 'Mahasiswa belum terdaftar pada kelas aktif.');
  }
  return classId;
}

async function requireTopicOwnership(
  client: Parameters<typeof repo.findTopicForLecturer>[0],
  topicId: string,
  lecturerId: string,
): Promise<repo.TopicRow> {
  const topic = await repo.findTopicForLecturer(client, topicId, lecturerId);
  if (!topic) throw notFound('TOPIC_NOT_FOUND', 'Topik tidak ditemukan.');
  return topic;
}

async function requireMaterialOwnership(
  client: Parameters<typeof repo.findMaterialForLecturer>[0],
  materialId: string,
  lecturerId: string,
): Promise<repo.MaterialRow> {
  const material = await repo.findMaterialForLecturer(client, materialId, lecturerId);
  if (!material) throw notFound('MATERIAL_NOT_FOUND', 'Materi tidak ditemukan.');
  return material;
}

export async function listTopicsForStudent(
  studentId: string,
  vark: VarkType,
  page: number,
  pageSize: number,
): Promise<{ items: TopicResponse[]; total: number }> {
  return withTransaction(async (client) => {
    const classId = requireStudentClass(await repo.findStudentClassId(client, studentId));
    const rows = await repo.listTopicsForStudent(
      client,
      classId,
      vark,
      pageSize,
      (page - 1) * pageSize,
    );
    const total = await repo.countTopicsForStudent(client, classId);
    return { items: rows.map(toTopic), total };
  });
}

export async function getStudentDashboard(
  studentId: string,
  vark: VarkType,
): Promise<StudentDashboardResponse> {
  return withTransaction(async (client) => {
    const classId = requireStudentClass(await repo.findStudentClassId(client, studentId));
    const row = await repo.getStudentDashboard(client, classId, studentId, vark);
    return {
      vark,
      availableMaterials: row.available_materials,
      notCompletedMaterials: row.not_completed_materials,
    };
  });
}

export async function getTopicForStudent(
  topicId: string,
  studentId: string,
  vark: VarkType,
): Promise<TopicResponse> {
  return withTransaction(async (client) => {
    const topic = await repo.findTopicForStudent(client, topicId, studentId, vark);
    if (!topic) throw notFound('TOPIC_NOT_FOUND', 'Topik tidak ditemukan.');
    return toTopic(topic);
  });
}

export async function listMaterialsForStudent(
  topicId: string,
  studentId: string,
  vark: VarkType,
): Promise<MaterialResponse[]> {
  return withTransaction(async (client) => {
    const topic = await repo.findTopicForStudent(client, topicId, studentId, vark);
    if (!topic) throw notFound('TOPIC_NOT_FOUND', 'Topik tidak ditemukan.');

    const rows = await repo.listMaterialsForStudent(client, topicId, vark);
    if (rows.length === 0) {
      throw new AppError('MATERIAL_NOT_AVAILABLE', 404, MATERIAL_NOT_AVAILABLE_MESSAGE);
    }
    return Promise.all(rows.map(toMaterial));
  });
}

export async function listTopicsForLecturer(
  lecturerId: string,
  page: number,
  pageSize: number,
): Promise<{ items: TopicResponse[]; total: number }> {
  return withTransaction(async (client) => {
    requireLecturerClass(await repo.findLecturerClassId(client, lecturerId));
    const rows = await repo.listTopicsForLecturer(
      client,
      lecturerId,
      pageSize,
      (page - 1) * pageSize,
    );
    const total = await repo.countTopicsForLecturer(client, lecturerId);
    return { items: rows.map(toTopic), total };
  });
}

export async function getTopicForLecturer(
  topicId: string,
  lecturerId: string,
): Promise<TopicResponse> {
  return withTransaction(async (client) => {
    const topic = await requireTopicOwnership(client, topicId, lecturerId);
    return toTopic(topic);
  });
}

export async function createTopic(
  lecturerId: string,
  input: { title: string; description?: string; sortOrder?: number },
): Promise<TopicResponse> {
  return withTransaction(async (client) => {
    const classId = requireLecturerClass(await repo.findLecturerClassId(client, lecturerId));
    const topic = await repo.insertTopic(client, {
      classId,
      createdBy: lecturerId,
      title: input.title,
      description: input.description ?? null,
      sortOrder: input.sortOrder,
    });
    await writeAudit(client, {
      actorId: lecturerId,
      action: 'TOPIC_CREATED',
      entity: 'TOPIC',
      entityId: topic.id,
      metadata: { title: topic.title },
    });
    return toTopic(topic);
  });
}

export async function updateTopic(
  topicId: string,
  lecturerId: string,
  input: { title?: string; description?: string | null },
): Promise<TopicResponse> {
  return withTransaction(async (client) => {
    await requireTopicOwnership(client, topicId, lecturerId);
    const topic = await repo.updateTopic(client, topicId, input);
    await writeAudit(client, {
      actorId: lecturerId,
      action: 'TOPIC_UPDATED',
      entity: 'TOPIC',
      entityId: topicId,
      metadata: { fields: Object.keys(input) },
    });
    return toTopic(topic);
  });
}

export async function setTopicPublication(
  topicId: string,
  lecturerId: string,
  status: 'DRAFT' | 'PUBLISHED',
): Promise<TopicResponse> {
  return withTransaction(async (client) => {
    await requireTopicOwnership(client, topicId, lecturerId);
    const topic = await repo.setTopicStatus(client, topicId, status);
    await writeAudit(client, {
      actorId: lecturerId,
      action: status === 'PUBLISHED' ? 'TOPIC_PUBLISHED' : 'TOPIC_UNPUBLISHED',
      entity: 'TOPIC',
      entityId: topicId,
      metadata: {},
    });
    return toTopic(topic);
  });
}

export async function deleteTopic(topicId: string, lecturerId: string): Promise<void> {
  await withTransaction(async (client) => {
    await requireTopicOwnership(client, topicId, lecturerId);
    if (await repo.topicHasAcademicData(client, topicId)) {
      throw conflict(
        'TOPIC_HAS_ACADEMIC_DATA',
        'Topik sudah memiliki submission/nilai. Gunakan unpublish/arsip.',
      );
    }
    await repo.softDeleteTopic(client, topicId);
    await writeAudit(client, {
      actorId: lecturerId,
      action: 'TOPIC_ARCHIVED',
      entity: 'TOPIC',
      entityId: topicId,
      metadata: {},
    });
  });
}

export async function reorderTopics(
  lecturerId: string,
  topicIds: string[],
): Promise<void> {
  await withTransaction(async (client) => {
    const classId = requireLecturerClass(await repo.findLecturerClassId(client, lecturerId));
    const liveCount = await repo.countLiveTopics(client, classId);
    if (liveCount !== topicIds.length) {
      throw unprocessable(
        'TOPIC_ORDER_INCOMPLETE',
        'Daftar urutan harus memuat seluruh topik aktif.',
      );
    }

    for (const topicId of topicIds) {
      await requireTopicOwnership(client, topicId, lecturerId);
    }

    // Two-phase update avoids collisions on ux_topic_order.
    for (let index = 0; index < topicIds.length; index += 1) {
      await repo.setTopicSortOrder(client, topicIds[index]!, 1_000_000 + index);
    }
    for (let index = 0; index < topicIds.length; index += 1) {
      await repo.setTopicSortOrder(client, topicIds[index]!, index);
    }

    await writeAudit(client, {
      actorId: lecturerId,
      action: 'TOPIC_REORDERED',
      entity: 'TOPIC',
      entityId: null,
      metadata: { topicIds },
    });
  });
}

export async function listMaterialsForLecturer(
  topicId: string,
  lecturerId: string,
): Promise<MaterialResponse[]> {
  return withTransaction(async (client) => {
    await requireTopicOwnership(client, topicId, lecturerId);
    const rows = await repo.listMaterialsForLecturer(client, topicId);
    return Promise.all(rows.map(toMaterial));
  });
}

export async function createFileMaterial(
  topicId: string,
  lecturerId: string,
  file: UploadedFileInput,
  fields: { title: string; vark: VarkType },
): Promise<MaterialResponse> {
  const validated = validateUploadedFile(file);
  const objectKey = 'materials/' + randomUUID() + validated.extension;

  return withTransaction(async (client) => {
    await requireTopicOwnership(client, topicId, lecturerId);

    await putObject(objectKey, file.buffer, validated.mime);

    let material: repo.MaterialRow;
    try {
      material = await repo.insertFileMaterial(client, {
        topicId,
        uploadedBy: lecturerId,
        vark: fields.vark,
        title: fields.title,
        objectKey,
        mime: validated.mime,
        sizeBytes: file.size,
      });
    } catch (error) {
      await deleteObject(objectKey).catch(() => undefined);
      throw error;
    }

    await writeAudit(client, {
      actorId: lecturerId,
      action: 'MATERIAL_CREATED',
      entity: 'MATERIAL',
      entityId: material.id,
      metadata: { kind: 'FILE', vark: fields.vark },
    });

    return toMaterial(material);
  });
}

export async function createLinkMaterial(
  topicId: string,
  lecturerId: string,
  input: { title: string; vark: VarkType; url: string },
): Promise<MaterialResponse> {
  return withTransaction(async (client) => {
    await requireTopicOwnership(client, topicId, lecturerId);
    const material = await repo.insertLinkMaterial(client, {
      topicId,
      uploadedBy: lecturerId,
      vark: input.vark,
      title: input.title,
      url: input.url,
    });
    await writeAudit(client, {
      actorId: lecturerId,
      action: 'MATERIAL_CREATED',
      entity: 'MATERIAL',
      entityId: material.id,
      metadata: { kind: 'LINK', vark: input.vark },
    });
    return toMaterial(material);
  });
}

export async function updateMaterial(
  materialId: string,
  lecturerId: string,
  input: { title?: string; vark?: VarkType },
): Promise<MaterialResponse> {
  return withTransaction(async (client) => {
    await requireMaterialOwnership(client, materialId, lecturerId);
    const material = await repo.updateMaterial(client, materialId, input);
    await writeAudit(client, {
      actorId: lecturerId,
      action: 'MATERIAL_UPDATED',
      entity: 'MATERIAL',
      entityId: materialId,
      metadata: { fields: Object.keys(input) },
    });
    return toMaterial(material);
  });
}

export async function deleteMaterial(materialId: string, lecturerId: string): Promise<void> {
  await withTransaction(async (client) => {
    await requireMaterialOwnership(client, materialId, lecturerId);
    await repo.softDeleteMaterial(client, materialId);
    await writeAudit(client, {
      actorId: lecturerId,
      action: 'MATERIAL_DELETED',
      entity: 'MATERIAL',
      entityId: materialId,
      metadata: {},
    });
  });
}
