import { Router } from 'express';
import {
  authenticate,
  requirePasswordChanged,
  requireRole,
  requireVark,
} from '../../middleware/auth';
import { getFeedbackHandler, saveFeedbackHandler } from './feedback.controller';

export const feedbackRouter = Router();

feedbackRouter.get(
  '/topics/:id/feedback',
  authenticate,
  requireRole('STUDENT'),
  requirePasswordChanged,
  requireVark,
  getFeedbackHandler,
);

feedbackRouter.put(
  '/topics/:id/feedback',
  authenticate,
  requireRole('STUDENT'),
  requirePasswordChanged,
  requireVark,
  saveFeedbackHandler,
);
