import { Router } from 'express';
import { getPool } from '../../db/client';
import { AppError } from '../../lib/errors';

export const healthRouter = Router();

healthRouter.get('/health', (_req, res) => {
  res.status(200).json({
    data: {
      status: 'ok',
      service: 'eduvark-backend',
      timestamp: new Date().toISOString(),
    },
  });
});

healthRouter.get('/health/ready', async (_req, res, next) => {
  try {
    await getPool().query('SELECT 1');
    res.status(200).json({
      data: {
        status: 'ready',
        service: 'eduvark-backend',
        timestamp: new Date().toISOString(),
      },
    });
  } catch {
    next(new AppError('DATABASE_UNAVAILABLE', 503, 'Database belum siap.'));
  }
});
