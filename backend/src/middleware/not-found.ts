import type { RequestHandler } from 'express';

export const notFound: RequestHandler = (req, res) => {
  res.status(404).json({
    error: {
      code: 'ROUTE_NOT_FOUND',
      message: 'Endpoint tidak ditemukan.',
      details: [],
      requestId: req.requestId,
    },
  });
};
