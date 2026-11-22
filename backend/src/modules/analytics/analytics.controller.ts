import type { Request, RequestHandler } from 'express';
import { unauthorized } from '../../lib/errors';
import type { AuthContext } from '../../types/auth';
import * as analyticsService from './analytics.service';
import { analyticsQuerySchema, analyticsUuidSchema } from './analytics.schema';

function auth(req: Request): AuthContext {
  if (!req.auth) {
    throw unauthorized('UNAUTHENTICATED', 'Token akses diperlukan.');
  }
  return req.auth;
}

export const getTopicAnalyticsHandler: RequestHandler = async (req, res, next) => {
  try {
    const context = auth(req);
    const topicId = analyticsUuidSchema.parse(req.params.id);
    const query = analyticsQuerySchema.parse(req.query);
    const result = await analyticsService.getTopicAnalytics(
      topicId,
      context.userId,
      query.category ?? null,
      query.studentId ?? null,
    );
    res.status(200).json({ data: result });
  } catch (error) {
    next(error);
  }
};
