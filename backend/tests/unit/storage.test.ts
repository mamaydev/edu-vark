import { mkdtemp, rm } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import path from 'node:path';
import { afterAll, beforeAll, describe, expect, it } from 'vitest';
import {
  deleteObject,
  getSignedObjectUrl,
  putObject,
  readLocalObject,
} from '../../src/lib/storage';

describe('local development storage', () => {
  let dir = '';

  beforeAll(async () => {
    dir = await mkdtemp(path.join(tmpdir(), 'eduvark-storage-'));
    process.env.DATABASE_URL = 'postgresql://localhost:5432/eduvark';
    process.env.NODE_ENV = 'test';
    process.env.LOCAL_STORAGE_DIR = dir;
    process.env.PUBLIC_API_BASE_URL = 'http://10.0.2.2:3000/api/v1';
    process.env.R2_ENDPOINT = '';
    process.env.R2_ACCESS_KEY_ID = '';
    process.env.R2_SECRET_ACCESS_KEY = '';
    process.env.R2_BUCKET = '';
  });

  afterAll(async () => {
    if (dir) await rm(dir, { recursive: true, force: true });
  });

  it('writes, signs, reads, and deletes a local object', async () => {
    const key = 'materials/00000000-0000-4000-8000-000000000001.pdf';
    const body = Buffer.from('%PDF-1.7 test');

    await putObject(key, body, 'application/pdf');
    const signed = new URL(await getSignedObjectUrl(key));
    const expires = Number(signed.searchParams.get('expires'));
    const signature = signed.searchParams.get('signature');

    expect(signed.pathname).toBe('/api/v1/material-files');
    expect(signature).toBeTruthy();

    const file = await readLocalObject(key, expires, signature!);
    expect(file.mime).toBe('application/pdf');
    expect(file.buffer.equals(body)).toBe(true);

    await deleteObject(key);
    await expect(readLocalObject(key, expires, signature!)).rejects.toMatchObject({
      code: 'MATERIAL_NOT_FOUND',
    });
  });
});
