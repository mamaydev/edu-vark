import { getEnv } from '../config/env';
import { AppError, unprocessable } from './errors';

const ALLOWED_MIME = new Set([
  'application/pdf',
  'image/png',
  'image/jpeg',
  'image/gif',
  'audio/mpeg',
  'audio/mp4',
  'audio/wav',
  'audio/ogg',
  'video/mp4',
  'video/webm',
]);

const EXTENSION: Record<string, string> = {
  'application/pdf': '.pdf',
  'image/png': '.png',
  'image/jpeg': '.jpg',
  'image/gif': '.gif',
  'audio/mpeg': '.mp3',
  'audio/mp4': '.m4a',
  'audio/wav': '.wav',
  'audio/ogg': '.ogg',
  'video/mp4': '.mp4',
  'video/webm': '.webm',
};

export interface UploadedFileInput {
  buffer: Buffer;
  mimetype: string;
  size: number;
  originalname: string;
}

export interface ValidatedFile {
  mime: string;
  extension: string;
}

export function detectMime(buffer: Buffer): string | null {
  if (buffer.length >= 5 && buffer.subarray(0, 5).toString('ascii') === '%PDF-') {
    return 'application/pdf';
  }
  if (
    buffer.length >= 8 &&
    buffer[0] === 0x89 &&
    buffer[1] === 0x50 &&
    buffer[2] === 0x4e &&
    buffer[3] === 0x47 &&
    buffer[4] === 0x0d &&
    buffer[5] === 0x0a &&
    buffer[6] === 0x1a &&
    buffer[7] === 0x0a
  ) {
    return 'image/png';
  }
  if (buffer.length >= 3 && buffer[0] === 0xff && buffer[1] === 0xd8 && buffer[2] === 0xff) {
    return 'image/jpeg';
  }
  if (
    buffer.length >= 6 &&
    (buffer.subarray(0, 6).toString('ascii') === 'GIF87a' ||
      buffer.subarray(0, 6).toString('ascii') === 'GIF89a')
  ) {
    return 'image/gif';
  }
  if (buffer.length >= 3 && buffer.subarray(0, 3).toString('ascii') === 'ID3') {
    return 'audio/mpeg';
  }
  if (buffer.length >= 2 && buffer[0] === 0xff && (buffer[1] & 0xe0) === 0xe0) {
    return 'audio/mpeg';
  }
  if (
    buffer.length >= 12 &&
    buffer.subarray(0, 4).toString('ascii') === 'RIFF' &&
    buffer.subarray(8, 12).toString('ascii') === 'WAVE'
  ) {
    return 'audio/wav';
  }
  if (buffer.length >= 4 && buffer.subarray(0, 4).toString('ascii') === 'OggS') {
    return 'audio/ogg';
  }
  if (buffer.length >= 12 && buffer.subarray(4, 8).toString('ascii') === 'ftyp') {
    const brand = buffer.subarray(8, 12).toString('ascii');
    return brand.startsWith('M4A') ? 'audio/mp4' : 'video/mp4';
  }
  if (
    buffer.length >= 4 &&
    buffer[0] === 0x1a &&
    buffer[1] === 0x45 &&
    buffer[2] === 0xdf &&
    buffer[3] === 0xa3
  ) {
    return 'video/webm';
  }
  return null;
}

export function validateUploadedFile(file: UploadedFileInput): ValidatedFile {
  const env = getEnv();
  if (file.size <= 0 || file.buffer.length === 0) {
    throw unprocessable('EMPTY_FILE', 'File tidak boleh kosong.');
  }
  if (file.size > env.MAX_UPLOAD_BYTES) {
    throw new AppError('FILE_TOO_LARGE', 413, 'Ukuran file melebihi batas.');
  }

  const detected = detectMime(file.buffer);
  if (!detected || !ALLOWED_MIME.has(detected)) {
    throw unprocessable(
      'INVALID_FILE_TYPE',
      'Tipe file tidak diizinkan. Gunakan PDF, gambar, audio, atau video.',
    );
  }

  if (file.mimetype !== detected) {
    throw unprocessable(
      'INVALID_FILE_TYPE',
      'Tipe file tidak sesuai dengan isi file.',
    );
  }

  return { mime: detected, extension: EXTENSION[detected] ?? '' };
}
