import { describe, expect, it } from 'vitest';
import {
  generateRefreshToken,
  hashToken,
  signAccessToken,
  verifyAccessToken,
} from '../../src/lib/tokens';
import { AppError } from '../../src/lib/errors';

describe('tokens', () => {
  it('signs and verifies an access token', () => {
    const token = signAccessToken({
      userId: '11111111-1111-1111-1111-111111111111',
      sessionId: '22222222-2222-2222-2222-222222222222',
      role: 'STUDENT',
    });

    const payload = verifyAccessToken(token);
    expect(payload.userId).toBe('11111111-1111-1111-1111-111111111111');
    expect(payload.sessionId).toBe('22222222-2222-2222-2222-222222222222');
    expect(payload.role).toBe('STUDENT');
  });

  it('rejects a tampered access token', () => {
    const token = signAccessToken({
      userId: '11111111-1111-1111-1111-111111111111',
      sessionId: '22222222-2222-2222-2222-222222222222',
      role: 'STUDENT',
    });

    expect(() => verifyAccessToken(token + 'x')).toThrowError(AppError);
  });

  it('creates opaque refresh tokens and deterministic hashes', () => {
    const first = generateRefreshToken();
    const second = generateRefreshToken();

    expect(first).not.toBe(second);
    expect(hashToken(first)).toBe(hashToken(first));
    expect(hashToken(first)).not.toBe(hashToken(second));
  });
});
