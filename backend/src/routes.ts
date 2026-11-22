import { Router } from 'express';

export const apiRouter = Router();

apiRouter.get('/', (_req, res) => {
  res.status(200).json({
    data: {
      name: 'EduVARK API',
      version: 'v1',
      status: 'foundation',
    },
  });
});
