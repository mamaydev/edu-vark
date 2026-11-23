/**
 * Academic login identifiers are UMY student NIMs (10–11 digits) and
 * lecturer NIP/NIDN numbers (10 digits). Admin accounts are the only
 * exception and may use a username such as "admin".
 */
export const ACADEMIC_LOGIN_ID_PATTERN = /^[0-9]{10,11}$/;

export const ACADEMIC_LOGIN_ID_MESSAGE =
  'NIM/NIP/NIDN harus berupa 10–11 digit angka.';

export const DEFAULT_ADMIN_LOGIN_ID = 'admin';

export function isAcademicLoginId(value: string): boolean {
  return ACADEMIC_LOGIN_ID_PATTERN.test(value.trim());
}

export function isDefaultAdminLoginId(value: string): boolean {
  return value.trim().toLowerCase() === DEFAULT_ADMIN_LOGIN_ID;
}
