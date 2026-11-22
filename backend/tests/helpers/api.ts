import request from 'supertest';
import type { Express } from 'express';

export async function loginAs(
  app: Express,
  loginId: string,
  password: string,
) {
  const response = await request(app)
    .post('/api/v1/auth/login')
    .send({ loginId, password })
    .expect(200);
  return response.body.data as {
    accessToken: string;
    refreshToken: string;
    user: { id: string; role: string };
    varkRequired: boolean;
  };
}

export function bearer(token: string): string {
  return 'Bearer ' + token;
}
