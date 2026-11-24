import type { RequestHandler } from 'express';
import { getEnv } from '../config/env';
import { AppError } from '../lib/errors';

export const CLIENT_VERSION_HEADER = 'x-eduvark-client-version';

/**
 * Guard kompatibilitas rilis. APK baseline tidak mengirim header ini; APK
 * kandidat mengirim versi kontraknya. Guard berjalan sebelum autentikasi dan
 * tidak mengubah identitas, role, atau aturan otorisasi.
 *
 * Produksi menegakkan guard secara default. Di luar produksi guard hanya aktif
 * bila CLIENT_COMPATIBILITY_ENFORCE=1, agar tes lama dan pengembangan lokal
 * tidak memerlukan header.
 */
export const requireClientCompatibility: RequestHandler = (req, _res, next) => {
  const env = getEnv();
  const enforced =
    env.NODE_ENV === 'production' || env.CLIENT_COMPATIBILITY_ENFORCE === '1';
  if (!enforced) {
    next();
    return;
  }

  if (req.method === 'OPTIONS') {
    next();
    return;
  }

  // Kesehatan layanan tetap dapat dipantau tanpa header versi.
  const path = req.path.replace(/\/+$/, '') || '/';
  if (path === '/health' || path === '/health/ready') {
    next();
    return;
  }

  const received = req.header(CLIENT_VERSION_HEADER)?.trim();
  const parsed = received && /^\d+$/.test(received) ? Number(received) : Number.NaN;
  if (!Number.isInteger(parsed) || parsed < env.CLIENT_MIN_VERSION) {
    next(
      new AppError(
        'CLIENT_VERSION_UNSUPPORTED',
        426,
        'Versi aplikasi tidak didukung. Perbarui EduVARK sebelum melanjutkan.',
        {
          minSupportedVersion: env.CLIENT_MIN_VERSION,
          receivedVersion: received ?? null,
          header: CLIENT_VERSION_HEADER,
        },
      ),
    );
    return;
  }

  next();
};
