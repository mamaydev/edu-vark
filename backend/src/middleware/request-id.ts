import { randomUUID } from 'node:crypto';
import type { RequestHandler } from 'express';

const VALID_REQUEST_ID = /^[A-Za-z0-9._-]{1,64}$/;

export const requestId: RequestHandler = (req, res, next) => {
  const incoming = req.header('x-request-id')?.trim();
  const id = incoming && VALID_REQUEST_ID.test(incoming) ? incoming : randomUUID();
  req.requestId = id;
  res.setHeader('X-Request-Id', id);
  next();
};
