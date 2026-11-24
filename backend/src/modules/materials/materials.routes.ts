import { Router } from 'express';
import { authenticate, requirePasswordChanged, requireRole } from '../../middleware/auth';
import {
  downloadMaterialContentHandler,
  getMaterialLinkTargetHandler,
} from './materials.controller';

export const materialsRouter = Router();

/**
 * Alamat unduh tetap untuk berkas terkelola (FR-015/DEC-001). Setiap
 * permintaan baru melewati autentikasi sesi + otorisasi akademik penuh:
 * tidak ada masa berlaku alamat dan tidak ada URL R2 yang diberikan ke klien.
 */
materialsRouter.get(
  '/materials/:id/content',
  authenticate,
  requireRole('STUDENT', 'LECTURER'),
  requirePasswordChanged,
  downloadMaterialContentHandler,
);

/**
 * Pembukaan tautan HTTPS eksternal melalui EduVARK: izin diperiksa pada
 * permintaan ini, lalu aplikasi membuka URL tujuan tanpa kredensial EduVARK.
 */
materialsRouter.get(
  '/materials/:id/link-target',
  authenticate,
  requireRole('STUDENT', 'LECTURER'),
  requirePasswordChanged,
  getMaterialLinkTargetHandler,
);
