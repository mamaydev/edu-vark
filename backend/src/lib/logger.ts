import pino from 'pino';

/**
 * Request logging must never persist credentials. `pino-http` includes request
 * headers by default, so tokens have to be redacted explicitly.
 */
export const logger = pino({
  level: process.env.LOG_LEVEL ?? 'info',
  base: undefined,
  redact: {
    paths: [
      'req.headers.authorization',
      'req.headers.cookie',
      'res.headers["set-cookie"]',
      'req.query.token',
      'req.query.signature',
    ],
    censor: '[redacted]',
  },
});
