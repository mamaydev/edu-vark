/**
 * Inventaris & eksekusi pembersihan backlog objek penyimpanan.
 *
 * Latar belakang: sebelum fitur hapus/ganti berkas, soft delete materi tidak
 * menghapus objek R2 sehingga menumpuk. Pembersihan backlog bersifat SATU KALI,
 * tidak berjalan otomatis saat backend aktif/restart, dan hanya boleh dieksekusi
 * setelah Project Owner menyetujui daftar sasaran yang persis.
 *
 * Mode default (dry-run) hanya membaca database + metadata objek; tidak pernah
 * mengubah database maupun penyimpanan.
 *
 * Mode eksekusi hanya menerima inventaris dry-run yang:
 *  - lolos validasi struktur (mode, format objectKey, daftar materialId valid
 *    dan tidak kosong untuk setiap entri),
 *  - checksum SHA-256-nya cocok dengan persetujuan Owner yang diberikan lewat
 *    input terpisah (`--sha256` atau `BACKLOG_CLEANUP_INVENTORY_SHA256`),
 *  - identitas lingkungannya sama dengan lingkungan aktif (database, mode
 *    penyimpanan, endpoint/bucket R2 atau direktori lokal).
 *
 * Setiap objek diverifikasi ulang di bawah kunci baris database sebelum
 * dihapus: pasangan materialId+objectKey masih cocok, baris masih memenuhi
 * syarat backlog, tidak ada referensi aktif, tidak ada pekerjaan antrean fitur,
 * dan objek masih ada. Gagal cocok = dilewati dengan alasan, bukan dihapus.
 *
 * Pemakaian:
 *   npm run storage:backlog:dry-run
 *   npm run storage:backlog:dry-run -- --out artifacts/backlog-inventory.json
 *   BACKLOG_CLEANUP_APPROVED=1 npm run storage:backlog:execute -- \
 *     --inventory artifacts/backlog-inventory.json \
 *     --sha256 <sha256-file-yang-disetujui> \
 *     --report artifacts/backlog-execute.json
 */
import { createHash } from 'node:crypto';
import fs from 'node:fs/promises';
import path from 'node:path';
import type { PoolClient } from 'pg';
import { closePool, getPool } from '../db/client';
import { getEnv } from '../config/env';
import { logger } from '../lib/logger';
import { deleteObject, listObjectKeys, objectStat, storageMode } from '../lib/storage';
import type { ObjectDeletionReason } from '../modules/materials/object-deletions.repository';

/** Versi skema inventaris. Inventaris versi lama ditolak saat eksekusi. */
export const BACKLOG_INVENTORY_VERSION = 2;

export const BACKLOG_APPROVAL_ENV = 'BACKLOG_CLEANUP_APPROVED';
export const BACKLOG_SHA_ENV = 'BACKLOG_CLEANUP_INVENTORY_SHA256';

const OBJECT_KEY_PATTERN = /^materials\/[A-Za-z0-9][A-Za-z0-9._-]*$/;
const MATERIAL_ID_PATTERN =
  /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;
const SHA256_PATTERN = /^[0-9a-f]{64}$/i;

/** Identitas lingkungan tanpa kredensial. Disimpan di manifest dan laporan. */
export interface BacklogEnvironmentIdentity {
  nodeEnv: string;
  storageMode: 'r2' | 'local';
  database: string;
  r2: {
    endpoint: string | null;
    bucket: string | null;
  } | null;
  localStorageDir: string | null;
}

export interface BacklogCandidateRow {
  id: string;
  object_key: string | null;
  title: string;
  deleted_at: Date;
}

export interface BacklogIncludedObject {
  objectKey: string;
  sizeBytes: number | null;
  materialIds: string[];
  earliestDeletedAt: string;
}

export interface BacklogExcludedEntry {
  objectKey: string | null;
  materialIds: string[];
  reason:
    | 'NO_OBJECT_KEY'
    | 'ACTIVE_REFERENCE'
    | 'OBJECT_ABSENT'
    | 'DUPLICATE_KEY'
    | 'ALREADY_HAS_JOB';
  detail: string;
}

export interface BacklogInventory {
  version: typeof BACKLOG_INVENTORY_VERSION;
  generatedAt: string;
  mode: 'dry-run';
  environment: BacklogEnvironmentIdentity;
  summary: {
    softDeletedFileMaterials: number;
    uniqueObjectKeys: number;
    deletableObjects: number;
    deletableBytes: number;
    unknownSizeObjects: number;
    excluded: number;
  };
  included: BacklogIncludedObject[];
  excluded: BacklogExcludedEntry[];
  orphanObjects: {
    scanned: boolean;
    count: number;
    keys: string[];
    note: string;
  };
  approval: {
    required: true;
    note: string;
  };
}

export type BacklogSkipReason =
  | 'PAIR_MISMATCH'
  | 'MATERIAL_SET_CHANGED'
  | 'ACTIVE_REFERENCE'
  | 'ALREADY_HAS_JOB'
  | 'OBJECT_ABSENT';

export interface ExecuteBacklogOptions {
  inventoryPath: string;
  reportPath?: string;
  /** SHA-256 file inventaris yang disetujui Owner (input terpisah). */
  approvedSha256?: string;
  /**
   * Seam observasi internal untuk test deterministik: dipanggil tepat sebelum
   * verifikasi tiap objek, setelah batch dimulai. Tidak dipakai di CLI.
   */
  onBeforeVerify?: (objectKey: string) => void | Promise<void>;
}

export interface ExecuteBacklogReport {
  executedAt: string;
  mode: 'execute';
  inventoryGeneratedAt: string;
  inventorySha256: string;
  environment: BacklogEnvironmentIdentity;
  deleted: { objectKey: string; materialIds: string[]; sizeBytes: number | null }[];
  skipped: {
    objectKey: string;
    materialIds: string[];
    reason: BacklogSkipReason;
    detail: string;
  }[];
  failed: { objectKey: string; materialIds: string[]; error: string }[];
  summary: { deleted: number; skipped: number; failed: number };
}

const APPROVAL_NOTE =
  'Daftar ini belum dieksekusi. Pembersihan backlog hanya boleh dijalankan setelah ' +
  'Project Owner menyetujui daftar sasaran yang persis dan memberikan checksum SHA-256 ' +
  'file inventaris ini secara terpisah. Eksekusi: --inventory <file> --sha256 <hex> ' +
  'dengan BACKLOG_CLEANUP_APPROVED=1.';

function maskDatabaseUrl(url: string): string {
  try {
    const parsed = new URL(url);
    return parsed.host + parsed.pathname;
  } catch {
    return 'unknown';
  }
}

function credentialFreeEndpoint(endpoint: string | undefined): string | null {
  if (!endpoint) return null;
  try {
    // Buang kredensial bila ada; host tetap memuat identitas akun R2. Hindari
    // query/path agar inventaris tidak memuat rahasia apa pun.
    return new URL(endpoint).origin;
  } catch {
    return 'unknown';
  }
}

function localStorageRoot(): string {
  return path.resolve(process.cwd(), getEnv().LOCAL_STORAGE_DIR ?? 'storage');
}

/** Identitas lingkungan aktif tanpa kredensial, untuk manifest & pencocokan. */
export function currentEnvironmentIdentity(): BacklogEnvironmentIdentity {
  const env = getEnv();
  const mode = storageMode();
  return {
    nodeEnv: env.NODE_ENV,
    storageMode: mode,
    database: maskDatabaseUrl(env.DATABASE_URL),
    r2:
      mode === 'r2'
        ? {
            endpoint: credentialFreeEndpoint(env.R2_ENDPOINT),
            bucket: env.R2_BUCKET ?? null,
          }
        : null,
    localStorageDir: mode === 'local' ? localStorageRoot() : null,
  };
}

export function computeInventorySha256(raw: string | Buffer): string {
  return createHash('sha256').update(raw).digest('hex');
}

function isPlainObject(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value);
}

function environmentsMatch(
  approved: BacklogEnvironmentIdentity,
  current: BacklogEnvironmentIdentity,
): boolean {
  return (
    approved.nodeEnv === current.nodeEnv &&
    approved.storageMode === current.storageMode &&
    approved.database === current.database &&
    approved.localStorageDir === current.localStorageDir &&
    (approved.r2?.endpoint ?? null) === (current.r2?.endpoint ?? null) &&
    (approved.r2?.bucket ?? null) === (current.r2?.bucket ?? null)
  );
}

/**
 * Validasi struktur inventaris sebelum operasi penghapusan apa pun. Gagal
 * tertutup: setiap penyimpangan melempar error, tidak ada kandidat yang
 * dieksekusi sebagian dari inventaris cacat.
 */
export function validateApprovedInventory(
  raw: Buffer,
  approvedSha256: string,
  current: BacklogEnvironmentIdentity = currentEnvironmentIdentity(),
): BacklogInventory {
  if (!SHA256_PATTERN.test(approvedSha256)) {
    throw new Error(
      'Persetujuan checksum wajib berupa SHA-256 hex 64 karakter yang diberikan terpisah.',
    );
  }
  const computed = computeInventorySha256(raw);
  if (computed.toLowerCase() !== approvedSha256.toLowerCase()) {
    throw new Error(
      'Checksum inventaris tidak cocok dengan persetujuan Owner. Eksekusi dibatalkan.',
    );
  }

  let parsed: unknown;
  try {
    parsed = JSON.parse(raw.toString('utf8'));
  } catch {
    throw new Error('Inventaris bukan JSON yang valid. Eksekusi dibatalkan.');
  }
  if (!isPlainObject(parsed)) {
    throw new Error('Inventaris harus berupa objek JSON. Eksekusi dibatalkan.');
  }

  if (parsed.version !== BACKLOG_INVENTORY_VERSION) {
    throw new Error(
      'Versi inventaris tidak didukung (harus ' + BACKLOG_INVENTORY_VERSION + ').',
    );
  }
  if (parsed.mode !== 'dry-run') {
    throw new Error('Inventaris wajib berasal dari mode dry-run.');
  }
  if (typeof parsed.generatedAt !== 'string' || parsed.generatedAt.length === 0) {
    throw new Error('Inventaris tidak memuat generatedAt yang valid.');
  }
  if (!isPlainObject(parsed.environment)) {
    throw new Error('Inventaris tidak memuat identitas lingkungan.');
  }
  const approvedEnvironment = parsed.environment as unknown as BacklogEnvironmentIdentity;
  if (approvedEnvironment.storageMode !== 'r2' && approvedEnvironment.storageMode !== 'local') {
    throw new Error('Identitas lingkungan inventaris tidak valid.');
  }
  if (!environmentsMatch(approvedEnvironment, current)) {
    throw new Error(
      'Inventaris tidak cocok dengan lingkungan aktif (database/mode penyimpanan/' +
        'direktori lokal/endpoint atau bucket R2 berbeda). Eksekusi dibatalkan.',
    );
  }
  if (!Array.isArray(parsed.included) || !Array.isArray(parsed.excluded)) {
    throw new Error('Inventaris tidak memuat daftar included/excluded.');
  }
  if (!isPlainObject(parsed.orphanObjects) || !Array.isArray(parsed.orphanObjects.keys)) {
    throw new Error('Inventaris tidak memuat daftar orphanObjects yang valid.');
  }
  if (!isPlainObject(parsed.summary)) {
    throw new Error('Inventaris tidak memuat ringkasan.');
  }

  const seenKeys = new Set<string>();
  const included: BacklogIncludedObject[] = [];
  for (const [index, entry] of parsed.included.entries()) {
    if (!isPlainObject(entry)) {
      throw new Error('Entri included #' + index + ' bukan objek.');
    }
    const objectKey = entry.objectKey;
    if (typeof objectKey !== 'string' || !OBJECT_KEY_PATTERN.test(objectKey)) {
      throw new Error('Entri included #' + index + ' memiliki objectKey tidak valid.');
    }
    if (seenKeys.has(objectKey)) {
      throw new Error('objectKey duplikat di inventaris: ' + objectKey);
    }
    seenKeys.add(objectKey);

    const materialIds = entry.materialIds;
    if (!Array.isArray(materialIds) || materialIds.length === 0) {
      throw new Error(
        'Entri ' + objectKey + ' wajib memiliki daftar materialId yang tidak kosong.',
      );
    }
    const uniqueIds = new Set<string>();
    for (const id of materialIds) {
      if (typeof id !== 'string' || !MATERIAL_ID_PATTERN.test(id)) {
        throw new Error('Entri ' + objectKey + ' memiliki materialId tidak valid.');
      }
      if (uniqueIds.has(id)) {
        throw new Error('Entri ' + objectKey + ' memiliki materialId duplikat.');
      }
      uniqueIds.add(id);
    }
    if (
      entry.sizeBytes !== null &&
      !(Number.isSafeInteger(entry.sizeBytes) && (entry.sizeBytes as number) >= 0)
    ) {
      throw new Error('Entri ' + objectKey + ' memiliki sizeBytes tidak valid.');
    }
    if (
      typeof entry.earliestDeletedAt !== 'string' ||
      Number.isNaN(Date.parse(entry.earliestDeletedAt))
    ) {
      throw new Error('Entri ' + objectKey + ' memiliki earliestDeletedAt tidak valid.');
    }
    included.push({
      objectKey,
      sizeBytes: entry.sizeBytes as number | null,
      materialIds: [...uniqueIds],
      earliestDeletedAt: entry.earliestDeletedAt,
    });
  }

  // Entri excluded/orphan tetap di luar sasaran; konflik dengan included adalah
  // inventaris cacat, bukan izin untuk menghapus.
  const excludedKeys = new Set<string>();
  for (const entry of parsed.excluded) {
    if (isPlainObject(entry) && typeof entry.objectKey === 'string') {
      excludedKeys.add(entry.objectKey);
    }
  }
  const orphanKeys = new Set(
    parsed.orphanObjects.keys.filter((key): key is string => typeof key === 'string'),
  );
  for (const key of seenKeys) {
    if (excludedKeys.has(key)) {
      throw new Error('objectKey ' + key + ' muncul di included dan excluded sekaligus.');
    }
    if (orphanKeys.has(key)) {
      throw new Error('objectKey ' + key + ' muncul di included dan orphanObjects sekaligus.');
    }
  }

  const summary = parsed.summary as Record<string, unknown>;
  if (summary.deletableObjects !== included.length) {
    throw new Error('Ringkasan deletableObjects tidak cocok dengan daftar included.');
  }

  return {
    version: BACKLOG_INVENTORY_VERSION,
    generatedAt: parsed.generatedAt,
    mode: 'dry-run',
    environment: approvedEnvironment,
    summary: parsed.summary as BacklogInventory['summary'],
    included,
    excluded: parsed.excluded as BacklogExcludedEntry[],
    orphanObjects: parsed.orphanObjects as BacklogInventory['orphanObjects'],
    approval: parsed.approval as BacklogInventory['approval'],
  };
}

/**
 * Kandidat backlog: materi FILE yang sudah soft-deleted dan TIDAK memiliki
 * pekerjaan penghapusan dari fitur baru. Baris seperti ini hanya mungkin berasal
 * dari soft delete sebelum fitur aktif, sehingga aman diidentifikasi sebagai
 * backlog satu kali.
 */
async function findBacklogCandidates(): Promise<BacklogCandidateRow[]> {
  const result = await getPool().query<BacklogCandidateRow>(
    `
      SELECT m.id, m.object_key, m.title, m.deleted_at
      FROM materials m
      WHERE m.kind = 'FILE'
        AND m.deleted_at IS NOT NULL
        AND NOT EXISTS (
          SELECT 1
          FROM material_object_deletions d
          WHERE d.material_id = m.id
            AND d.reason IN ('MATERIAL_DELETED', 'FILE_REPLACED')
        )
      ORDER BY m.deleted_at ASC, m.id ASC
    `,
  );
  return result.rows;
}

async function findActiveReferencedKeys(keys: string[]): Promise<Set<string>> {
  if (keys.length === 0) return new Set();
  const result = await getPool().query<{ object_key: string }>(
    `
      SELECT DISTINCT object_key
      FROM materials
      WHERE deleted_at IS NULL AND object_key = ANY($1::text[])
    `,
    [keys],
  );
  return new Set(result.rows.map((row) => row.object_key));
}

async function findKeysWithJobs(keys: string[]): Promise<Set<string>> {
  if (keys.length === 0) return new Set();
  const result = await getPool().query<{ object_key: string }>(
    `
      SELECT DISTINCT object_key
      FROM material_object_deletions
      WHERE state IN ('PENDING', 'COMPLETED', 'ABSENT')
        AND object_key = ANY($1::text[])
    `,
    [keys],
  );
  return new Set(result.rows.map((row) => row.object_key));
}

async function findOrphanObjects(): Promise<string[]> {
  const stored = await listObjectKeys('materials/');
  if (stored.length === 0) return [];
  const result = await getPool().query<{ object_key: string }>(
    `
      SELECT DISTINCT object_key
      FROM materials
      WHERE object_key = ANY($1::text[])
    `,
    [stored],
  );
  const known = new Set(result.rows.map((row) => row.object_key));
  return stored.filter((key) => !known.has(key));
}

export interface BuildInventoryOptions {
  verifyObjects?: boolean;
  scanOrphans?: boolean;
}

/**
 * Bangun inventaris kandidat backlog. Tanpa `verifyObjects`, ukuran objek tidak
 * dibaca (inventaris murni dari database). Dengan `verifyObjects`, setiap objek
 * diperiksa keberadaannya dan ukurannya agar daftar sasaran dapat dipastikan.
 * Fungsi ini tidak pernah mengubah database maupun penyimpanan.
 */
export async function buildBacklogInventory(
  options: BuildInventoryOptions = {},
): Promise<BacklogInventory> {
  const verifyObjects = options.verifyObjects ?? false;
  const scanOrphans = options.scanOrphans ?? false;

  const rows = await findBacklogCandidates();
  const materialIdsByKey = new Map<string, BacklogCandidateRow[]>();
  const excluded: BacklogExcludedEntry[] = [];

  for (const row of rows) {
    if (!row.object_key) {
      excluded.push({
        objectKey: null,
        materialIds: [row.id],
        reason: 'NO_OBJECT_KEY',
        detail: 'Materi FILE tanpa object_key; tidak ada objek untuk dihapus.',
      });
      continue;
    }
    const existing = materialIdsByKey.get(row.object_key) ?? [];
    existing.push(row);
    materialIdsByKey.set(row.object_key, existing);
  }

  const uniqueKeys = [...materialIdsByKey.keys()];
  const activeReferences = await findActiveReferencedKeys(uniqueKeys);
  const keysWithJobs = await findKeysWithJobs(uniqueKeys);

  const included: BacklogIncludedObject[] = [];
  let deletableBytes = 0;
  let unknownSizeObjects = 0;

  for (const objectKey of uniqueKeys) {
    const materialRows = materialIdsByKey.get(objectKey) ?? [];
    const materialIds = materialRows.map((row) => row.id);
    if (activeReferences.has(objectKey)) {
      excluded.push({
        objectKey,
        materialIds,
        reason: 'ACTIVE_REFERENCE',
        detail: 'Masih dirujuk materi aktif; tidak boleh dihapus.',
      });
      continue;
    }
    if (keysWithJobs.has(objectKey)) {
      excluded.push({
        objectKey,
        materialIds,
        reason: 'ALREADY_HAS_JOB',
        detail: 'Sudah ditangani antrean penghapusan fitur; tidak termasuk backlog.',
      });
      continue;
    }
    if (materialRows.length > 1) {
      excluded.push({
        objectKey,
        materialIds,
        reason: 'DUPLICATE_KEY',
        detail: 'Satu objek dirujuk beberapa baris materi; dihitung satu kali.',
      });
    }

    let sizeBytes: number | null = null;
    if (verifyObjects) {
      const stat = await objectStat(objectKey);
      if (stat === null) {
        excluded.push({
          objectKey,
          materialIds,
          reason: 'OBJECT_ABSENT',
          detail: 'Objek sudah tidak ada di penyimpanan.',
        });
        continue;
      }
      sizeBytes = stat.size;
      deletableBytes += stat.size;
    } else {
      unknownSizeObjects += 1;
    }

    included.push({
      objectKey,
      sizeBytes,
      materialIds,
      earliestDeletedAt: materialRows[0]!.deleted_at.toISOString(),
    });
  }

  let orphanKeys: string[] = [];
  if (scanOrphans) {
    orphanKeys = await findOrphanObjects();
  }

  included.sort((a, b) => a.objectKey.localeCompare(b.objectKey));

  return {
    version: BACKLOG_INVENTORY_VERSION,
    generatedAt: new Date().toISOString(),
    mode: 'dry-run',
    environment: currentEnvironmentIdentity(),
    summary: {
      softDeletedFileMaterials: rows.length,
      uniqueObjectKeys: uniqueKeys.length,
      deletableObjects: included.length,
      deletableBytes,
      unknownSizeObjects,
      excluded: excluded.length,
    },
    included,
    excluded,
    orphanObjects: {
      scanned: scanOrphans,
      count: orphanKeys.length,
      keys: orphanKeys,
      note:
        'Objek tanpa pasangan record database berada di luar daftar sasaran dan ' +
        'hanya dilaporkan terpisah.',
    },
    approval: { required: true, note: APPROVAL_NOTE },
  };
}

/**
 * Verifikasi ulang satu kandidat lalu hapus objeknya. Verifikasi berjalan di
 * dalam transaksi dengan `SELECT ... FOR UPDATE` pada baris materi sehingga
 * perubahan referensi oleh aplikasi tidak dapat terjadi di antara pemeriksaan
 * dan komit. Objek dihapus sebelum COMMIT; bila penghapusan gagal, transaksi
 * di-rollback dan tidak ada baris database yang berubah. Setelah COMMIT,
 * `DeleteObject` bersifat idempoten sehingga pengulangan aman.
 */
async function verifyAndDeleteCandidate(
  client: PoolClient,
  item: BacklogIncludedObject,
): Promise<
  | { outcome: 'DELETED'; sizeBytes: number }
  | { outcome: 'SKIPPED'; reason: BacklogSkipReason; detail: string }
> {
  const locked = await client.query<{
    id: string;
    object_key: string | null;
    kind: string;
    deleted_at: Date | null;
  }>(
    `
      SELECT id, object_key, kind, deleted_at
      FROM materials
      WHERE id = ANY($1::uuid[])
      FOR UPDATE
    `,
    [item.materialIds],
  );
  if (locked.rows.length !== item.materialIds.length) {
    return {
      outcome: 'SKIPPED',
      reason: 'PAIR_MISMATCH',
      detail: 'Ada materialId inventaris yang tidak lagi ditemukan.',
    };
  }
  for (const row of locked.rows) {
    if (row.object_key !== item.objectKey || row.kind !== 'FILE' || !row.deleted_at) {
      return {
        outcome: 'SKIPPED',
        reason: 'PAIR_MISMATCH',
        detail:
          'Pasangan materialId+objectKey tidak lagi cocok dengan materi FILE ' +
          'soft-deleted yang memenuhi syarat backlog.',
      };
    }
  }

  const active = await client.query(
    'SELECT 1 FROM materials WHERE object_key = $1 AND deleted_at IS NULL LIMIT 1',
    [item.objectKey],
  );
  if ((active.rowCount ?? 0) > 0) {
    return {
      outcome: 'SKIPPED',
      reason: 'ACTIVE_REFERENCE',
      detail: 'Objek masih dirujuk materi aktif; tidak boleh dihapus.',
    };
  }

  const jobs = await client.query(
    `
      SELECT 1
      FROM material_object_deletions
      WHERE object_key = $1
        AND reason IN ('MATERIAL_DELETED', 'FILE_REPLACED')
        AND state IN ('PENDING', 'COMPLETED', 'ABSENT')
      LIMIT 1
    `,
    [item.objectKey],
  );
  if ((jobs.rowCount ?? 0) > 0) {
    return {
      outcome: 'SKIPPED',
      reason: 'ALREADY_HAS_JOB',
      detail: 'Objek sudah ditangani antrean penghapusan fitur; bukan backlog.',
    };
  }

  // Tidak boleh ada baris backlog lain untuk key ini yang tidak tercantum di
  // inventaris (mis. soft delete baru setelah dry-run). Termasuk baris baru
  // dengan key sama, sehingga penghapusan objek tidak meninggalkan record yatim.
  const eligible = await client.query<{ id: string }>(
    `
      SELECT m.id
      FROM materials m
      WHERE m.object_key = $1
        AND m.kind = 'FILE'
        AND m.deleted_at IS NOT NULL
        AND NOT EXISTS (
          SELECT 1
          FROM material_object_deletions d
          WHERE d.material_id = m.id
            AND d.reason IN ('MATERIAL_DELETED', 'FILE_REPLACED')
        )
      ORDER BY m.id ASC
    `,
    [item.objectKey],
  );
  const eligibleIds = new Set(eligible.rows.map((row) => row.id));
  const sameSet =
    eligibleIds.size === item.materialIds.length &&
    item.materialIds.every((id) => eligibleIds.has(id));
  if (!sameSet) {
    return {
      outcome: 'SKIPPED',
      reason: 'MATERIAL_SET_CHANGED',
      detail: 'Kumpulan materi backlog untuk objek ini berubah sejak inventaris dibuat.',
    };
  }

  const stat = await objectStat(item.objectKey);
  if (stat === null) {
    return {
      outcome: 'SKIPPED',
      reason: 'OBJECT_ABSENT',
      detail: 'Objek sudah tidak ada di penyimpanan.',
    };
  }

  await deleteObject(item.objectKey);
  return { outcome: 'DELETED', sizeBytes: stat.size };
}

/**
 * Eksekusi pembersihan backlog dari inventaris yang disetujui. Setiap kandidat
 * diverifikasi ulang sebelum dihapus; kandidat yang berubah dilewati dengan
 * alasan. Hasil per objek dicatat sehingga eksekusi ulang setelah kegagalan
 * parsial aman: objek yang sudah hilang dilewati sebagai OBJECT_ABSENT.
 */
export async function executeBacklogCleanup(
  options: ExecuteBacklogOptions,
): Promise<ExecuteBacklogReport> {
  if (process.env[BACKLOG_APPROVAL_ENV] !== '1') {
    throw new Error(
      'Eksekusi backlog ditolak: set BACKLOG_CLEANUP_APPROVED=1 hanya setelah ' +
        'Project Owner menyetujui daftar sasaran.',
    );
  }

  const approvedSha256 =
    options.approvedSha256 ?? process.env[BACKLOG_SHA_ENV] ?? '';
  if (!approvedSha256) {
    throw new Error(
      'Eksekusi backlog ditolak: checksum SHA-256 inventaris yang disetujui wajib ' +
        'diberikan lewat --sha256 atau BACKLOG_CLEANUP_INVENTORY_SHA256.',
    );
  }

  const raw = await fs.readFile(options.inventoryPath);
  const current = currentEnvironmentIdentity();
  const inventory = validateApprovedInventory(raw, approvedSha256, current);
  const inventorySha256 = computeInventorySha256(raw);

  const report: ExecuteBacklogReport = {
    executedAt: new Date().toISOString(),
    mode: 'execute',
    inventoryGeneratedAt: inventory.generatedAt,
    inventorySha256,
    environment: current,
    deleted: [],
    skipped: [],
    failed: [],
    summary: { deleted: 0, skipped: 0, failed: 0 },
  };

  for (const item of inventory.included) {
    await options.onBeforeVerify?.(item.objectKey);
    const client = await getPool().connect();
    try {
      await client.query('BEGIN');
      const result = await verifyAndDeleteCandidate(client, item);
      if (result.outcome === 'SKIPPED') {
        await client.query('ROLLBACK');
        report.skipped.push({
          objectKey: item.objectKey,
          materialIds: item.materialIds,
          reason: result.reason,
          detail: result.detail,
        });
        continue;
      }
      await client.query('COMMIT');
      report.deleted.push({
        objectKey: item.objectKey,
        materialIds: item.materialIds,
        sizeBytes: result.sizeBytes,
      });
    } catch (error) {
      await client.query('ROLLBACK').catch(() => undefined);
      report.failed.push({
        objectKey: item.objectKey,
        materialIds: item.materialIds,
        error: error instanceof Error ? error.message : String(error),
      });
    } finally {
      client.release();
    }
  }

  report.summary = {
    deleted: report.deleted.length,
    skipped: report.skipped.length,
    failed: report.failed.length,
  };

  if (options.reportPath) {
    await fs.mkdir(path.dirname(path.resolve(options.reportPath)), { recursive: true });
    await fs.writeFile(
      path.resolve(options.reportPath),
      JSON.stringify(report, null, 2) + '\n',
      'utf8',
    );
  }

  return report;
}

function parseArgs(argv: string[]): {
  execute: boolean;
  inventoryPath?: string;
  reportPath?: string;
  outPath?: string;
  sha256?: string;
  scanOrphans: boolean;
  skipVerify: boolean;
} {
  const args = {
    execute: false,
    inventoryPath: undefined as string | undefined,
    reportPath: undefined as string | undefined,
    outPath: undefined as string | undefined,
    sha256: undefined as string | undefined,
    scanOrphans: false,
    skipVerify: false,
  };
  for (let index = 0; index < argv.length; index += 1) {
    const arg = argv[index];
    if (arg === '--execute') args.execute = true;
    else if (arg === '--scan-orphans') args.scanOrphans = true;
    else if (arg === '--skip-verify') args.skipVerify = true;
    else if (arg === '--inventory') args.inventoryPath = argv[++index];
    else if (arg === '--report') args.reportPath = argv[++index];
    else if (arg === '--out') args.outPath = argv[++index];
    else if (arg === '--sha256') args.sha256 = argv[++index];
  }
  return args;
}

async function main(): Promise<void> {
  const args = parseArgs(process.argv.slice(2));

  if (args.execute) {
    if (!args.inventoryPath) {
      throw new Error(
        '--execute memerlukan --inventory <file> hasil dry-run yang disetujui ' +
          'dan --sha256 <checksum persetujuan>.',
      );
    }
    const report = await executeBacklogCleanup({
      inventoryPath: args.inventoryPath,
      reportPath: args.reportPath,
      approvedSha256: args.sha256,
    });
    logger.info(report.summary, 'Pembersihan backlog selesai');
    console.log(JSON.stringify(report.summary, null, 2));
    return;
  }

  const inventory = await buildBacklogInventory({
    verifyObjects: !args.skipVerify,
    scanOrphans: args.scanOrphans,
  });
  const serialized = JSON.stringify(inventory, null, 2) + '\n';
  if (args.outPath) {
    await fs.mkdir(path.dirname(path.resolve(args.outPath)), { recursive: true });
    await fs.writeFile(path.resolve(args.outPath), serialized, 'utf8');
    const sha = computeInventorySha256(serialized);
    logger.info(
      { inventoryPath: args.outPath, sha256: sha },
      'Inventaris backlog (dry-run) ditulis; serahkan checksum ke Owner untuk persetujuan',
    );
  }
  console.log(serialized);
  logger.info(inventory.summary, 'Inventaris backlog (dry-run) selesai');
}

if (require.main === module) {
  main()
    .catch((error: unknown) => {
      logger.error({ err: error }, 'Skrip backlog gagal');
      process.exitCode = 1;
    })
    .finally(() => {
      void closePool();
    });
}

export type { ObjectDeletionReason };
