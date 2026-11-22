import { createHash, randomBytes } from 'node:crypto';
import jwt from 'jsonwebtoken';
import { AppError } from './errors';
import { parseDurationSeconds } from './duration';
import { USER_ROLES, type UserRole } from '../types/auth';

const ISSUER = 'eduvark';
const AUDIENCE = 'eduvark-app';

function accessSecret(): string {
  const secret = process.env.JWT_ACCESS_SECRET;
  if (!secret) {
    throw new AppError(
      'AUTH_CONFIG_MISSING',
      500,
      'Konfigurasi JWT_ACCESS_SECRET belum diisi.',
    );
  }
  return secret;
}

export interface AccessTokenInput {
  userId: string;
  sessionId: string;
  role: UserRole;
}

export interface AccessTokenPayload {
  userId: string;
  sessionId: string;
  role: UserRole;
}

export function signAccessToken(input: AccessTokenInput): string {
  return jwt.sign(
    {
      sub: input.userId,
      sid: input.sessionId,
      role: input.role,
    },
    accessSecret(),
    {
      expiresIn: parseDurationSeconds(process.env.ACCESS_TOKEN_TTL, '15m'),
      issuer: ISSUER,
      audience: AUDIENCE,
    },
  );
}

export function verifyAccessToken(token: string): AccessTokenPayload {
  try {
    const payload = jwt.verify(token, accessSecret(), {
      issuer: ISSUER,
      audience: AUDIENCE,
    });

    if (typeof payload === 'string') {
      throw new Error('Unexpected JWT payload');
    }

    const { sub, sid, role } = payload;
    if (
      typeof sub !== 'string' ||
      typeof sid !== 'string' ||
      typeof role !== 'string' ||
      !USER_ROLES.includes(role as UserRole)
    ) {
      throw new Error('Missing JWT claims');
    }

    return {
      userId: sub,
      sessionId: sid,
      role: role as UserRole,
    };
  } catch (error) {
    if (error instanceof jwt.TokenExpiredError) {
      throw new AppError('TOKEN_EXPIRED', 401, 'Token akses sudah kedaluwarsa.');
    }
    throw new AppError('TOKEN_INVALID', 401, 'Token akses tidak valid.');
  }
}

export function generateRefreshToken(): string {
  return randomBytes(32).toString('base64url');
}

export function hashToken(token: string): string {
  return createHash('sha256').update(token).digest('hex');
}
