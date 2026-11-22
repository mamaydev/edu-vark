export type UserRole = 'STUDENT' | 'LECTURER' | 'ADMIN';

export type VarkType = 'VISUAL' | 'AURAL' | 'READ_WRITE' | 'KINESTHETIC';

export interface AuthContext {
  userId: string;
  sessionId: string;
  role: UserRole;
  varkType: VarkType | null;
  mustChangePassword: boolean;
  loginId: string;
  fullName: string;
}

export const USER_ROLES: readonly UserRole[] = ['STUDENT', 'LECTURER', 'ADMIN'];
export const VARK_TYPES: readonly VarkType[] = [
  'VISUAL',
  'AURAL',
  'READ_WRITE',
  'KINESTHETIC',
];
