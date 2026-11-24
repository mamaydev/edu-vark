import { randomUUID } from 'node:crypto';
import { getPool } from '../../db/client';
import { AppError, forbidden, unauthorized, unprocessable } from '../../lib/errors';
import { parseDurationMs, parseDurationSeconds } from '../../lib/duration';
import { isValidLoginIdForRole } from '../../lib/login-id';
import { writeAudit } from '../../lib/audit';
import {
  assertLoginAllowed,
  clearLoginFailures,
  MAX_IDENTIFIER_FAILURES,
  MAX_IP_FAILURES,
  recordLoginFailure,
} from '../../lib/login-throttle';
import { assertStrongPassword, hashPassword, verifyPassword } from '../../lib/password';
import {
  generateRefreshToken,
  hashToken,
  signAccessToken,
} from '../../lib/tokens';
import type { UserRole, VarkType } from '../../types/auth';
import * as repo from './auth.repository';

function sessionAbsoluteMs(): number {
  return parseDurationMs(process.env.SESSION_ABSOLUTE_TTL, '12h');
}

function sessionIdleMs(): number {
  return parseDurationMs(process.env.SESSION_IDLE_TTL, '8h');
}

function accessTokenSeconds(): number {
  return parseDurationSeconds(process.env.ACCESS_TOKEN_TTL, '15m');
}

export interface UserResponse {
  id: string;
  loginId: string;
  fullName: string;
  role: UserRole;
  status: 'ACTIVE' | 'INACTIVE';
  mustChangePassword: boolean;
  activeVark: VarkType | null;
}

export interface LoginResponse {
  accessToken: string;
  refreshToken: string;
  expiresIn: number;
  user: UserResponse;
  varkRequired: boolean;
  activeVark: VarkType | null;
}

export interface TokenPairResponse {
  accessToken: string;
  refreshToken: string;
  expiresIn: number;
}

function toUserResponse(
  user: repo.UserRow,
  activeVark: VarkType | null,
): UserResponse {
  return {
    id: user.id,
    loginId: user.login_id,
    fullName: user.full_name,
    role: user.role,
    status: user.status,
    mustChangePassword: user.must_change_password,
    activeVark,
  };
}

export async function login(
  input: { loginId: string; password: string },
  ip: string,
): Promise<LoginResponse> {
  const identifierKey = 'id:' + input.loginId.trim().toLowerCase();
  const ipKey = 'ip:' + ip;
  assertLoginAllowed(identifierKey, MAX_IDENTIFIER_FAILURES);
  assertLoginAllowed(ipKey, MAX_IP_FAILURES);

  const client = await getPool().connect();
  try {
    const user = await repo.findUserByLoginId(client, input.loginId);
    const valid = user
      ? await verifyPassword(user.password_hash, input.password)
      : false;

    if (!user || !valid) {
      recordLoginFailure(identifierKey);
      recordLoginFailure(ipKey);
      throw unauthorized(
        'INVALID_CREDENTIALS',
        'NIM/NIP/NIDN atau password tidak valid.',
      );
    }

    // Every role signs in with a 10–11 digit ASCII identifier: students use
    // NIM, lecturers use NIDN, and admins use the system-issued 014014XXXXX
    // pattern. The legacy `admin` username (FR-036) is retired.
    if (!isValidLoginIdForRole(user.role, input.loginId)) {
      recordLoginFailure(identifierKey);
      recordLoginFailure(ipKey);
      throw unauthorized(
        'INVALID_CREDENTIALS',
        'NIM/NIP/NIDN atau password tidak valid.',
      );
    }

    if (user.status !== 'ACTIVE') {
      throw forbidden('ACCOUNT_INACTIVE', 'Akun tidak aktif.');
    }

    clearLoginFailures(identifierKey);
    clearLoginFailures(ipKey);

    const sessionId = randomUUID();
    const accessToken = signAccessToken({
      userId: user.id,
      sessionId,
      role: user.role,
    });
    const refreshToken = generateRefreshToken();
    const expiresAt = new Date(Date.now() + sessionAbsoluteMs());

    await client.query('BEGIN');
    try {
      await repo.revokeActiveSessions(client, user.id);
      await repo.createSession(client, {
        id: sessionId,
        userId: user.id,
        tokenHash: hashToken(accessToken),
        refreshTokenHash: hashToken(refreshToken),
        expiresAt,
      });
      await writeAudit(client, {
        actorId: user.id,
        action: 'LOGIN_SUCCEEDED',
        entity: 'USER',
        entityId: user.id,
        metadata: { ip },
      });
      await client.query('COMMIT');
    } catch (error) {
      await client.query('ROLLBACK');
      throw error;
    }

    return {
      accessToken,
      refreshToken,
      expiresIn: accessTokenSeconds(),
      user: toUserResponse(user, null),
      varkRequired: user.role === 'STUDENT',
      activeVark: null,
    };
  } finally {
    client.release();
  }
}

export async function refresh(refreshToken: string): Promise<TokenPairResponse> {
  const client = await getPool().connect();
  try {
    const session = await repo.findSessionByRefreshHash(
      client,
      hashToken(refreshToken),
    );

    if (!session || !session.is_active || session.revoked_at !== null) {
      throw unauthorized('REFRESH_INVALID', 'Refresh token tidak valid.');
    }

    if (session.status !== 'ACTIVE') {
      await repo.revokeSession(client, session.session_id);
      throw forbidden('ACCOUNT_INACTIVE', 'Akun tidak aktif.');
    }

    const now = Date.now();
    if (new Date(session.expires_at).getTime() <= now) {
      await repo.revokeSession(client, session.session_id);
      throw unauthorized('SESSION_EXPIRED', 'Sesi sudah berakhir. Silakan login ulang.');
    }

    if (now - new Date(session.last_seen_at).getTime() > sessionIdleMs()) {
      await repo.revokeSession(client, session.session_id);
      throw unauthorized('SESSION_EXPIRED', 'Sesi tidak aktif terlalu lama.');
    }

    const accessToken = signAccessToken({
      userId: session.user_id,
      sessionId: session.session_id,
      role: session.role,
    });
    const newRefreshToken = generateRefreshToken();

    await client.query('BEGIN');
    try {
      await repo.updateSessionTokens(
        client,
        session.session_id,
        hashToken(accessToken),
        hashToken(newRefreshToken),
      );
      await client.query('COMMIT');
    } catch (error) {
      await client.query('ROLLBACK');
      throw error;
    }

    return {
      accessToken,
      refreshToken: newRefreshToken,
      expiresIn: accessTokenSeconds(),
    };
  } finally {
    client.release();
  }
}

export async function logout(
  userId: string,
  sessionId: string,
): Promise<void> {
  const client = await getPool().connect();
  try {
    await client.query('BEGIN');
    try {
      await repo.revokeSession(client, sessionId);
      await writeAudit(client, {
        actorId: userId,
        action: 'LOGOUT',
        entity: 'AUTH_SESSION',
        entityId: sessionId,
        metadata: {},
      });
      await client.query('COMMIT');
    } catch (error) {
      await client.query('ROLLBACK');
      throw error;
    }
  } finally {
    client.release();
  }
}

export async function me(userId: string, sessionId: string): Promise<UserResponse> {
  const client = await getPool().connect();
  try {
    const user = await repo.findUserById(client, userId);
    const session = await repo.findSessionContext(client, sessionId);
    if (!user || !session) {
      throw unauthorized('SESSION_INVALID', 'Sesi tidak ditemukan.');
    }
    return toUserResponse(user, session.vark_type);
  } finally {
    client.release();
  }
}

export async function changePassword(
  userId: string,
  input: { currentPassword: string; newPassword: string; confirmPassword: string },
): Promise<void> {
  const client = await getPool().connect();
  try {
    const user = await repo.findUserById(client, userId);
    if (!user) {
      throw unauthorized('SESSION_INVALID', 'Pengguna tidak ditemukan.');
    }

    const valid = await verifyPassword(user.password_hash, input.currentPassword);
    if (!valid) {
      throw unprocessable('INVALID_PASSWORD', 'Password saat ini tidak sesuai.');
    }

    if (input.newPassword !== input.confirmPassword) {
      throw unprocessable(
        'PASSWORD_CONFIRMATION_MISMATCH',
        'Konfirmasi password baru tidak sama.',
      );
    }

    if (await verifyPassword(user.password_hash, input.newPassword)) {
      throw unprocessable(
        'PASSWORD_REUSED',
        'Password baru harus berbeda dari password saat ini.',
      );
    }

    assertStrongPassword(input.newPassword);

    const passwordHash = await hashPassword(input.newPassword);

    await client.query('BEGIN');
    try {
      await repo.updatePassword(client, userId, passwordHash);
      await repo.revokeAllSessions(client, userId);
      await writeAudit(client, {
        actorId: userId,
        action: 'PASSWORD_CHANGED',
        entity: 'USER',
        entityId: userId,
        metadata: {},
      });
      await client.query('COMMIT');
    } catch (error) {
      await client.query('ROLLBACK');
      throw error;
    }
  } finally {
    client.release();
  }
}

export async function selectVark(
  userId: string,
  sessionId: string,
  vark: VarkType,
): Promise<VarkType> {
  const client = await getPool().connect();
  try {
    const session = await repo.findSessionContext(client, sessionId);
    if (!session || session.user_id !== userId) {
      throw unauthorized('SESSION_INVALID', 'Sesi tidak ditemukan.');
    }
    if (session.role !== 'STUDENT') {
      throw forbidden('FORBIDDEN', 'Hanya mahasiswa yang memilih VARK.');
    }
    if (session.vark_type !== null) {
      throw new AppError('VARK_ALREADY_SET', 409, 'VARK sesi ini sudah dikunci.');
    }

    await client.query('BEGIN');
    try {
      const updated = await repo.setVark(client, sessionId, vark);
      if (!updated) {
        throw new AppError('VARK_ALREADY_SET', 409, 'VARK sesi ini sudah dikunci.');
      }
      await writeAudit(client, {
        actorId: userId,
        action: 'VARK_SELECTED',
        entity: 'AUTH_SESSION',
        entityId: sessionId,
        metadata: { vark },
      });
      await client.query('COMMIT');
    } catch (error) {
      await client.query('ROLLBACK');
      throw error;
    }

    return vark;
  } finally {
    client.release();
  }
}
