import { Router } from 'express';
import {
  authenticate,
  requirePasswordChanged,
  requireRole,
} from '../../middleware/auth';
import { getTopicAnalyticsHandler } from './analytics.controller';

export const analyticsRouter = Router();

analyticsRouter.use(authenticate, requireRole('LECTURER'), requirePasswordChanged);

analyticsRouter.get('/analytics/topics/:id', getTopicAnalyticsHandler);
