import { describe, expect, it } from 'vitest';
import { parseEnv } from '../../src/config/env';

describe('parseEnv', () => {
  it('applies safe defaults', () => {
    const env = parseEnv({
      NODE_ENV: 'test',
      DATABASE_URL: 'postgresql://localhost:5432/eduvark',
    });

    expect(env.PORT).toBe(3000);
    expect(env.MAX_UPLOAD_BYTES).toBe(104857600);
    expect(env.R2_SIGNED_URL_TTL_SECONDS).toBe(300);
  });

  it('coerces numeric values', () => {
    const env = parseEnv({
      NODE_ENV: 'test',
      DATABASE_URL: 'postgresql://localhost:5432/eduvark',
      PORT: '4100',
      MAX_UPLOAD_BYTES: '2048',
    });

    expect(env.PORT).toBe(4100);
    expect(env.MAX_UPLOAD_BYTES).toBe(2048);
  });

  it('rejects an upload limit above the database constraint', () => {
    expect(() =>
      parseEnv({
        NODE_ENV: 'test',
        DATABASE_URL: 'postgresql://localhost:5432/eduvark',
        MAX_UPLOAD_BYTES: '104857601',
      }),
    ).toThrow(/Invalid environment/);
  });

  it('rejects a missing database URL', () => {
    expect(() => parseEnv({ NODE_ENV: 'test' })).toThrow(/Invalid environment/);
  });

  it('treats empty optional values as undefined', () => {
    const env = parseEnv({
      NODE_ENV: 'test',
      DATABASE_URL: 'postgresql://localhost:5432/eduvark',
      R2_ENDPOINT: '',
      R2_BUCKET: '',
      JWT_ACCESS_SECRET: '',
    });

    expect(env.R2_ENDPOINT).toBeUndefined();
    expect(env.R2_BUCKET).toBeUndefined();
    expect(env.JWT_ACCESS_SECRET).toBeUndefined();
  });

  it('rejects an invalid JWT secret length', () => {
    expect(() =>
      parseEnv({
        NODE_ENV: 'test',
        DATABASE_URL: 'postgresql://localhost:5432/eduvark',
        JWT_ACCESS_SECRET: 'short',
      }),
    ).toThrow(/Invalid environment/);
  });
});

describe('parseEnv production guardrails', () => {
  const base = {
    NODE_ENV: 'production',
    DATABASE_URL: 'postgresql://eduvark:secret@postgres:5432/eduvark',
  };

  it('rejects production without JWT secrets', () => {
    expect(() => parseEnv(base)).toThrow(/Invalid environment/);
  });

  it('rejects identical access and refresh secrets', () => {
    expect(() =>
      parseEnv({
        ...base,
        JWT_ACCESS_SECRET: 'a'.repeat(40),
        JWT_REFRESH_SECRET: 'a'.repeat(40),
      }),
    ).toThrow(/Invalid environment/);
  });

  it('accepts production with distinct secrets and complete R2 config', () => {
    const env = parseEnv({
      ...base,
      JWT_ACCESS_SECRET: 'a'.repeat(40),
      JWT_REFRESH_SECRET: 'b'.repeat(40),
      R2_ENDPOINT: 'https://example.r2.cloudflarestorage.com',
      R2_ACCESS_KEY_ID: 'key',
      R2_SECRET_ACCESS_KEY: 'secret',
      R2_BUCKET: 'eduvark',
    });

    expect(env.NODE_ENV).toBe('production');
    expect(env.R2_BUCKET).toBe('eduvark');
  });

  it('rejects a partial R2 configuration', () => {
    expect(() =>
      parseEnv({
        ...base,
        JWT_ACCESS_SECRET: 'a'.repeat(40),
        JWT_REFRESH_SECRET: 'b'.repeat(40),
        R2_ENDPOINT: 'https://example.r2.cloudflarestorage.com',
      }),
    ).toThrow(/Invalid environment/);
  });
});
