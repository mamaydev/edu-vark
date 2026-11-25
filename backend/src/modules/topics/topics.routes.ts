import { Router } from 'express';
import multer from 'multer';
import {
  authenticate,
  requirePasswordChanged,
  requireRole,
  requireVark,
} from '../../middleware/auth';
import {
  createFileMaterialHandler,
  createLinkMaterialHandler,
  createTopicHandler,
  deleteMaterialHandler,
  deleteTopicHandler,
  getStudentDashboardHandler,
  getTopicHandler,
  listTopicMaterialsHandler,
  listTopicsHandler,
  publishTopicHandler,
  reorderTopicsHandler,
  replaceMaterialFileHandler,
  unpublishTopicHandler,
  updateMaterialHandler,
  updateTopicHandler,
} from './topics.controller';

const maxUploadBytes = Number(process.env.MAX_UPLOAD_BYTES ?? 104857600) || 104857600;

const upload = multer({
  storage: multer.memoryStorage(),
  limits: { fileSize: maxUploadBytes, files: 1, fields: 2, parts: 4 },
});

export const publicTopicsRouter = Router();

publicTopicsRouter.get(
  '/student/dashboard',
  authenticate,
  requireRole('STUDENT'),
  requirePasswordChanged,
  requireVark,
  getStudentDashboardHandler,
);

publicTopicsRouter.get(
  '/topics',
  authenticate,
  requireRole('STUDENT', 'LECTURER'),
  requirePasswordChanged,
  requireVark,
  listTopicsHandler,
);

publicTopicsRouter.get(
  '/topics/:id',
  authenticate,
  requireRole('STUDENT', 'LECTURER'),
  requirePasswordChanged,
  requireVark,
  getTopicHandler,
);

publicTopicsRouter.get(
  '/topics/:id/materials',
  authenticate,
  requireRole('STUDENT', 'LECTURER'),
  requirePasswordChanged,
  requireVark,
  listTopicMaterialsHandler,
);

export const lecturerTopicsRouter = Router();

lecturerTopicsRouter.use(authenticate, requireRole('LECTURER'), requirePasswordChanged);

lecturerTopicsRouter.get('/topics', listTopicsHandler);
lecturerTopicsRouter.post('/topics', createTopicHandler);
lecturerTopicsRouter.patch('/topics/reorder', reorderTopicsHandler);
lecturerTopicsRouter.get('/topics/:id', getTopicHandler);
lecturerTopicsRouter.patch('/topics/:id', updateTopicHandler);
lecturerTopicsRouter.delete('/topics/:id', deleteTopicHandler);
lecturerTopicsRouter.post('/topics/:id/publish', publishTopicHandler);
lecturerTopicsRouter.post('/topics/:id/unpublish', unpublishTopicHandler);
lecturerTopicsRouter.get('/topics/:id/materials', listTopicMaterialsHandler);
lecturerTopicsRouter.post(
  '/topics/:id/materials/file',
  upload.single('file'),
  createFileMaterialHandler,
);
lecturerTopicsRouter.post('/topics/:id/materials/link', createLinkMaterialHandler);
lecturerTopicsRouter.patch('/materials/:id', updateMaterialHandler);
lecturerTopicsRouter.post(
  '/materials/:id/file',
  upload.single('file'),
  replaceMaterialFileHandler,
);
lecturerTopicsRouter.delete('/materials/:id', deleteMaterialHandler);
