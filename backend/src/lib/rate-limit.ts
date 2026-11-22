import type { RequestHandler } from 'express';
import { AppError } from './errors';

interface Bucket {
  count: number;
  resetAt: number;
}

const WINDOW_MS = 5 * 60 * 1000;
const MAX_REQUESTS = 1800;
const buckets = new Map<string, Bucket>();

/**
 * Small in-memory guard for the public API. It is intentionally generous: the
 * goal is to stop trivial floods, not to shape traffic. A restart clears state
 * (single backend instance, homelab scope).
 */
export const apiRateLimit: RequestHandler = (req, _res, next) => {
  if (req.method === 'OPTIONS') {
    next();
    return;
  }

  const key = req.ip ?? 'unknown';
  const now = Date.now();
  const bucket = buckets.get(key);

  if (!bucket || now >= bucket.resetAt) {
    buckets.set(key, { count: 1, resetAt: now + WINDOW_MS });
    next();
    return;
  }

  bucket.count += 1;
  if (bucket.count > MAX_REQUESTS) {
    next(
      new AppError(
        'TOO_MANY_REQUESTS',
        429,
        'Terlalu banyak permintaan. Coba lagi beberapa saat lagi.',
      ),
    );
    return;
  }

  next();
};

export function resetApiRateLimit(): void {
  buckets.clear();
}
