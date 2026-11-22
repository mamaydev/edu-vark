import type { Request, RequestHandler } from 'express';
import { unauthorized } from '../../lib/errors';
import type { AuthContext } from '../../types/auth';
import * as assessmentService from './assessments.service';
import { assessmentUuidSchema, createAssessmentSchema } from './assessments.schema';

function auth(req: Request): AuthContext {
  if (!req.auth) {
    throw unauthorized('UNAUTHENTICATED', 'Token akses diperlukan.');
  }
  return req.auth;
}

export const listCaseSubmissionsHandler: RequestHandler = async (req, res, next) => {
  try {
    const context = auth(req);
    const caseId = assessmentUuidSchema.parse(req.params.id);
    const result = await assessmentService.listCaseSubmissions(caseId, context.userId);
    res.status(200).json({ data: result });
  } catch (error) {
    next(error);
  }
};

export const getSubmissionHandler: RequestHandler = async (req, res, next) => {
  try {
    const context = auth(req);
    const submissionId = assessmentUuidSchema.parse(req.params.id);
    const result = await assessmentService.getSubmissionDetail(
      submissionId,
      context.userId,
      context.role,
    );
    res.status(200).json({ data: result });
  } catch (error) {
    next(error);
  }
};

export const createAssessmentHandler: RequestHandler = async (req, res, next) => {
  try {
    const context = auth(req);
    const submissionId = assessmentUuidSchema.parse(req.params.id);
    const input = createAssessmentSchema.parse(req.body);
    const result = await assessmentService.createAssessment(
      submissionId,
      context.userId,
      input.score,
      input.comment ?? null,
    );
    res.status(201).json({ data: result });
  } catch (error) {
    next(error);
  }
};
