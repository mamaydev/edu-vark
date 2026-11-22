import type { RequestHandler } from 'express';
import { getPool } from '../db/client';
import { AppError } from '../lib/errors';
import { verifyAccessToken } from '../lib/tokens';
import type { UserRole } from '../types/auth';
import * as authRepo from '../modules/auth/auth.repository';
import { parseDurationMs } from '../lib/duration';

export const authenticate: RequestHandler = async (req, res, next) => {
  try {
    const header = req.header('authorization');
    if (!header?.startsWith('Bearer ')) {
      throw new AppError('UNAUTHENTICATED', 401, 'Token akses diperlukan.');
    }

    const token = header.slice('Bearer '.length).trim();
    const payload = verifyAccessToken(token);

    const client = await getPool().connect();
    try {
      const session = await authRepo.findSessionContext(client, payload.sessionId);

      if (!session || session.user_id !== payload.userId) {
        throw new AppError('SESSION_INVALID', 401, 'Sesi tidak valid.');
      }

      if (!session.is_active || session.revoked_at !== null) {
        throw new AppError('SESSION_REVOKED', 401, 'Sesi sudah dicabut.');
      }

      const now = Date.now();
      if (new Date(session.expires_at).getTime() <= now) {
        await authRepo.revokeSession(client, session.session_id);
        throw new AppError('SESSION_EXPIRED', 401, 'Sesi sudah berakhir.');
      }

      if (
        now - new Date(session.last_seen_at).getTime() >
        parseDurationMs(process.env.SESSION_IDLE_TTL, '8h')
      ) {
        await authRepo.revokeSession(client, session.session_id);
        throw new AppError('SESSION_EXPIRED', 401, 'Sesi tidak aktif terlalu lama.');
      }

      if (session.status !== 'ACTIVE') {
        await authRepo.revokeSession(client, session.session_id);
        throw new AppError('SESSION_REVOKED', 401, 'Akun tidak aktif.');
      }

      await authRepo.touchSession(client, session.session_id);

      req.auth = {
        userId: session.user_id,
        sessionId: session.session_id,
        role: session.role,
        varkType: session.vark_type,
        mustChangePassword: session.must_change_password,
        loginId: session.login_id,
        fullName: session.full_name,
      };

      next();
    } finally {
      client.release();
    }
  } catch (error) {
    next(error);
  }
};

export function requireRole(...roles: UserRole[]): RequestHandler {
  return (req, _res, next) => {
    if (!req.auth) {
      next(new AppError('UNAUTHENTICATED', 401, 'Token akses diperlukan.'));
      return;
    }
    if (!roles.includes(req.auth.role)) {
      next(new AppError('FORBIDDEN', 403, 'Anda tidak berwenang untuk aksi ini.'));
      return;
    }
    next();
  };
}

export const requirePasswordChanged: RequestHandler = (req, _res, next) => {
  if (req.auth?.mustChangePassword) {
    next(
      new AppError(
        'PASSWORD_CHANGE_REQUIRED',
        403,
        'Ganti password terlebih dahulu sebelum melanjutkan.',
      ),
    );
    return;
  }
  next();
};

export const requireVark: RequestHandler = (req, _res, next) => {
  if (!req.auth) {
    next(new AppError('UNAUTHENTICATED', 401, 'Token akses diperlukan.'));
    return;
  }
  if (req.auth.role === 'STUDENT' && req.auth.varkType === null) {
    next(new AppError('VARK_REQUIRED', 428, 'Pilih VARK terlebih dahulu.'));
    return;
  }
  next();
};
