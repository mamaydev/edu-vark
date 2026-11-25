import { mkdtempSync } from 'node:fs';
import { readFile, stat, unlink, writeFile } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import path from 'node:path';
import { afterAll, beforeEach, describe, expect, it, vi } from 'vitest';
import { getPool } from '../../src/db/client';
import { deleteObject, putObject } from '../../src/lib/storage';
import { processPendingObjectDeletions } from '../../src/modules/materials/object-deletions.service';
import {
  BACKLOG_INVENTORY_VERSION,
  buildBacklogInventory,
  computeInventorySha256,
  executeBacklogCleanup,
  type BacklogInventory,
} from '../../src/scripts/backlog-object-cleanup';
import {
  closeDb,
  createClass,
  createUser,
  resetDb,
} from '../helpers/db';
import { createTopic } from '../helpers/fixtures';

// Penyimpanan lokal privat: keberadaan objek sesudah eksekusi dapat dibuktikan
// langsung pada filesystem. Worker retry dimatikan agar tidak mengganggu.
const storageRoot = mkdtempSync(path.join(tmpdir(), 'eduvark-backlog-storage-'));
process.env.LOCAL_STORAGE_DIR = storageRoot;
process.env.STORAGE_DELETION_INTERVAL_MS = '0';

vi.mock('../../src/lib/storage', async (importOriginal) => {
  const actual = await importOriginal<typeof import('../../src/lib/storage')>();
  return {
    ...actual,
    deleteObject: vi.fn(actual.deleteObject),
  };
});

const describeDb = process.env.DATABASE_URL ? describe : describe.skip;
const mockedDeleteObject = vi.mocked(deleteObject);

let classId: string;
let lecturerId: string;
let topicId: string;

async function insertMaterial(input: {
  objectKey: string | null;
  kind?: 'FILE' | 'LINK';
  deletedAt?: Date | null;
  title?: string;
}): Promise<string> {
  const result = await getPool().query<{ id: string }>(
    `
      INSERT INTO materials (
        topic_id, uploaded_by, vark_type, title, kind, object_key, url, mime,
        size_bytes, deleted_at
      )
      VALUES (
        $1, $2, 'VISUAL', $3, $4, $5,
        CASE WHEN $4 = 'LINK' THEN 'https://example.com/x' ELSE NULL END,
        CASE WHEN $4 = 'FILE' THEN 'application/pdf' ELSE NULL END,
        CASE WHEN $4 = 'FILE' THEN 32 ELSE NULL END,
        $6
      )
      RETURNING id
    `,
    [
      topicId,
      lecturerId,
      input.title ?? 'Materi',
      input.kind ?? 'FILE',
      input.objectKey,
      input.deletedAt ?? null,
    ],
  );
  return result.rows[0]!.id;
}

async function seedFile(key: string, bytes = '%PDF-1.7 isi'): Promise<string> {
  await putObject(key, Buffer.from(bytes), 'application/pdf');
  return insertMaterial({
    objectKey: key,
    deletedAt: new Date(Date.now() - 86_400_000),
    title: 'Kandidat ' + key,
  });
}

interface WrittenInventory {
  path: string;
  sha256: string;
}

/**
 * Tulis inventaris persis seperti manifest dry-run lalu hitung checksum dari
 * byte yang ditulis. Checksum ini mewakili persetujuan Owner yang diberikan
 * lewat input terpisah.
 */
async function writeInventory(
  inventory: BacklogInventory,
  name = 'inventory.json',
): Promise<WrittenInventory> {
  const raw = Buffer.from(JSON.stringify(inventory, null, 2) + '\n', 'utf8');
  const inventoryPath = path.join(storageRoot, name);
  await writeFile(inventoryPath, raw);
  return { path: inventoryPath, sha256: computeInventorySha256(raw) };
}

async function writeTamperedInventory(
  inventory: BacklogInventory,
  mutate: (value: BacklogInventory) => void,
  name = 'tampered-inventory.json',
): Promise<WrittenInventory> {
  const clone = JSON.parse(JSON.stringify(inventory)) as BacklogInventory;
  mutate(clone);
  return writeInventory(clone, name);
}

async function executeApproved(
  written: WrittenInventory,
  extra: { reportPath?: string; onBeforeVerify?: (key: string) => void | Promise<void> } = {},
) {
  process.env.BACKLOG_CLEANUP_APPROVED = '1';
  try {
    return await executeBacklogCleanup({
      inventoryPath: written.path,
      approvedSha256: written.sha256,
      ...extra,
    });
  } finally {
    delete process.env.BACKLOG_CLEANUP_APPROVED;
  }
}

async function expectRejectedWithoutDeletion(
  written: WrittenInventory,
  pattern: RegExp,
  options: { approvedSha256?: string } = {},
): Promise<void> {
  process.env.BACKLOG_CLEANUP_APPROVED = '1';
  try {
    await expect(
      executeBacklogCleanup({
        inventoryPath: written.path,
        approvedSha256: options.approvedSha256 ?? written.sha256,
      }),
    ).rejects.toThrow(pattern);
  } finally {
    delete process.env.BACKLOG_CLEANUP_APPROVED;
  }
  expect(mockedDeleteObject).not.toHaveBeenCalled();
}

beforeEach(async () => {
  mockedDeleteObject.mockClear();
  await resetDb();
  classId = await createClass();
  lecturerId = await createUser({
    loginId: '1990010001',
    password: 'LecturerPass123',
    role: 'LECTURER',
  });
  topicId = await createTopic({
    classId,
    createdBy: lecturerId,
    title: 'Topik Backlog',
    status: 'DRAFT',
  });
});

afterAll(async () => {
  delete process.env.BACKLOG_CLEANUP_APPROVED;
  delete process.env.BACKLOG_CLEANUP_INVENTORY_SHA256;
  await closeDb();
});

describeDb('inventaris dan eksekusi backlog objek penyimpanan', () => {
  it('dry-run memuat identitas lingkungan tanpa kredensial, mengelompokkan kandidat, dan tidak menghapus apa pun', async () => {
    const deletableKey = 'materials/legacy-deletable.pdf';
    const deletableId = await seedFile(deletableKey, '%PDF-1.7 deletable');

    // Kandidat yang objeknya sudah tidak ada di penyimpanan.
    await insertMaterial({
      objectKey: 'materials/legacy-missing.pdf',
      deletedAt: new Date(Date.now() - 86_400_000),
      title: 'Legacy Missing',
    });

    // Key yang masih dirujuk materi aktif tidak boleh masuk daftar.
    const activeKey = 'materials/legacy-shared-active.pdf';
    await putObject(activeKey, Buffer.from('%PDF-1.7 active'), 'application/pdf');
    await insertMaterial({ objectKey: activeKey, deletedAt: null, title: 'Aktif' });
    await insertMaterial({
      objectKey: activeKey,
      deletedAt: new Date(Date.now() - 86_400_000),
      title: 'Legacy Shared',
    });

    // Key yang sudah punya pekerjaan fitur (mis. materi lain berbagi key).
    const jobKey = 'materials/legacy-with-job.pdf';
    await putObject(jobKey, Buffer.from('%PDF-1.7 job'), 'application/pdf');
    const jobMaterialId = await insertMaterial({
      objectKey: jobKey,
      deletedAt: new Date(Date.now() - 86_400_000),
      title: 'Legacy With Job',
    });
    await getPool().query(
      `
        INSERT INTO material_object_deletions (object_key, material_id, reason, state)
        VALUES ($1, $2, 'MATERIAL_DELETED', 'COMPLETED')
      `,
      [jobKey, jobMaterialId],
    );
    await insertMaterial({
      objectKey: jobKey,
      deletedAt: new Date(Date.now() - 86_400_000),
      title: 'Legacy Companion',
    });

    // Materi LINK tidak pernah punya objek penyimpanan.
    await insertMaterial({
      objectKey: null,
      kind: 'LINK',
      deletedAt: new Date(Date.now() - 86_400_000),
      title: 'Legacy Link',
    });

    const inventory = await buildBacklogInventory({
      verifyObjects: true,
      scanOrphans: true,
    });

    expect(inventory.version).toBe(BACKLOG_INVENTORY_VERSION);
    expect(inventory.mode).toBe('dry-run');
    expect(inventory.environment.storageMode).toBe('local');
    expect(inventory.environment.localStorageDir).toBe(
      path.resolve(process.cwd(), storageRoot),
    );
    expect(inventory.environment.r2).toBeNull();
    expect(inventory.environment.database).toMatch(/^[^/]+\/[^/]+/);
    expect(JSON.stringify(inventory.environment)).not.toMatch(
      /ACCESS_KEY|SECRET|PASSWORD/i,
    );

    expect(inventory.summary.softDeletedFileMaterials).toBe(4);
    expect(inventory.summary.uniqueObjectKeys).toBe(4);
    expect(inventory.summary.deletableObjects).toBe(1);
    expect(inventory.summary.deletableBytes).toBe(
      Buffer.byteLength('%PDF-1.7 deletable'),
    );
    expect(inventory.included.map((item) => item.objectKey)).toEqual([deletableKey]);
    expect(inventory.included[0]?.materialIds).toEqual([deletableId]);
    expect(inventory.excluded.map((item) => item.reason).sort()).toEqual([
      'ACTIVE_REFERENCE',
      'ALREADY_HAS_JOB',
      'OBJECT_ABSENT',
    ]);
    expect(inventory.orphanObjects.scanned).toBe(true);
    expect(inventory.approval.required).toBe(true);

    // Dry-run tidak menghapus objek apa pun dan tidak memanggil deleteObject.
    await stat(path.join(storageRoot, deletableKey));
    await stat(path.join(storageRoot, activeKey));
    expect(mockedDeleteObject).not.toHaveBeenCalled();
  }, 20_000);

  it('worker antrean tidak pernah menyentuh backlog lama saat backend aktif/restart', async () => {
    const legacyKey = 'materials/legacy-untouched.pdf';
    await putObject(legacyKey, Buffer.from('%PDF-1.7 lama'), 'application/pdf');
    await insertMaterial({
      objectKey: legacyKey,
      deletedAt: new Date(Date.now() - 86_400_000),
      title: 'Legacy Tanpa Pekerjaan',
    });

    // Worker hanya memproses baris material_object_deletions PENDING. Backlog
    // lama tidak punya baris pekerjaan, sehingga tick worker tidak menyentuhnya.
    await processPendingObjectDeletions();
    await processPendingObjectDeletions();

    await stat(path.join(storageRoot, legacyKey));
    const jobs = await getPool().query<{ count: string }>(
      'SELECT count(*)::text AS count FROM material_object_deletions',
    );
    expect(Number(jobs.rows[0]?.count)).toBe(0);
  }, 20_000);

  it('eksekusi ditolak tanpa persetujuan Project Owner', async () => {
    const key = 'materials/legacy-no-approval.pdf';
    await seedFile(key);
    const written = await writeInventory(await buildBacklogInventory({ verifyObjects: true }));

    delete process.env.BACKLOG_CLEANUP_APPROVED;
    await expect(
      executeBacklogCleanup({ inventoryPath: written.path, approvedSha256: written.sha256 }),
    ).rejects.toThrow(/BACKLOG_CLEANUP_APPROVED/);
    expect(mockedDeleteObject).not.toHaveBeenCalled();
    await stat(path.join(storageRoot, key));
  }, 20_000);

  it('eksekusi ditolak tanpa checksum persetujuan yang diberikan terpisah', async () => {
    const key = 'materials/legacy-no-checksum.pdf';
    await seedFile(key);
    const written = await writeInventory(await buildBacklogInventory({ verifyObjects: true }));

    process.env.BACKLOG_CLEANUP_APPROVED = '1';
    try {
      await expect(executeBacklogCleanup({ inventoryPath: written.path })).rejects.toThrow(
        /checksum|SHA-256/i,
      );
    } finally {
      delete process.env.BACKLOG_CLEANUP_APPROVED;
    }
    expect(mockedDeleteObject).not.toHaveBeenCalled();
    await stat(path.join(storageRoot, key));
  }, 20_000);

  it('checksum salah menolak eksekusi tanpa satu pun panggilan deleteObject', async () => {
    const key = 'materials/legacy-wrong-checksum.pdf';
    await seedFile(key);
    const written = await writeInventory(await buildBacklogInventory({ verifyObjects: true }));

    await expectRejectedWithoutDeletion(
      written,
      /Checksum inventaris tidak cocok/,
      { approvedSha256: '0'.repeat(64) },
    );
    await stat(path.join(storageRoot, key));
  }, 20_000);

  it('lingkungan berbeda (database) menolak eksekusi tanpa menghapus objek', async () => {
    const key = 'materials/legacy-wrong-environment.pdf';
    await seedFile(key);
    const inventory = await buildBacklogInventory({ verifyObjects: true });
    const written = await writeTamperedInventory(inventory, (value) => {
      value.environment.database = 'db-lain:5432/eduvark_lain';
    });

    await expectRejectedWithoutDeletion(written, /lingkungan aktif/);
    await stat(path.join(storageRoot, key));
  }, 20_000);

  it('inventaris cacat (IDs kosong, key tidak valid, duplikat, mode salah) ditolak sebelum penghapusan', async () => {
    const key = 'materials/legacy-malformed.pdf';
    await seedFile(key);
    const base = await buildBacklogInventory({ verifyObjects: true });
    expect(base.included).toHaveLength(1);

    const cases: { name: string; mutate: (value: BacklogInventory) => void; pattern: RegExp }[] = [
      {
        name: 'empty material ids',
        mutate: (value) => {
          value.included[0]!.materialIds = [];
        },
        pattern: /materialId/,
      },
      {
        name: 'invalid object key',
        mutate: (value) => {
          value.included[0]!.objectKey = '../etc/passwd';
        },
        pattern: /objectKey/,
      },
      {
        name: 'duplicate object key',
        mutate: (value) => {
          value.included.push({ ...value.included[0]! });
        },
        pattern: /duplikat|Ringkasan/,
      },
      {
        name: 'wrong mode',
        mutate: (value) => {
          (value as unknown as { mode: string }).mode = 'execute';
        },
        pattern: /dry-run/,
      },
      {
        name: 'included also excluded',
        mutate: (value) => {
          value.excluded.push({
            objectKey: value.included[0]!.objectKey,
            materialIds: [...value.included[0]!.materialIds],
            reason: 'ACTIVE_REFERENCE',
            detail: 'konflik',
          });
        },
        pattern: /excluded/,
      },
      {
        name: 'wrong inventory version',
        mutate: (value) => {
          (value as unknown as { version: number }).version = 1;
        },
        pattern: /Versi/,
      },
    ];

    for (const testCase of cases) {
      mockedDeleteObject.mockClear();
      const written = await writeTamperedInventory(
        base,
        testCase.mutate,
        'malformed-' + testCase.name.replaceAll(' ', '-') + '.json',
      );
      await expectRejectedWithoutDeletion(written, testCase.pattern);
      await stat(path.join(storageRoot, key));
    }
  }, 30_000);

  it('eksekusi valid menghapus hanya sasaran, melaporkan per objek, dan aman diulang setelah kegagalan parsial', async () => {
    const deletableKey = 'materials/legacy-approved.pdf';
    await seedFile(deletableKey, '%PDF-1.7 approved');
    const partiallyGoneKey = 'materials/legacy-partial.pdf';
    await seedFile(partiallyGoneKey, '%PDF-1.7 partial');

    // Inventaris disetujui saat kedua objek masih ada; kegagalan parsial terjadi
    // SETELAH persetujuan (objek kedua hilang sebelum eksekusi).
    const inventory = await buildBacklogInventory({ verifyObjects: true });
    expect(inventory.included).toHaveLength(2);
    await unlink(path.join(storageRoot, partiallyGoneKey));
    const written = await writeInventory(inventory);
    const reportPath = path.join(storageRoot, 'execute-report.json');

    const report = await executeApproved(written, { reportPath });
    expect(report.mode).toBe('execute');
    expect(report.inventorySha256).toBe(written.sha256);
    expect(report.summary.deleted).toBe(1);
    expect(report.summary.skipped).toBe(1);
    expect(report.deleted[0]?.objectKey).toBe(deletableKey);
    expect(report.skipped[0]?.objectKey).toBe(partiallyGoneKey);
    expect(report.skipped[0]?.reason).toBe('OBJECT_ABSENT');

    await expect(stat(path.join(storageRoot, deletableKey))).rejects.toThrow();
    const writtenReport = JSON.parse(await readFile(reportPath, 'utf8'));
    expect(writtenReport.summary).toEqual({ deleted: 1, skipped: 1, failed: 0 });

    // Dijalankan ulang dengan inventaris & checksum yang sama: semua objek
    // sekarang tidak ada sehingga dilewati, tidak ada kegagalan baru.
    const second = await executeApproved(written);
    expect(second.summary.deleted).toBe(0);
    expect(second.summary.skipped).toBe(2);
    expect(second.skipped.map((item) => item.reason)).toEqual([
      'OBJECT_ABSENT',
      'OBJECT_ABSENT',
    ]);
  }, 30_000);

  it('objectKey yang tidak lagi cocok dengan record dilewati tanpa deleteObject', async () => {
    const key = 'materials/legacy-key-changed.pdf';
    const materialId = await seedFile(key);
    const inventory = await buildBacklogInventory({ verifyObjects: true });
    const written = await writeInventory(inventory);

    await getPool().query(
      "UPDATE materials SET object_key = 'materials/legacy-key-baru.pdf' WHERE id = $1",
      [materialId],
    );

    const report = await executeApproved(written);
    expect(report.summary.deleted).toBe(0);
    expect(report.skipped[0]?.reason).toBe('PAIR_MISMATCH');
    expect(mockedDeleteObject).not.toHaveBeenCalled();
    await stat(path.join(storageRoot, key));
  }, 20_000);

  it('referensi aktif yang muncul setelah dry-run melindungi objek dari penghapusan', async () => {
    const key = 'materials/legacy-reference-gained.pdf';
    await seedFile(key);
    const inventory = await buildBacklogInventory({ verifyObjects: true });
    const written = await writeInventory(inventory);

    // Referensi aktif baru setelah inventaris disetujui (sebelum batch).
    await insertMaterial({ objectKey: key, deletedAt: null, title: 'Aktif Baru' });

    const report = await executeApproved(written);
    expect(report.summary.deleted).toBe(0);
    expect(report.skipped[0]?.reason).toBe('ACTIVE_REFERENCE');
    expect(mockedDeleteObject).not.toHaveBeenCalled();
    await stat(path.join(storageRoot, key));
  }, 20_000);

  it('referensi aktif yang muncul setelah batch mulai tetap terdeteksi di bawah kunci baris', async () => {
    const firstKey = 'materials/legacy-batch-a.pdf';
    const protectedKey = 'materials/legacy-batch-z.pdf';
    await seedFile(firstKey);
    await seedFile(protectedKey);
    const inventory = await buildBacklogInventory({ verifyObjects: true });
    const written = await writeInventory(inventory);

    // Batch sudah dimulai; referensi aktif untuk objek kedua ditambahkan tepat
    // sebelum verifikasi objek tersebut.
    const report = await executeApproved(written, {
      onBeforeVerify: async (objectKey) => {
        if (objectKey === protectedKey) {
          await insertMaterial({
            objectKey: protectedKey,
            deletedAt: null,
            title: 'Aktif Saat Batch',
          });
        }
      },
    });

    expect(report.summary.deleted).toBe(1);
    expect(report.deleted[0]?.objectKey).toBe(firstKey);
    expect(report.skipped[0]?.objectKey).toBe(protectedKey);
    expect(report.skipped[0]?.reason).toBe('ACTIVE_REFERENCE');
    await expect(stat(path.join(storageRoot, firstKey))).rejects.toThrow();
    await stat(path.join(storageRoot, protectedKey));
  }, 30_000);

  it('pekerjaan antrean yang muncul setelah dry-run membuat objek dilewati sebagai bukan backlog', async () => {
    const key = 'materials/legacy-job-gained.pdf';
    const materialId = await seedFile(key);
    const inventory = await buildBacklogInventory({ verifyObjects: true });
    const written = await writeInventory(inventory);

    await getPool().query(
      `
        INSERT INTO material_object_deletions (object_key, material_id, reason, state)
        VALUES ($1, $2, 'MATERIAL_DELETED', 'COMPLETED')
      `,
      [key, materialId],
    );

    const report = await executeApproved(written);
    expect(report.summary.deleted).toBe(0);
    expect(report.skipped[0]?.reason).toBe('ALREADY_HAS_JOB');
    expect(mockedDeleteObject).not.toHaveBeenCalled();
    await stat(path.join(storageRoot, key));
  }, 20_000);
});
