import type { Request, RequestHandler } from 'express';
import { AppError, unauthorized } from '../../lib/errors';
import type { AuthContext } from '../../types/auth';
import * as caseService from './cases.service';
import { caseUuidSchema, submitCaseSchema, upsertCaseSchema } from './cases.schema';

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

export const getStudentCaseHandler: RequestHandler = async (req, res, next) => {
  try {
    const context = auth(req);
    const topicId = caseUuidSchema.parse(req.params.id);
    const result = await caseService.getCaseForStudent(
      topicId,
      context.userId,
      vark(context),
    );
    res.status(200).json({ data: result });
  } catch (error) {
    next(error);
  }
};

export const submitCaseHandler: RequestHandler = async (req, res, next) => {
  try {
    const context = auth(req);
    const topicId = caseUuidSchema.parse(req.params.id);
    const input = submitCaseSchema.parse(req.body);
    const result = await caseService.submitCase(
      topicId,
      context.userId,
      vark(context),
      input.answerText,
    );
    res.status(201).json({ data: result });
  } catch (error) {
    next(error);
  }
};

export const getLecturerCaseHandler: RequestHandler = async (req, res, next) => {
  try {
    const context = auth(req);
    const topicId = caseUuidSchema.parse(req.params.id);
    const result = await caseService.getCaseForLecturer(topicId, context.userId);
    res.status(200).json({ data: result });
  } catch (error) {
    next(error);
  }
};

export const upsertCaseHandler: RequestHandler = async (req, res, next) => {
  try {
    const context = auth(req);
    const topicId = caseUuidSchema.parse(req.params.id);
    const input = upsertCaseSchema.parse(req.body);
    const result = await caseService.upsertCase(topicId, context.userId, input);
    res.status(200).json({ data: result });
  } catch (error) {
    next(error);
  }
};
