export class AppError extends Error {
  constructor(
    public readonly code: string,
    public readonly statusCode: number,
    message: string,
    public readonly details?: unknown,
  ) {
    super(message);
    this.name = 'AppError';
  }
}

export function badRequest(code: string, message: string, details?: unknown): AppError {
  return new AppError(code, 400, message, details);
}

export function unauthorized(code: string, message: string): AppError {
  return new AppError(code, 401, message);
}

export function forbidden(code: string, message: string): AppError {
  return new AppError(code, 403, message);
}

export function notFound(code: string, message: string): AppError {
  return new AppError(code, 404, message);
}

export function conflict(code: string, message: string): AppError {
  return new AppError(code, 409, message);
}

export function unprocessable(code: string, message: string, details?: unknown): AppError {
  return new AppError(code, 422, message, details);
}
