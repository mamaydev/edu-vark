import { beforeEach, describe, expect, it } from 'vitest';
import {
  assertLoginAllowed,
  clearLoginFailures,
  MAX_IDENTIFIER_FAILURES,
  MAX_IP_FAILURES,
  recordLoginFailure,
  resetLoginThrottle,
} from '../../src/lib/login-throttle';
import { AppError } from '../../src/lib/errors';

function expectBlocked(fn: () => void): void {
  try {
    fn();
    expect.fail('Expected throttle to block');
  } catch (error) {
    expect(error).toBeInstanceOf(AppError);
    expect((error as AppError).code).toBe('TOO_MANY_ATTEMPTS');
  }
}

describe('login throttle', () => {
  beforeEach(() => {
    resetLoginThrottle();
  });

  it('blocks an identifier after five failures', () => {
    const key = 'id:2410511001';

    for (let i = 0; i < MAX_IDENTIFIER_FAILURES; i += 1) {
      assertLoginAllowed(key, MAX_IDENTIFIER_FAILURES);
      recordLoginFailure(key);
    }

    expectBlocked(() => assertLoginAllowed(key, MAX_IDENTIFIER_FAILURES));
  });

  it('keeps the per-IP budget independent from the identifier budget', () => {
    const ipKey = 'ip:203.0.113.7';
    const firstAccount = 'id:2410511001';
    const secondAccount = 'id:2410511002';

    for (let i = 0; i < MAX_IDENTIFIER_FAILURES; i += 1) {
      recordLoginFailure(firstAccount);
      recordLoginFailure(ipKey);
    }

    expectBlocked(() => assertLoginAllowed(firstAccount, MAX_IDENTIFIER_FAILURES));
    expect(() => assertLoginAllowed(secondAccount, MAX_IDENTIFIER_FAILURES)).not.toThrow();
    expect(() => assertLoginAllowed(ipKey, MAX_IP_FAILURES)).not.toThrow();
  });

  it('clears failures after a successful login', () => {
    recordLoginFailure('ip:127.0.0.1');
    clearLoginFailures('ip:127.0.0.1');
    expect(() => assertLoginAllowed('ip:127.0.0.1', MAX_IP_FAILURES)).not.toThrow();
  });
});
