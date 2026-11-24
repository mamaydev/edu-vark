import cors from 'cors';
import express from 'express';
import helmet from 'helmet';
import pinoHttp from 'pino-http';
import { logger } from './lib/logger';
import { errorHandler } from './middleware/error-handler';
import { notFound } from './middleware/not-found';
import { requestId } from './middleware/request-id';
import { requireClientCompatibility } from './middleware/client-compatibility';
import { healthRouter } from './modules/health/health.routes';
import { authRouter } from './modules/auth/auth.routes';
import { adminRouter } from './modules/admin/admin.routes';
import {
  lecturerTopicsRouter,
  publicTopicsRouter,
} from './modules/topics/topics.routes';
import { feedbackRouter } from './modules/feedback/feedback.routes';
import { casesRouter, lecturerCasesRouter } from './modules/cases/cases.routes';
import {
  assessmentsRouter,
  lecturerAssessmentsRouter,
} from './modules/assessments/assessments.routes';
import { analyticsRouter } from './modules/analytics/analytics.routes';
import { materialFilesRouter } from './modules/materials/material-files.routes';
import { materialsRouter } from './modules/materials/materials.routes';
import { apiRouter } from './routes';
import { getEnv } from './config/env';
import { apiRateLimit } from './lib/rate-limit';

export function createApp(): express.Express {
  const env = getEnv();
  const app = express();

  app.disable('x-powered-by');
  // Dua hop tepercaya: Cloudflare Tunnel -> Traefik. Hanya alamat privat yang
  // dipercaya agar X-Forwarded-For dari internet tidak bisa dipalsukan.
  app.set('trust proxy', ['loopback', 'linklocal', 'uniquelocal']);
  app.use(requestId);
  app.use(pinoHttp({ logger }));
  app.use(helmet());

  const allowedOrigins = (env.CORS_ORIGINS ?? '')
    .split(',')
    .map((origin) => origin.trim())
    .filter(Boolean);
  // Aplikasi Android tidak membutuhkan CORS. Hanya origin yang eksplisit
  // didaftarkan lewat CORS_ORIGINS yang diizinkan.
  app.use(cors({ origin: allowedOrigins.length > 0 ? allowedOrigins : false }));
  app.use(express.json({ limit: '1mb' }));
  app.use('/api/v1', requireClientCompatibility);
  app.use('/api/v1', apiRateLimit);

  app.use(healthRouter);
  app.use('/api/v1', apiRouter);
  app.use('/api/v1', healthRouter);
  app.use('/api/v1/auth', authRouter);
  app.use('/api/v1/admin', adminRouter);
  app.use('/api/v1', publicTopicsRouter);
  app.use('/api/v1', feedbackRouter);
  app.use('/api/v1', casesRouter);
  app.use('/api/v1', assessmentsRouter);
  app.use('/api/v1', materialFilesRouter);
  app.use('/api/v1', materialsRouter);
  app.use('/api/v1/lecturer', lecturerTopicsRouter);
  app.use('/api/v1/lecturer', lecturerCasesRouter);
  app.use('/api/v1/lecturer', lecturerAssessmentsRouter);
  app.use('/api/v1/lecturer', analyticsRouter);

  app.use(notFound);
  app.use(errorHandler);

  return app;
}
