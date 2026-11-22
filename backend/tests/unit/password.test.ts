import { describe, expect, it } from 'vitest';
import {
  assertStrongPassword,
  hashPassword,
  verifyPassword,
} from '../../src/lib/password';
import { AppError } from '../../src/lib/errors';

describe('password policy', () => {
  it('rejects passwords shorter than 6 characters', () => {
    expect(() => assertStrongPassword('12345')).toThrowError(AppError);
  });

  it('accepts exactly 6 characters without composition rules', () => {
    expect(() => assertStrongPassword('aaaaaa')).not.toThrow();
    expect(() => assertStrongPassword('password')).not.toThrow();
    expect(() => assertStrongPassword('123456')).not.toThrow();
  });

  it('accepts a 6-character password and verifies its hash', async () => {
    const password = 'abc123';
    assertStrongPassword(password);

    const hash = await hashPassword(password);
    await expect(verifyPassword(hash, password)).resolves.toBe(true);
    await expect(verifyPassword(hash, 'wrong1')).resolves.toBe(false);
  });
});
