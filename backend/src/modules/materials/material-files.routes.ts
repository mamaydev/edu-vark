import { Router } from 'express';
import { AppError } from '../../lib/errors';
import { readLocalObject } from '../../lib/storage';

export const materialFilesRouter = Router();

materialFilesRouter.get('/material-files', async (req, res, next) => {
  try {
    const key = typeof req.query.key === 'string' ? req.query.key : '';
    const expiresRaw = typeof req.query.expires === 'string' ? req.query.expires : '';
    const signature = typeof req.query.signature === 'string' ? req.query.signature : '';
    const expires = Number(expiresRaw);
    if (!key || !expiresRaw || !signature) {
      throw new AppError('SIGNED_URL_INVALID', 403, 'Tautan materi tidak valid.');
    }
    const file = await readLocalObject(key, expires, signature);
    // Tautan transisi C1-02 tetap tidak boleh di-cache klien/proxy.
    res.setHeader('Cache-Control', 'no-store');
    res.setHeader('Vary', 'Authorization');
    res.type(file.mime).send(file.buffer);
  } catch (error) {
    next(error);
  }
});
