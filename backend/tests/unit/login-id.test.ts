import { describe, expect, it } from 'vitest';
import {
  allocateAdminLoginId,
  ADMIN_LOGIN_ID_PATTERN,
  ADMIN_LOGIN_ID_PREFIX,
  adminLoginIdSequence,
  formatAdminLoginId,
  isAcademicLoginId,
  isAdminLoginId,
  isLegacyAdminLoginId,
  isValidLoginIdForRole,
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

  it('recognizes only the strict 014014 + five digit Admin pattern', () => {
    expect(isAdminLoginId('01401400001')).toBe(true);
    expect(isAdminLoginId(' 01401499999 ')).toBe(true);
    expect(isAdminLoginId('0140140000')).toBe(false);
    expect(isAdminLoginId('014014000001')).toBe(false);
    expect(isAdminLoginId('01401500001')).toBe(false);
    expect(isAdminLoginId('admin')).toBe(false);
    expect(isAdminLoginId('01401a00001')).toBe(false);
    expect(ADMIN_LOGIN_ID_PATTERN.test(ADMIN_LOGIN_ID_PREFIX + '00002')).toBe(true);
  });

  it('treats 01401400000 as outside the valid 00001–99999 range', () => {
    expect(isAdminLoginId('01401400000')).toBe(false);
    expect(isAdminLoginId('01401400001')).toBe(true);
    expect(isAdminLoginId('01401499999')).toBe(true);
    expect(isAdminLoginId('014014100000')).toBe(false);
    expect(adminLoginIdSequence('01401400000')).toBeNull();
    expect(isValidLoginIdForRole('ADMIN', '01401400000')).toBe(false);
    expect(isValidLoginIdForRole('ADMIN', '01401400001')).toBe(true);
    expect(isValidLoginIdForRole('ADMIN', '01401499999')).toBe(true);
  });

  it('validates identifiers per role', () => {
    expect(isValidLoginIdForRole('STUDENT', '2025000001')).toBe(true);
    expect(isValidLoginIdForRole('LECTURER', '1990010001')).toBe(true);
    expect(isValidLoginIdForRole('ADMIN', '01401400001')).toBe(true);
    expect(isValidLoginIdForRole('ADMIN', 'admin')).toBe(false);
    expect(isValidLoginIdForRole('ADMIN', '2025000001')).toBe(false);
    expect(isValidLoginIdForRole('STUDENT', '01401400001')).toBe(true);
  });

  it('flags the retired legacy admin username explicitly', () => {
    expect(isLegacyAdminLoginId('admin')).toBe(true);
    expect(isLegacyAdminLoginId(' ADMIN ')).toBe(true);
    expect(isLegacyAdminLoginId('admin2')).toBe(false);
  });

  it('formats and parses Admin sequences with zero padding', () => {
    expect(formatAdminLoginId(1)).toBe('01401400001');
    expect(formatAdminLoginId(99999)).toBe('01401499999');
    expect(adminLoginIdSequence('01401400042')).toBe(42);
    expect(adminLoginIdSequence('2025000001')).toBeNull();
  });

  it('rejects out-of-range Admin sequences', () => {
    expect(() => formatAdminLoginId(0)).toThrow(/00001/);
    expect(() => formatAdminLoginId(100000)).toThrow(/99999/);
  });

  it('allocation needs a transaction-capable client', () => {
    // Allocation itself is exercised against PostgreSQL in the integration
    // suite; here we only guard the exported contract shape.
    expect(typeof allocateAdminLoginId).toBe('function');
  });
});
