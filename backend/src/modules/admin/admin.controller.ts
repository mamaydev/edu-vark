import type { Request, RequestHandler } from 'express';
import { unauthorized } from '../../lib/errors';
import type { AuthContext } from '../../types/auth';
import * as adminService from './admin.service';
import {
  createUserSchema,
  leaveEnrollmentSchema,
  listUsersQuerySchema,
  resetPasswordSchema,
  updateUserSchema,
  uuidSchema,
} from './admin.schema';

function auth(req: Request): AuthContext {
  if (!req.auth) {
    throw unauthorized('UNAUTHENTICATED', 'Token akses diperlukan.');
  }
  return req.auth;
}

export const listUsersHandler: RequestHandler = async (req, res, next) => {
  try {
    const query = listUsersQuerySchema.parse(req.query);
    const result = await adminService.listUsers(query);
    res.status(200).json({
      data: result.items,
      meta: {
        page: result.page,
        pageSize: result.pageSize,
        total: result.total,
      },
    });
  } catch (error) {
    next(error);
  }
};

export const createUserHandler: RequestHandler = async (req, res, next) => {
  try {
    const context = auth(req);
    const input = createUserSchema.parse(req.body);
    const user = await adminService.createUser(input, context.userId);
    res.status(201).json({ data: user });
  } catch (error) {
    next(error);
  }
};

export const getUserHandler: RequestHandler = async (req, res, next) => {
  try {
    const id = uuidSchema.parse(req.params.id);
    const user = await adminService.getUser(id);
    res.status(200).json({ data: user });
  } catch (error) {
    next(error);
  }
};

export const updateUserHandler: RequestHandler = async (req, res, next) => {
  try {
    const context = auth(req);
    const id = uuidSchema.parse(req.params.id);
    const input = updateUserSchema.parse(req.body);
    const user = await adminService.updateUser(id, input, context.userId);
    res.status(200).json({ data: user });
  } catch (error) {
    next(error);
  }
};

export const resetPasswordHandler: RequestHandler = async (req, res, next) => {
  try {
    const context = auth(req);
    const id = uuidSchema.parse(req.params.id);
    const input = resetPasswordSchema.parse(req.body);
    await adminService.resetPassword(id, input.temporaryPassword, context.userId);
    res.status(204).send();
  } catch (error) {
    next(error);
  }
};

export const deactivateUserHandler: RequestHandler = async (req, res, next) => {
  try {
    const context = auth(req);
    const id = uuidSchema.parse(req.params.id);
    await adminService.deactivateUser(id, context.userId);
    res.status(204).send();
  } catch (error) {
    next(error);
  }
};

export const leaveEnrollmentHandler: RequestHandler = async (req, res, next) => {
  try {
    const context = auth(req);
    const id = uuidSchema.parse(req.params.id);
    const input = leaveEnrollmentSchema.parse(req.body);
    const result = await adminService.leaveEnrollment(id, input, context.userId);
    res.status(200).json({ data: result });
  } catch (error) {
    next(error);
  }
};
