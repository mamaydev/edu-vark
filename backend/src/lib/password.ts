import argon2 from 'argon2';
import { unprocessable } from './errors';

export const MIN_PASSWORD_LENGTH = 6;

export function assertStrongPassword(password: string): void {
  if (password.length < MIN_PASSWORD_LENGTH) {
    throw unprocessable(
      'WEAK_PASSWORD',
      'Password minimal ' + MIN_PASSWORD_LENGTH + ' karakter.',
    );
  }
}

export async function hashPassword(password: string): Promise<string> {
  return argon2.hash(password, { type: argon2.argon2id });
}

export async function verifyPassword(hash: string, password: string): Promise<boolean> {
  try {
    return await argon2.verify(hash, password);
  } catch {
    return false;
  }
}
