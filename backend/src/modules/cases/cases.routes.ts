import { Router } from 'express';
import {
  authenticate,
  requirePasswordChanged,
  requireRole,
  requireVark,
} from '../../middleware/auth';
import {
  getLecturerCaseHandler,
  getStudentCaseHandler,
  submitCaseHandler,
  upsertCaseHandler,
} from './cases.controller';

export const casesRouter = Router();

casesRouter.get(
  '/topics/:id/case',
  authenticate,
  requireRole('STUDENT'),
  requirePasswordChanged,
  requireVark,
  getStudentCaseHandler,
);

casesRouter.post(
  '/topics/:id/case/submission',
  authenticate,
  requireRole('STUDENT'),
  requirePasswordChanged,
  requireVark,
  submitCaseHandler,
);

export const lecturerCasesRouter = Router();

lecturerCasesRouter.use(authenticate, requireRole('LECTURER'), requirePasswordChanged);

lecturerCasesRouter.get('/topics/:id/case', getLecturerCaseHandler);
lecturerCasesRouter.put('/topics/:id/case', upsertCaseHandler);
