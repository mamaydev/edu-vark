import { Router } from 'express';
import {
  authenticate,
  requirePasswordChanged,
} from '../../middleware/auth';
import {
  changePasswordHandler,
  loginHandler,
  logoutHandler,
  meHandler,
  refreshHandler,
  varkHandler,
} from './auth.controller';

export const authRouter = Router();

authRouter.post('/login', loginHandler);
authRouter.post('/refresh', refreshHandler);
authRouter.post('/logout', authenticate, logoutHandler);
authRouter.get('/me', authenticate, meHandler);
authRouter.post('/change-password', authenticate, changePasswordHandler);
authRouter.post('/vark', authenticate, requirePasswordChanged, varkHandler);
