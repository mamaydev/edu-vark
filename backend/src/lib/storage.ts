import { createHash, createHmac, timingSafeEqual } from 'node:crypto';
import { createReadStream } from 'node:fs';
import { mkdir, readdir, readFile, stat, unlink, writeFile } from 'node:fs/promises';
import path from 'node:path';
import {
  DeleteObjectCommand,
  GetObjectCommand,
  HeadObjectCommand,
  ListObjectsV2Command,
  PutObjectCommand,
  S3Client,
} from '@aws-sdk/client-s3';
import { getSignedUrl } from '@aws-sdk/s3-request-presigner';
import { getEnv } from '../config/env';
import { AppError } from './errors';

let client: S3Client | null = null;

const LOCAL_MIME: Record<string, string> = {
  '.pdf': 'application/pdf',
  '.png': 'image/png',
  '.jpg': 'image/jpeg',
  '.jpeg': 'image/jpeg',
  '.gif': 'image/gif',
  '.mp3': 'audio/mpeg',
  '.m4a': 'audio/mp4',
  '.wav': 'audio/wav',
  '.ogg': 'audio/ogg',
  '.mp4': 'video/mp4',
  '.webm': 'video/webm',
};

function hasR2(): boolean {
  const env = getEnv();
  return Boolean(
    env.R2_ENDPOINT &&
      env.R2_ACCESS_KEY_ID &&
      env.R2_SECRET_ACCESS_KEY &&
      env.R2_BUCKET,
  );
}

function isLocalStorage(): boolean {
  const env = getEnv();
  return !hasR2() && env.NODE_ENV !== 'production';
}

function localDir(): string {
  return path.resolve(process.cwd(), getEnv().LOCAL_STORAGE_DIR ?? 'storage');
}

function localPath(key: string): string {
  const root = localDir();
  const resolved = path.resolve(root, key);
  if (!resolved.startsWith(root + path.sep)) {
    throw new AppError('STORAGE_UNAVAILABLE', 500, 'Kunci penyimpanan tidak valid.');
  }
  return resolved;
}

function localSecret(): string {
  return getEnv().JWT_ACCESS_SECRET ?? 'eduvark-local-storage-development-secret';
}

function localSignature(key: string, expires: number): string {
  return createHmac('sha256', localSecret())
    .update(key + ':' + expires)
    .digest('hex');
}

function config(): { client: S3Client; bucket: string; ttl: number } {
  const env = getEnv();
  if (!hasR2()) {
    throw new AppError(
      'STORAGE_UNAVAILABLE',
      502,
      'Penyimpanan materi belum dikonfigurasi.',
    );
  }

  if (!client) {
    client = new S3Client({
      region: 'auto',
      endpoint: env.R2_ENDPOINT,
      credentials: {
        accessKeyId: env.R2_ACCESS_KEY_ID!,
        secretAccessKey: env.R2_SECRET_ACCESS_KEY!,
      },
    });
  }

  return { client, bucket: env.R2_BUCKET!, ttl: env.R2_SIGNED_URL_TTL_SECONDS };
}

function storageFailure(error: unknown): AppError {
  if (error instanceof AppError) return error;
  return new AppError(
    'STORAGE_UNAVAILABLE',
    502,
    'Penyimpanan materi sedang tidak tersedia.',
  );
}

export async function putObject(
  key: string,
  body: Buffer,
  _contentType: string,
): Promise<void> {
  if (isLocalStorage()) {
    try {
      const filePath = localPath(key);
      await mkdir(path.dirname(filePath), { recursive: true });
      await writeFile(filePath, body);
      return;
    } catch (error) {
      throw storageFailure(error);
    }
  }

  try {
    const cfg = config();
    await cfg.client.send(
      new PutObjectCommand({
        Bucket: cfg.bucket,
        Key: key,
        Body: body,
        ContentType: _contentType,
      }),
    );
  } catch (error) {
    throw storageFailure(error);
  }
}

export async function getSignedObjectUrl(key: string): Promise<string> {
  if (isLocalStorage()) {
    const env = getEnv();
    const ttl = env.R2_SIGNED_URL_TTL_SECONDS;
    const expires = Math.floor(Date.now() / 1000) + ttl;
    const base = (env.PUBLIC_API_BASE_URL ?? 'http://10.0.2.2:3000/api/v1').replace(
      /\/$/,
      '',
    );
    return (
      base +
      '/material-files?key=' +
      encodeURIComponent(key) +
      '&expires=' +
      expires +
      '&signature=' +
      localSignature(key, expires)
    );
  }

  try {
    const cfg = config();
    return await getSignedUrl(
      cfg.client,
      new GetObjectCommand({ Bucket: cfg.bucket, Key: key }),
      { expiresIn: cfg.ttl },
    );
  } catch (error) {
    throw storageFailure(error);
  }
}

export async function deleteObject(key: string): Promise<void> {
  if (isLocalStorage()) {
    try {
      await unlink(localPath(key));
    } catch (error) {
      const code = (error as NodeJS.ErrnoException).code;
      if (code !== 'ENOENT') throw storageFailure(error);
    }
    return;
  }

  try {
    const cfg = config();
    await cfg.client.send(
      new DeleteObjectCommand({ Bucket: cfg.bucket, Key: key }),
    );
  } catch (error) {
    throw storageFailure(error);
  }
}

export async function readLocalObject(
  key: string,
  expires: number,
  signature: string,
): Promise<{ buffer: Buffer; mime: string }> {
  if (!isLocalStorage()) {
    throw new AppError('STORAGE_UNAVAILABLE', 404, 'Materi tidak ditemukan.');
  }
  if (!Number.isFinite(expires) || expires < Math.floor(Date.now() / 1000)) {
    throw new AppError('SIGNED_URL_EXPIRED', 403, 'Tautan materi sudah kedaluwarsa.');
  }

  const expected = localSignature(key, expires);
  const actualBuffer = Buffer.from(signature, 'utf8');
  const expectedBuffer = Buffer.from(expected, 'utf8');
  if (
    actualBuffer.length !== expectedBuffer.length ||
    !timingSafeEqual(actualBuffer, expectedBuffer)
  ) {
    throw new AppError('SIGNED_URL_INVALID', 403, 'Tautan materi tidak valid.');
  }

  const filePath = localPath(key);
  try {
    const buffer = await readFile(filePath);
    return { buffer, mime: LOCAL_MIME[path.extname(filePath).toLowerCase()] ?? 'application/octet-stream' };
  } catch (error) {
    const code = (error as NodeJS.ErrnoException).code;
    if (code === 'ENOENT') {
      throw new AppError('MATERIAL_NOT_FOUND', 404, 'Materi tidak ditemukan.');
    }
    throw storageFailure(error);
  }
}

/**
 * Metadata for a private stored object. Dipakai oleh endpoint unduh tetap
 * (`GET /api/v1/materials/:id/content`) untuk menghitung ETag, panjang isi,
 * serta validasi `Range`/`If-Range` sebelum objek dibaca.
 */
export interface ObjectInfo {
  key: string;
  size: number;
  contentType: string;
  etag: string;
  lastModified: string | null;
}

export interface ObjectByteRange {
  start: number;
  end: number;
}

function mimeForKey(key: string): string {
  return LOCAL_MIME[path.extname(key).toLowerCase()] ?? 'application/octet-stream';
}

function normalizeEtag(value: string | undefined): string {
  if (!value) return '';
  return value.startsWith('"') ? value : '"' + value + '"';
}

function isStorageNotFound(error: unknown): boolean {
  const code = (error as NodeJS.ErrnoException).code;
  if (code === 'ENOENT' || code === 'ENOTDIR' || code === 'NotFound' || code === 'NoSuchKey') {
    return true;
  }
  const status = (error as { $metadata?: { httpStatusCode?: number } }).$metadata?.httpStatusCode;
  return status === 404;
}

/**
 * Baca metadata objek privat tanpa mengirim isi. Objek yang tidak ada
 * dilaporkan sebagai MATERIAL_NOT_FOUND agar route tidak membocorkan
 * perbedaan antara "materi tidak berhak" dan "objek hilang".
 */
export async function getObjectInfo(key: string): Promise<ObjectInfo> {
  if (isLocalStorage()) {
    try {
      const filePath = localPath(key);
      const info = await stat(filePath);
      if (!info.isFile()) {
        throw new AppError('MATERIAL_NOT_FOUND', 404, 'Materi tidak ditemukan.');
      }
      const etag =
        '"' +
        createHash('sha256')
          .update(key + ':' + info.size + ':' + info.mtimeMs)
          .digest('hex')
          .slice(0, 24) +
        '"';
      return {
        key,
        size: info.size,
        contentType: mimeForKey(key),
        etag,
        lastModified: info.mtime.toUTCString(),
      };
    } catch (error) {
      if (error instanceof AppError) throw error;
      if (isStorageNotFound(error)) {
        throw new AppError('MATERIAL_NOT_FOUND', 404, 'Materi tidak ditemukan.');
      }
      throw storageFailure(error);
    }
  }

  try {
    const cfg = config();
    const head = await cfg.client.send(
      new HeadObjectCommand({ Bucket: cfg.bucket, Key: key }),
    );
    return {
      key,
      size: Number(head.ContentLength ?? 0),
      contentType: head.ContentType ?? mimeForKey(key),
      etag: normalizeEtag(head.ETag),
      lastModified: head.LastModified ? head.LastModified.toUTCString() : null,
    };
  } catch (error) {
    if (isStorageNotFound(error)) {
      throw new AppError('MATERIAL_NOT_FOUND', 404, 'Materi tidak ditemukan.');
    }
    throw storageFailure(error);
  }
}

/** Mode penyimpanan aktif; dipakai skrip inventaris backlog untuk laporan. */
export function storageMode(): 'r2' | 'local' {
  return isLocalStorage() ? 'local' : 'r2';
}

/**
 * Ukuran objek bila ada, atau `null` bila objek sudah tidak ada. Berbeda dari
 * `getObjectInfo`, ketiadaan objek bukan error: worker penghapusan dan skrip
 * inventaris perlu membedakan "sudah hilang" dari "penyimpanan gagal".
 */
export async function objectStat(key: string): Promise<{ size: number } | null> {
  if (isLocalStorage()) {
    try {
      const info = await stat(localPath(key));
      if (!info.isFile()) return null;
      return { size: info.size };
    } catch (error) {
      if (isStorageNotFound(error)) return null;
      throw storageFailure(error);
    }
  }

  try {
    const cfg = config();
    const head = await cfg.client.send(
      new HeadObjectCommand({ Bucket: cfg.bucket, Key: key }),
    );
    return { size: Number(head.ContentLength ?? 0) };
  } catch (error) {
    if (isStorageNotFound(error)) return null;
    throw storageFailure(error);
  }
}

/**
 * Daftar kunci objek pada prefix tertentu. Hanya dipakai skrip inventaris
 * backlog (dry-run) untuk melaporkan objek yatim; tidak dipakai alur runtime.
 */
export async function listObjectKeys(prefix: string): Promise<string[]> {
  if (isLocalStorage()) {
    const root = localDir();
    const start = path.resolve(root, prefix);
    if (!start.startsWith(root + path.sep)) {
      throw new AppError('STORAGE_UNAVAILABLE', 500, 'Prefix penyimpanan tidak valid.');
    }
    const keys: string[] = [];
    const walk = async (dir: string): Promise<void> => {
      let entries;
      try {
        entries = await readdir(dir, { withFileTypes: true });
      } catch (error) {
        if (isStorageNotFound(error)) return;
        throw storageFailure(error);
      }
      for (const entry of entries) {
        const full = path.join(dir, entry.name);
        if (entry.isDirectory()) {
          await walk(full);
        } else if (entry.isFile()) {
          keys.push(path.relative(root, full).split(path.sep).join('/'));
        }
      }
    };
    await walk(start);
    return keys.sort();
  }

  const cfg = config();
  const keys: string[] = [];
  let token: string | undefined;
  do {
    const page = await cfg.client.send(
      new ListObjectsV2Command({
        Bucket: cfg.bucket,
        Prefix: prefix,
        ContinuationToken: token,
      }),
    );
    for (const item of page.Contents ?? []) {
      if (item.Key) keys.push(item.Key);
    }
    token = page.IsTruncated ? page.NextContinuationToken : undefined;
  } while (token);
  return keys.sort();
}

/**
 * Ambil isi objek privat sebagai stream (R2 GetObject) atau stream berkas
 * lokal pada mode pengembangan. `range` sudah divalidasi pemanggil.
 */
export async function getObjectBody(
  key: string,
  range: ObjectByteRange | null,
): Promise<NodeJS.ReadableStream | Buffer> {
  if (isLocalStorage()) {
    try {
      localPath(key);
      return range
        ? createReadStream(localPath(key), { start: range.start, end: range.end })
        : createReadStream(localPath(key));
    } catch (error) {
      throw storageFailure(error);
    }
  }

  try {
    const cfg = config();
    const response = await cfg.client.send(
      new GetObjectCommand({
        Bucket: cfg.bucket,
        Key: key,
        Range: range ? 'bytes=' + range.start + '-' + range.end : undefined,
      }),
    );
    const body = response.Body as unknown as NodeJS.ReadableStream | undefined;
    if (!body) {
      throw new AppError(
        'STORAGE_UNAVAILABLE',
        502,
        'Penyimpanan materi sedang tidak tersedia.',
      );
    }
    return body;
  } catch (error) {
    throw storageFailure(error);
  }
}
