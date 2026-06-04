import { HttpErrorResponse } from '@angular/common/http';

/** Extrae mensaje legible del cuerpo de error del backend (ApiError o Problem Details). */
export function mensajeErrorHttp(err: HttpErrorResponse, fallback: string): string {
  const body = err.error;
  if (body == null) {
    return fallback;
  }
  if (typeof body === 'string') {
    return body.trim() || fallback;
  }
  if (typeof body.message === 'string' && body.message.trim()) {
    return body.message;
  }
  if (typeof body.detail === 'string' && body.detail.trim()) {
    return body.detail;
  }
  if (typeof body.error === 'string' && body.error.trim()) {
    return body.error;
  }
  return fallback;
}
