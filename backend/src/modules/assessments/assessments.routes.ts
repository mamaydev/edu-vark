import { Router } from 'express';
import {
  authenticate,
  requirePasswordChanged,
  requireRole,
} from '../../middleware/auth';
import {
  createAssessmentHandler,
  getSubmissionHandler,
  listCaseSubmissionsHandler,
} from './assessments.controller';

export const assessmentsRouter = Router();

assessmentsRouter.get(
  '/submissions/:id',
  authenticate,
  requireRole('STUDENT', 'LECTURER'),
  requirePasswordChanged,
  getSubmissionHandler,
);

assessmentsRouter.post(
  '/submissions/:id/assessment',
  authenticate,
  requireRole('LECTURER'),
  requirePasswordChanged,
  createAssessmentHandler,
);

export const lecturerAssessmentsRouter = Router();

lecturerAssessmentsRouter.use(
  authenticate,
  requireRole('LECTURER'),
  requirePasswordChanged,
);

lecturerAssessmentsRouter.get(
  '/cases/:id/submissions',
  listCaseSubmissionsHandler,
);
