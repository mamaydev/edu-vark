import type { Request, RequestHandler } from 'express';
import { AppError, unauthorized } from '../../lib/errors';
import type { AuthContext } from '../../types/auth';
import * as feedbackService from './feedback.service';
import { feedbackUuidSchema, saveFeedbackSchema } from './feedback.schema';

function auth(req: Request): AuthContext {
  if (!req.auth) {
    throw unauthorized('UNAUTHENTICATED', 'Token akses diperlukan.');
  }
  return req.auth;
}

function vark(context: AuthContext) {
  if (!context.varkType) {
    throw new AppError('VARK_REQUIRED', 428, 'Pilih VARK terlebih dahulu.');
  }
  return context.varkType;
}

export const getFeedbackHandler: RequestHandler = async (req, res, next) => {
  try {
    const context = auth(req);
    const topicId = feedbackUuidSchema.parse(req.params.id);
    const result = await feedbackService.getFeedback(
      context.userId,
      topicId,
      vark(context),
    );
    res.status(200).json({ data: result });
  } catch (error) {
    next(error);
  }
};

export const saveFeedbackHandler: RequestHandler = async (req, res, next) => {
  try {
    const context = auth(req);
    const topicId = feedbackUuidSchema.parse(req.params.id);
    const input = saveFeedbackSchema.parse(req.body);
    const result = await feedbackService.saveFeedback(
      context.userId,
      topicId,
      vark(context),
      input.value,
    );
    res.status(200).json({ data: result });
  } catch (error) {
    next(error);
  }
};
