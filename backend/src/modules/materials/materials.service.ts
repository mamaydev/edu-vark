import { withTransaction } from '../../db/transaction';
import { AppError, forbidden, notFound } from '../../lib/errors';
import {
  getObjectBody,
  getObjectInfo,
  type ObjectByteRange,
  type ObjectInfo,
} from '../../lib/storage';
import type { AuthContext } from '../../types/auth';
import * as repo from './materials.repository';

/**
 * Menilai izin akses materi pada SETIAP permintaan unduh/pembukaan tautan.
 * Dek-001/FR-015: hasil penilaian tidak boleh di-cache antar permintaan.
 */
export async function authorizeMaterialAccess(
  context: AuthContext,
  materialId: string,
): Promise<repo.MaterialAccessRow> {
  return withTransaction(async (client) => {
    const row = await repo.findMaterialForAccess(client, materialId);
    if (!row || row.deleted_at !== null || row.topic_deleted_at !== null) {
      throw notFound('MATERIAL_NOT_FOUND', 'Materi tidak ditemukan.');
    }

    if (context.role === 'STUDENT') {
      // FR-007: mahasiswa wajib punya VARK sesi aktif sebelum akses materi.
      if (!context.varkType) {
        throw new AppError('VARK_REQUIRED', 428, 'Pilih VARK terlebih dahulu.');
      }
      // Mahasiswa hanya melihat materi pada topik terbit dan kelas aktif.
      if (row.topic_status !== 'PUBLISHED' || !row.class_is_active) {
        throw notFound('MATERIAL_NOT_FOUND', 'Materi tidak ditemukan.');
      }
      // FR-014/FR-015: kategori materi harus sama dengan VARK sesi.
      if (row.vark_type !== context.varkType) {
        throw new AppError(
          'MATERIAL_VARK_FORBIDDEN',
          403,
          'Materi tidak tersedia untuk kategori VARK sesi ini.',
        );
      }
      if (!(await repo.hasActiveEnrollment(client, row.class_id, context.userId))) {
        throw forbidden('NOT_ENROLLED', 'Mahasiswa belum terdaftar pada kelas aktif.');
      }
      return row;
    }

    if (context.role === 'LECTURER') {
      if (!row.class_is_active) {
        throw notFound('MATERIAL_NOT_FOUND', 'Materi tidak ditemukan.');
      }
      if (!(await repo.hasTeachingAssignment(client, row.class_id, context.userId))) {
        throw notFound('MATERIAL_NOT_FOUND', 'Materi tidak ditemukan.');
      }
      return row;
    }

    throw forbidden('FORBIDDEN', 'Anda tidak berwenang untuk aksi ini.');
  });
}

export interface ResolvedFileContent {
  material: repo.MaterialAccessRow;
  info: ObjectInfo;
  range: ObjectByteRange | null;
  partial: boolean;
}

export type ParsedRange =
  | { kind: 'none' }
  | { kind: 'range'; start: number; end: number }
  | { kind: 'unsatisfiable' };

export function parseRangeHeader(
  raw: string | undefined,
  size: number,
): ParsedRange {
  if (!raw) return { kind: 'none' };
  const match = /^bytes=(\d*)-(\d*)$/.exec(raw.trim());
  if (!match) {
    // Multi-range dan format lain ditolak; server boleh menjawab 416.
    return { kind: 'unsatisfiable' };
  }
  const startRaw = match[1] ?? '';
  const endRaw = match[2] ?? '';
  if (startRaw === '' && endRaw === '') return { kind: 'unsatisfiable' };
  if (startRaw === '') {
    const suffix = Number(endRaw);
    if (!Number.isSafeInteger(suffix) || suffix <= 0 || size === 0) {
      return { kind: 'unsatisfiable' };
    }
    return { kind: 'range', start: Math.max(size - suffix, 0), end: size - 1 };
  }
  const start = Number(startRaw);
  if (!Number.isSafeInteger(start) || start < 0 || start >= size) {
    return { kind: 'unsatisfiable' };
  }
  const end = endRaw === '' ? size - 1 : Math.min(Number(endRaw), size - 1);
  if (!Number.isSafeInteger(end) || end < start) {
    return { kind: 'unsatisfiable' };
  }
  return { kind: 'range', start, end };
}

function ifRangeMatches(ifRange: string | undefined, info: ObjectInfo): boolean {
  if (!ifRange) return true;
  const value = ifRange.trim();
  return value === info.etag || (info.lastModified !== null && value === info.lastModified);
}

export async function resolveFileContent(
  context: AuthContext,
  materialId: string,
  options: { range?: string; ifRange?: string },
): Promise<ResolvedFileContent> {
  const material = await authorizeMaterialAccess(context, materialId);
  if (material.kind !== 'FILE' || !material.object_key) {
    throw notFound('MATERIAL_NOT_FOUND', 'Materi tidak ditemukan.');
  }

  const info = await getObjectInfo(material.object_key);

  // If-Range tidak cocok berarti klien meminta versi objek yang berbeda:
  // server mengabaikan Range dan mengirim isi penuh (RFC 7233 §3.2).
  const rangeAllowed = ifRangeMatches(options.ifRange, info);
  const parsed = rangeAllowed
    ? parseRangeHeader(options.range, info.size)
    : ({ kind: 'none' } as ParsedRange);

  if (parsed.kind === 'unsatisfiable') {
    throw new AppError(
      'RANGE_NOT_SATISFIABLE',
      416,
      'Rentang byte tidak dapat dipenuhi.',
      { size: info.size },
    );
  }

  return {
    material,
    info,
    range: parsed.kind === 'range' ? { start: parsed.start, end: parsed.end } : null,
    partial: parsed.kind === 'range',
  };
}

/**
 * Validasi tujuan tautan: hanya URL HTTPS absolut yang boleh dibuka. Database
 * juga menegakkan aturan ini (ck_material_shape), sehingga pemeriksaan ini
 * adalah pertahanan lapis aplikasi bila data lama/import tidak sesuai.
 */
export function assertSecureLinkTarget(rawUrl: string): string {
  let parsed: URL;
  try {
    parsed = new URL(rawUrl);
  } catch {
    throw forbidden('LINK_TARGET_INVALID', 'Tautan materi tidak valid.');
  }
  if (parsed.protocol !== 'https:') {
    throw forbidden('LINK_TARGET_INSECURE', 'Tautan materi harus menggunakan HTTPS.');
  }
  return parsed.toString();
}

/**
 * Tautan HTTPS eksternal tetap didukung, tetapi tujuan hanya dikembalikan
 * setelah EduVARK memeriksa izin terkini (FR-015/DEC-001).
 */
export async function resolveLinkTarget(
  context: AuthContext,
  materialId: string,
): Promise<string> {
  const material = await authorizeMaterialAccess(context, materialId);
  if (material.kind !== 'LINK' || !material.url) {
    throw notFound('MATERIAL_NOT_FOUND', 'Materi tidak ditemukan.');
  }
  return assertSecureLinkTarget(material.url);
}

/** Dipakai route untuk mengalirkan isi objek setelah otorisasi lolos. */
export async function openFileContentBody(
  resolved: ResolvedFileContent,
): Promise<NodeJS.ReadableStream | Buffer> {
  return getObjectBody(resolved.info.key, resolved.range);
}
