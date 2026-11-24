import type { Request, RequestHandler } from 'express';
import path from 'node:path';
import { AppError, unauthorized } from '../../lib/errors';
import type { AuthContext } from '../../types/auth';
import { uuidSchema } from '../topics/topics.schema';
import * as materialsService from './materials.service';

function auth(req: Request): AuthContext {
  if (!req.auth) {
    throw unauthorized('UNAUTHENTICATED', 'Token akses diperlukan.');
  }
  return req.auth;
}

function contentDisposition(material: { title: string; object_key: string | null }): string {
  const extension = path.extname(material.object_key ?? '');
  const base = material.title.replace(/[^\x20-\x7e]/g, '_').replace(/["\\]/g, '_');
  const ascii = (base.trim() || 'materi') + extension;
  const utf8 = encodeURIComponent(material.title + extension);
  return 'attachment; filename="' + ascii + '"; filename*=UTF-8\'\'' + utf8;
}

export const downloadMaterialContentHandler: RequestHandler = async (req, res, next) => {
  try {
    const context = auth(req);
    const materialId = uuidSchema.parse(req.params.id);
    const rangeHeader =
      typeof req.headers.range === 'string' ? req.headers.range : undefined;
    const ifRangeHeader =
      typeof req.headers['if-range'] === 'string' ? req.headers['if-range'] : undefined;

    let resolved: materialsService.ResolvedFileContent;
    try {
      resolved = await materialsService.resolveFileContent(context, materialId, {
        range: rangeHeader,
        ifRange: ifRangeHeader,
      });
    } catch (error) {
      if (error instanceof AppError && error.code === 'RANGE_NOT_SATISFIABLE') {
        const size = (error.details as { size?: number } | undefined)?.size;
        if (typeof size === 'number') {
          res.setHeader('Content-Range', 'bytes */' + size);
          res.setHeader('Accept-Ranges', 'bytes');
        }
      }
      throw error;
    }

    // Isi objek diambil sebelum header dikirim agar kegagalan storage tetap
    // menghasilkan error envelope JSON, bukan respons setengah jadi.
    const body = await materialsService.openFileContentBody(resolved);

    const { info, range, partial } = resolved;
    const length = range ? range.end - range.start + 1 : info.size;
    res.status(partial ? 206 : 200);
    res.setHeader('Content-Type', info.contentType);
    res.setHeader('Content-Length', String(length));
    res.setHeader('Content-Disposition', contentDisposition(resolved.material));
    // Isi materi privat tidak boleh tersimpan di cache klien/proxy mana pun.
    res.setHeader('Cache-Control', 'no-store');
    res.setHeader('Vary', 'Authorization');
    res.setHeader('Accept-Ranges', 'bytes');
    if (info.etag) res.setHeader('ETag', info.etag);
    if (range) {
      res.setHeader(
        'Content-Range',
        'bytes ' + range.start + '-' + range.end + '/' + info.size,
      );
    }

    if (Buffer.isBuffer(body)) {
      res.end(body);
      return;
    }
    body.on('error', (error) => {
      res.destroy(error);
    });
    body.pipe(res);
  } catch (error) {
    next(error);
  }
};

export const getMaterialLinkTargetHandler: RequestHandler = async (req, res, next) => {
  try {
    const context = auth(req);
    const materialId = uuidSchema.parse(req.params.id);
    const url = await materialsService.resolveLinkTarget(context, materialId);
    // Tujuan tautan bisa berubah bila izin dicabut pada permintaan berikutnya.
    res.setHeader('Cache-Control', 'no-store');
    res.setHeader('Vary', 'Authorization');
    res.status(200).json({ data: { url } });
  } catch (error) {
    next(error);
  }
};
