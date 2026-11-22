import { Router } from 'express';
import {
  authenticate,
  requirePasswordChanged,
  requireRole,
} from '../../middleware/auth';
import {
  createUserHandler,
  deactivateUserHandler,
  getUserHandler,
  leaveEnrollmentHandler,
  listUsersHandler,
  resetPasswordHandler,
  updateUserHandler,
} from './admin.controller';

export const adminRouter = Router();

adminRouter.use(authenticate, requireRole('ADMIN'), requirePasswordChanged);

adminRouter.get('/users', listUsersHandler);
adminRouter.post('/users', createUserHandler);
adminRouter.get('/users/:id', getUserHandler);
adminRouter.patch('/users/:id', updateUserHandler);
adminRouter.post('/users/:id/reset-password', resetPasswordHandler);
adminRouter.post('/users/:id/deactivate', deactivateUserHandler);
adminRouter.patch('/enrollments/:id', leaveEnrollmentHandler);
