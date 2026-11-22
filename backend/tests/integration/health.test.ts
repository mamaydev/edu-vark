import request from 'supertest';
import { describe, expect, it } from 'vitest';
import { createApp } from '../../src/app';

const app = createApp();

describe('health endpoints', () => {
  it('returns liveness at /health', async () => {
    const response = await request(app).get('/health').expect(200);

    expect(response.body).toMatchObject({
      data: {
        status: 'ok',
        service: 'eduvark-backend',
      },
    });
    expect(response.body.data.timestamp).toEqual(expect.any(String));
  });

  it('returns liveness under /api/v1/health', async () => {
    const response = await request(app).get('/api/v1/health').expect(200);

    expect(response.body.data.status).toBe('ok');
    expect(response.headers['x-request-id']).toBeTruthy();
  });

  it('returns a consistent 404 envelope', async () => {
    const response = await request(app).get('/tidak-ada').expect(404);

    expect(response.body.error.code).toBe('ROUTE_NOT_FOUND');
    expect(response.body.error.requestId).toBeTruthy();
  });
});
