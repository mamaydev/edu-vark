import { AppError } from './errors';

const WINDOW_MS = 15 * 60 * 1000;
export const MAX_PASSWORD_CHANGE_FAILURES = 5;

interface Attempt {
  failures: number;
  resetAt: number;
}

const attempts = new Map<string, Attempt>();

function current(key: string): Attempt | undefined {
  const entry = attempts.get(key);
  if (!entry) return undefined;
  if (Date.now() >= entry.resetAt) {
    attempts.delete(key);
    return undefined;
  }
  return entry;
}

export function assertPasswordChangeAllowed(userId: string): void {
  const entry = current(userId);
  if (entry && entry.failures >= MAX_PASSWORD_CHANGE_FAILURES) {
    throw new AppError(
      'TOO_MANY_ATTEMPTS',
      429,
      'Terlalu banyak percobaan ganti password. Coba lagi dalam 15 menit.',
    );
  }
}

export function recordPasswordChangeFailure(userId: string): void {
  const entry = current(userId);
  if (!entry) {
    attempts.set(userId, { failures: 1, resetAt: Date.now() + WINDOW_MS });
    return;
  }
  entry.failures += 1;
}

export function clearPasswordChangeFailures(userId: string): void {
  attempts.delete(userId);
}

export function resetPasswordChangeThrottle(): void {
  attempts.clear();
}
