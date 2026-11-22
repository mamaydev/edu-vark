process.env.NODE_ENV = 'test';
process.env.LOG_LEVEL = process.env.LOG_LEVEL ?? 'silent';
process.env.MAX_UPLOAD_BYTES = process.env.MAX_UPLOAD_BYTES ?? '1048576';
process.env.JWT_ACCESS_SECRET =
  process.env.JWT_ACCESS_SECRET ?? 'test-access-secret-at-least-32-characters';
