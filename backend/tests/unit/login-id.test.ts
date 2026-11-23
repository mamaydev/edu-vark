import { describe, expect, it } from 'vitest';
import {
  isAcademicLoginId,
  isDefaultAdminLoginId,
} from '../../src/lib/login-id';

describe('login identifier rules', () => {
  it('accepts 10 and 11 digit academic identifiers', () => {
    expect(isAcademicLoginId('2025000001')).toBe(true);
    expect(isAcademicLoginId('20250000011')).toBe(true);
    expect(isAcademicLoginId(' 1990010001 ')).toBe(true);
  });

  it('rejects shorter, longer, and non-numeric identifiers', () => {
    expect(isAcademicLoginId('202500001')).toBe(false);
    expect(isAcademicLoginId('202500000112')).toBe(false);
    expect(isAcademicLoginId('student1')).toBe(false);
    expect(isAcademicLoginId('')).toBe(false);
  });

  it('recognizes the default admin username', () => {
    expect(isDefaultAdminLoginId('admin')).toBe(true);
    expect(isDefaultAdminLoginId(' ADMIN ')).toBe(true);
    expect(isDefaultAdminLoginId('admin2')).toBe(false);
  });
});
