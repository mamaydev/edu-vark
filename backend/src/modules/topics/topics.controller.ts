import type { Request, RequestHandler } from 'express';
import { AppError, unauthorized } from '../../lib/errors';
import type { AuthContext, VarkType } from '../../types/auth';
import * as topicsService from './topics.service';
import {
  createTopicSchema,
  fileMaterialFieldsSchema,
  linkMaterialSchema,
  listQuerySchema,
  reorderTopicsSchema,
  updateMaterialSchema,
  updateTopicSchema,
  uuidSchema,
} from './topics.schema';

function auth(req: Request): AuthContext {
  if (!req.auth) {
    throw unauthorized('UNAUTHENTICATED', 'Token akses diperlukan.');
  }
  return req.auth;
}

function studentVark(context: AuthContext): VarkType {
  if (!context.varkType) {
    throw new AppError('VARK_REQUIRED', 428, 'Pilih VARK terlebih dahulu.');
  }
  return context.varkType;
}

export const listTopicsHandler: RequestHandler = async (req, res, next) => {
  try {
    const context = auth(req);
    const query = listQuerySchema.parse(req.query);

    if (context.role === 'LECTURER') {
      const result = await topicsService.listTopicsForLecturer(
        context.userId,
        query.page,
        query.pageSize,
      );
      res.status(200).json({
        data: result.items,
        meta: { page: query.page, pageSize: query.pageSize, total: result.total },
      });
      return;
    }

    const result = await topicsService.listTopicsForStudent(
      context.userId,
      studentVark(context),
      query.page,
      query.pageSize,
    );
    res.status(200).json({
      data: result.items,
      meta: { page: query.page, pageSize: query.pageSize, total: result.total },
    });
  } catch (error) {
    next(error);
  }
};

export const getTopicHandler: RequestHandler = async (req, res, next) => {
  try {
    const context = auth(req);
    const topicId = uuidSchema.parse(req.params.id);

    const topic = context.role === 'LECTURER'
      ? await topicsService.getTopicForLecturer(topicId, context.userId)
      : await topicsService.getTopicForStudent(topicId, context.userId, studentVark(context));

    res.status(200).json({ data: topic });
  } catch (error) {
    next(error);
  }
};

export const getStudentDashboardHandler: RequestHandler = async (req, res, next) => {
  try {
    const context = auth(req);
    const dashboard = await topicsService.getStudentDashboard(
      context.userId,
      studentVark(context),
    );
    res.status(200).json({ data: dashboard });
  } catch (error) {
    next(error);
  }
};

export const listTopicMaterialsHandler: RequestHandler = async (req, res, next) => {
  try {
    const context = auth(req);
    const topicId = uuidSchema.parse(req.params.id);

    const materials = context.role === 'LECTURER'
      ? await topicsService.listMaterialsForLecturer(topicId, context.userId)
      : await topicsService.listMaterialsForStudent(topicId, context.userId, studentVark(context));

    res.status(200).json({ data: materials });
  } catch (error) {
    next(error);
  }
};

export const createTopicHandler: RequestHandler = async (req, res, next) => {
  try {
    const context = auth(req);
    const input = createTopicSchema.parse(req.body);
    const topic = await topicsService.createTopic(context.userId, input);
    res.status(201).json({ data: topic });
  } catch (error) {
    next(error);
  }
};

export const updateTopicHandler: RequestHandler = async (req, res, next) => {
  try {
    const context = auth(req);
    const topicId = uuidSchema.parse(req.params.id);
    const input = updateTopicSchema.parse(req.body);
    const topic = await topicsService.updateTopic(topicId, context.userId, input);
    res.status(200).json({ data: topic });
  } catch (error) {
    next(error);
  }
};

export const publishTopicHandler: RequestHandler = async (req, res, next) => {
  try {
    const context = auth(req);
    const topicId = uuidSchema.parse(req.params.id);
    const topic = await topicsService.setTopicPublication(topicId, context.userId, 'PUBLISHED');
    res.status(200).json({ data: topic });
  } catch (error) {
    next(error);
  }
};

export const unpublishTopicHandler: RequestHandler = async (req, res, next) => {
  try {
    const context = auth(req);
    const topicId = uuidSchema.parse(req.params.id);
    const topic = await topicsService.setTopicPublication(topicId, context.userId, 'DRAFT');
    res.status(200).json({ data: topic });
  } catch (error) {
    next(error);
  }
};

export const deleteTopicHandler: RequestHandler = async (req, res, next) => {
  try {
    const context = auth(req);
    const topicId = uuidSchema.parse(req.params.id);
    await topicsService.deleteTopic(topicId, context.userId);
    res.status(204).send();
  } catch (error) {
    next(error);
  }
};

export const reorderTopicsHandler: RequestHandler = async (req, res, next) => {
  try {
    const context = auth(req);
    const input = reorderTopicsSchema.parse(req.body);
    await topicsService.reorderTopics(context.userId, input.topicIds);
    res.status(200).json({ data: { reordered: true } });
  } catch (error) {
    next(error);
  }
};

export const createFileMaterialHandler: RequestHandler = async (req, res, next) => {
  try {
    const context = auth(req);
    const topicId = uuidSchema.parse(req.params.id);
    if (!req.file) {
      res.status(422).json({
        error: {
          code: 'FILE_REQUIRED',
          message: 'File materi wajib diunggah.',
          details: [],
          requestId: req.requestId,
        },
      });
      return;
    }

    const fields = fileMaterialFieldsSchema.parse(req.body);
    const material = await topicsService.createFileMaterial(
      topicId,
      context.userId,
      {
        buffer: req.file.buffer,
        mimetype: req.file.mimetype,
        size: req.file.size,
        originalname: req.file.originalname,
      },
      { title: fields.title, vark: fields.vark as VarkType },
    );

    res.status(201).json({ data: material });
  } catch (error) {
    next(error);
  }
};

export const createLinkMaterialHandler: RequestHandler = async (req, res, next) => {
  try {
    const context = auth(req);
    const topicId = uuidSchema.parse(req.params.id);
    const input = linkMaterialSchema.parse(req.body);
    const material = await topicsService.createLinkMaterial(topicId, context.userId, {
      title: input.title,
      vark: input.vark as VarkType,
      url: input.url,
    });
    res.status(201).json({ data: material });
  } catch (error) {
    next(error);
  }
};

export const updateMaterialHandler: RequestHandler = async (req, res, next) => {
  try {
    const context = auth(req);
    const materialId = uuidSchema.parse(req.params.id);
    const input = updateMaterialSchema.parse(req.body);
    const material = await topicsService.updateMaterial(materialId, context.userId, {
      title: input.title,
      vark: input.vark as VarkType | undefined,
    });
    res.status(200).json({ data: material });
  } catch (error) {
    next(error);
  }
};

export const deleteMaterialHandler: RequestHandler = async (req, res, next) => {
  try {
    const context = auth(req);
    const materialId = uuidSchema.parse(req.params.id);
    const result = await topicsService.deleteMaterial(materialId, context.userId);
    res.status(200).json({ data: result });
  } catch (error) {
    next(error);
  }
};

export const replaceMaterialFileHandler: RequestHandler = async (req, res, next) => {
  try {
    const context = auth(req);
    const materialId = uuidSchema.parse(req.params.id);
    if (!req.file) {
      res.status(422).json({
        error: {
          code: 'FILE_REQUIRED',
          message: 'File pengganti wajib diunggah.',
          details: [],
          requestId: req.requestId,
        },
      });
      return;
    }

    const result = await topicsService.replaceMaterialFile(
      materialId,
      context.userId,
      {
        buffer: req.file.buffer,
        mimetype: req.file.mimetype,
        size: req.file.size,
        originalname: req.file.originalname,
      },
    );

    res.status(200).json({ data: result });
  } catch (error) {
    next(error);
  }
};
