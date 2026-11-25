import { randomUUID } from 'node:crypto';
import { withTransaction } from '../../db/transaction';
import { conflict, forbidden, notFound, unprocessable } from '../../lib/errors';
import { AppError } from '../../lib/errors';
import { MATERIAL_NOT_AVAILABLE_MESSAGE } from '../../lib/messages';
import { writeAudit } from '../../lib/audit';
import { validateUploadedFile, type UploadedFileInput } from '../../lib/file-validation';
import { deleteObject, putObject } from '../../lib/storage';
import { logger } from '../../lib/logger';
import type { VarkType } from '../../types/auth';
import {
  attemptObjectDeletionAfterCommit,
  enqueueObjectDeletion,
  type FileDeletionOutcome,
} from '../materials/object-deletions.service';
import {
  beginFileReplacement,
  lockPendingFileReplacement,
  markFileReplacementActive,
  resolveFileReplacementAfterFailure,
} from '../materials/file-replacements.service';
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
  /** URL tujuan tautan eksternal (hanya untuk materi LINK). */
  url: string | null;
  /** Alamat unduh tetap EduVARK untuk berkas terkelola (C1-02/FR-015). */
  contentUrl: string | null;
  /** Alamat pembukaan tautan eksternal melalui EduVARK (cek izin terkini). */
  linkTargetUrl: string | null;
  /** Selalu null: URL bertanda tangan tidak lagi diterbitkan (C1-02). */
  signedUrl: null;
  /** Selalu null: alamat tetap tidak memiliki masa berlaku tersendiri. */
  expiresAt: null;
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
  // C1-02: URL bertanda tangan berhenti diterbitkan. Klien memakai alamat
  // tetap yang selalu melewati otorisasi backend pada setiap permintaan.
  return {
    id: row.id,
    topicId: row.topic_id,
    vark: row.vark_type,
    title: row.title,
    kind: row.kind,
    mime: row.mime,
    sizeBytes: row.size_bytes === null ? null : Number(row.size_bytes),
    url: row.url,
    contentUrl:
      row.kind === 'FILE' ? '/api/v1/materials/' + row.id + '/content' : null,
    linkTargetUrl:
      row.kind === 'LINK' ? '/api/v1/materials/' + row.id + '/link-target' : null,
    signedUrl: null,
    expiresAt: null,
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

export interface MaterialDeletionResult {
  id: string;
  /** Hasil penghapusan objek penyimpanan: tidak berlaku untuk materi LINK. */
  fileDeletion: FileDeletionOutcome;
}

export interface MaterialFileReplacement {
  material: MaterialResponse;
  /** Hasil penghapusan objek berkas lama setelah berkas baru aktif. */
  previousFileDeletion: FileDeletionOutcome;
}

export interface MaterialDeletionQueued {
  jobId: string | null;
  /** Kunci objek yang benar-benar dihapus, hasil mutasi (bukan pembacaan awal). */
  objectKey: string | null;
  kind: 'FILE' | 'LINK';
}

/**
 * Seam internal untuk test deterministik. `afterOwnershipRead` dipanggil setelah
 * pembacaan otorisasi dan sebelum mutasi, sehingga test dapat menyisipkan
 * commit Ganti File tepat pada jendela balapan yang dilindungi.
 */
export interface MaterialDeletionHooks {
  afterOwnershipRead?: () => void | Promise<void>;
}

/**
 * Bagian transaksi dari Hapus materi: mutasi soft delete, audit, dan pencatatan
 * pekerjaan penghapusan objek.
 *
 * `object_key` diambil dari `UPDATE ... RETURNING`, bukan dari pembacaan
 * otorisasi sebelumnya. Bila Ganti File berhasil commit di antara pembacaan
 * dan mutasi, hasil mutasi memuat kunci baru sehingga antrean menghapus berkas
 * yang benar-benar aktif saat materi disembunyikan. Audit dan pekerjaan hanya
 * ditulis bila UPDATE benar-benar mengubah baris; permintaan berikutnya
 * menerima 404 MATERIAL_NOT_FOUND.
 */
export async function deleteMaterialInTransaction(
  client: Parameters<typeof repo.softDeleteMaterial>[0],
  materialId: string,
  lecturerId: string,
  hooks: MaterialDeletionHooks = {},
): Promise<MaterialDeletionQueued> {
  await requireMaterialOwnership(client, materialId, lecturerId);
  await hooks.afterOwnershipRead?.();

  const deleted = await repo.softDeleteMaterial(client, materialId);
  if (!deleted) {
    // Permintaan Hapus lain sudah menyembunyikan materi ini lebih dulu.
    throw notFound('MATERIAL_NOT_FOUND', 'Materi tidak ditemukan.');
  }

  await writeAudit(client, {
    actorId: lecturerId,
    action: 'MATERIAL_DELETED',
    entity: 'MATERIAL',
    entityId: materialId,
    metadata: { kind: deleted.kind },
  });

  if (deleted.kind !== 'FILE' || !deleted.object_key) {
    return { jobId: null, objectKey: null, kind: deleted.kind };
  }
  const jobId = await enqueueObjectDeletion(client, {
    objectKey: deleted.object_key,
    materialId,
    requestedBy: lecturerId,
    reason: 'MATERIAL_DELETED',
  });
  return { jobId, objectKey: deleted.object_key, kind: deleted.kind };
}

/**
 * Hapus materi: record, audit, dan riwayat feedback tetap disimpan (soft
 * delete). Untuk materi FILE, niat penghapusan objek dicatat dalam transaksi
 * yang sama lalu langsung dicoba setelah commit tanpa masa tenggang. Bila
 * penyimpanan gagal/belum pasti, hasil status fisik dilaporkan apa adanya dan
 * worker menangani percobaan ulang.
 */
export async function deleteMaterial(
  materialId: string,
  lecturerId: string,
): Promise<MaterialDeletionResult> {
  const queued = await withTransaction((client) =>
    deleteMaterialInTransaction(client, materialId, lecturerId),
  );

  const fileDeletion = await attemptObjectDeletionAfterCommit(queued.jobId);
  return { id: materialId, fileDeletion };
}

/**
 * Kait pengujian deterministik untuk menginjeksi kegagalan pada titik yang
 * tepat. Tidak dipakai produksi; tanpa kait, alur berjalan normal.
 */
export interface MaterialFileReplacementHooks {
  /** Setelah unggahan selesai, sebelum transaksi metadata dimulai. */
  afterUpload?: () => Promise<void> | void;
  /** Di dalam transaksi metadata, setelah baris materi diperbarui. */
  afterMetadataUpdate?: () => Promise<void> | void;
  /** Setelah COMMIT metadata berhasil, sebelum respons dibentuk. */
  afterMetadataCommit?: () => Promise<void> | void;
}

/**
 * Ganti berkas pada materi FILE yang sama.
 *
 * Niat penggantian dicatat lebih dulu (PENDING) sebelum objek pengganti
 * ditulis. Saat metadata di-commit, niat ditandai ACTIVE dan pekerjaan hapus
 * objek lama dicatat dalam transaksi yang sama, sehingga:
 *   - COMMIT berhasil tetapi konfirmasi hilang tidak pernah menghapus objek
 *     yang kini aktif (record sudah ACTIVE dan menunjuk objek baru);
 *   - rollback/unggahan gagal membuat objek lama tetap aktif dan objek
 *     pengganti masuk antrean pembersihan yang dapat diulang;
 *   - proses yang berhenti sebelum commit metadata meninggalkan niat PENDING
 *     yang diselesaikan worker setelah lease habis.
 */
export async function replaceMaterialFile(
  materialId: string,
  lecturerId: string,
  file: UploadedFileInput,
  hooks: MaterialFileReplacementHooks = {},
): Promise<MaterialFileReplacement> {
  const validated = validateUploadedFile(file);
  const before = await withTransaction((client) =>
    requireMaterialOwnership(client, materialId, lecturerId),
  );
  if (before.kind !== 'FILE' || !before.object_key) {
    throw unprocessable(
      'MATERIAL_KIND_UNSUPPORTED',
      'Hanya materi berjenis berkas yang dapat diganti.',
    );
  }
  const expectedObjectKey = before.object_key;
  const objectKey = 'materials/' + randomUUID() + validated.extension;

  // Niat persisten dicatat SEBELUM objek ditulis: bila proses berhenti di
  // titik mana pun setelah ini, kunci pengganti sudah dapat ditemukan.
  const intent = await beginFileReplacement({
    materialId,
    requestedBy: lecturerId,
    oldObjectKey: expectedObjectKey,
    newObjectKey: objectKey,
  });
  if (intent.leaseToken === null) {
    throw new AppError(
      'STORAGE_REPLACEMENT_UNAVAILABLE',
      503,
      'Tidak dapat memulai penggantian berkas. Coba lagi.',
    );
  }
  const leaseToken = intent.leaseToken;

  try {
    await putObject(objectKey, file.buffer, validated.mime);
    await hooks.afterUpload?.();

    const updated = await withTransaction(async (client) => {
      const material = await requireMaterialOwnership(client, materialId, lecturerId);
      if (material.kind !== 'FILE' || !material.object_key) {
        throw unprocessable(
          'MATERIAL_KIND_UNSUPPORTED',
          'Hanya materi berjenis berkas yang dapat diganti.',
        );
      }

      // Urutan kunci: niat lalu materi (sama dengan rekonsiliasi) agar tidak
      // deadlock dengan worker yang memulihkan operasi tertinggal.
      const leaseValid = await lockPendingFileReplacement(
        client,
        intent.id,
        leaseToken,
      );
      if (!leaseValid) {
        throw conflict(
          'MATERIAL_CHANGED',
          'Materi berubah saat proses ganti berkas. Muat ulang lalu coba lagi.',
        );
      }

      const row = await repo.replaceMaterialFile(client, materialId, {
        expectedObjectKey,
        objectKey,
        mime: validated.mime,
        sizeBytes: file.size,
      });
      if (!row) {
        throw conflict(
          'MATERIAL_CHANGED',
          'Materi berubah saat proses ganti berkas. Muat ulang lalu coba lagi.',
        );
      }

      await hooks.afterMetadataUpdate?.();

      await writeAudit(client, {
        actorId: lecturerId,
        action: 'MATERIAL_FILE_REPLACED',
        entity: 'MATERIAL',
        entityId: materialId,
        metadata: { kind: 'FILE', mime: validated.mime, sizeBytes: file.size },
      });

      const jobId = await enqueueObjectDeletion(client, {
        objectKey: expectedObjectKey,
        materialId,
        requestedBy: lecturerId,
        reason: 'FILE_REPLACED',
      });
      const activated = await markFileReplacementActive(
        client,
        intent.id,
        leaseToken,
        jobId,
      );
      if (!activated) {
        throw conflict(
          'MATERIAL_CHANGED',
          'Materi berubah saat proses ganti berkas. Muat ulang lalu coba lagi.',
        );
      }
      return { row, jobId };
    });

    // Titik di mana COMMIT sudah terjadi tetapi proses masih bisa gagal
    // sebelum respons terkirim (kait ini mensimulasikannya di test).
    await hooks.afterMetadataCommit?.();

    const previousFileDeletion = await attemptObjectDeletionAfterCommit(updated.jobId);
    return {
      material: await toMaterial(updated.row),
      previousFileDeletion,
    };
  } catch (error) {
    // Kompensasi TIDAK PERNAH menghapus objek pengganti secara langsung.
    // Keputusan diambil dari state database: ACTIVE berarti commit berhasil
    // (pertahankan objek), ABANDONED berarti objek lama tetap aktif dan objek
    // baru masuk antrean bersih, UNKNOWN berarti pertahankan + rekonsiliasi.
    const resolution = await resolveFileReplacementAfterFailure(
      intent.id,
      leaseToken,
    );

    if (resolution.status === 'ACTIVE') {
      logger.warn(
        { materialId, replacementId: intent.id },
        'Konfirmasi Ganti File hilang setelah commit; record menunjuk objek baru',
      );
      const previousFileDeletion = await attemptObjectDeletionAfterCommit(
        resolution.oldDeletionJobId,
      );
      return {
        material: await toMaterial(resolution.material),
        previousFileDeletion,
      };
    }

    if (resolution.status === 'ABANDONED') {
      // Pekerjaan pembersihan sudah dicatat; coba sekali sekarang, worker akan
      // mengulang bila penyimpanan sedang gagal.
      await attemptObjectDeletionAfterCommit(resolution.cleanupJobId);
      throw error;
    }

    logger.error(
      { err: error, materialId, replacementId: intent.id },
      'Status Ganti File tidak pasti; objek pengganti dipertahankan untuk rekonsiliasi',
    );
    throw new AppError(
      'REPLACEMENT_UNCERTAIN',
      503,
      'Perubahan berkas belum dapat dipastikan. Muat ulang materi; sistem akan ' +
        'merekonsiliasi otomatis.',
    );
  }
}
