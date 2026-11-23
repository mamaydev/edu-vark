import type { Request, RequestHandler } from 'express';
import { unauthorized } from '../../lib/errors';
import {
  assertPasswordChangeAllowed,
  clearPasswordChangeFailures,
  recordPasswordChangeFailure,
} from '../../lib/password-change-throttle';
import type { AuthContext, VarkType } from '../../types/auth';
import * as authService from './auth.service';
import {
  changePasswordSchema,
  loginSchema,
  refreshSchema,
  varkSchema,
} from './auth.schema';

function auth(req: Request): AuthContext {
  if (!req.auth) {
    throw unauthorized('UNAUTHENTICATED', 'Token akses diperlukan.');
  }
  return req.auth;
}

export const loginHandler: RequestHandler = async (req, res, next) => {
  try {
    const input = loginSchema.parse(req.body);
    const result = await authService.login(input, req.ip ?? 'unknown');
    res.status(200).json({ data: result });
  } catch (error) {
    next(error);
  }
};

export const refreshHandler: RequestHandler = async (req, res, next) => {
  try {
    const input = refreshSchema.parse(req.body);
    const result = await authService.refresh(input.refreshToken);
    res.status(200).json({ data: result });
  } catch (error) {
    next(error);
  }
};

export const logoutHandler: RequestHandler = async (req, res, next) => {
  try {
    const context = auth(req);
    await authService.logout(context.userId, context.sessionId);
    res.status(204).send();
  } catch (error) {
    next(error);
  }
};

export const meHandler: RequestHandler = async (req, res, next) => {
  try {
    const context = auth(req);
    const user = await authService.me(context.userId, context.sessionId);
    res.status(200).json({ data: user });
  } catch (error) {
    next(error);
  }
};

export const changePasswordHandler: RequestHandler = async (req, res, next) => {
  try {
    const context = auth(req);
    assertPasswordChangeAllowed(context.userId);
    const input = changePasswordSchema.parse(req.body);
    try {
      await authService.changePassword(context.userId, input);
    } catch (error) {
      if (error instanceof Error && 'code' in error && error.code === 'INVALID_PASSWORD') {
        recordPasswordChangeFailure(context.userId);
      }
      throw error;
    }
    clearPasswordChangeFailures(context.userId);
    res.status(204).send();
  } catch (error) {
    next(error);
  }
};

export const varkHandler: RequestHandler = async (req, res, next) => {
  try {
    const context = auth(req);
    const input = varkSchema.parse(req.body);
    const vark = await authService.selectVark(
      context.userId,
      context.sessionId,
      input.vark as VarkType,
    );
    res.status(200).json({ data: { activeVark: vark, locked: true } });
  } catch (error) {
    next(error);
  }
};
