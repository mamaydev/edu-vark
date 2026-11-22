import { AppError } from './errors';

const WINDOW_MS = 15 * 60 * 1000;
export const MAX_IDENTIFIER_FAILURES = 5;
export const MAX_IP_FAILURES = 20;

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

export function assertLoginAllowed(key: string, maxFailures: number): void {
  const entry = current(key);
  if (entry && entry.failures >= maxFailures) {
    throw new AppError(
      'TOO_MANY_ATTEMPTS',
      429,
      'Terlalu banyak percobaan login. Coba lagi dalam 15 menit.',
    );
  }
}

export function recordLoginFailure(key: string): void {
  const entry = current(key);
  if (!entry) {
    attempts.set(key, { failures: 1, resetAt: Date.now() + WINDOW_MS });
    return;
  }
  entry.failures += 1;
}

export function clearLoginFailures(key: string): void {
  attempts.delete(key);
}

export function resetLoginThrottle(): void {
  attempts.clear();
}
