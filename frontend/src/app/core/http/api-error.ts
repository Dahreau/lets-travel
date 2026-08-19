import { HttpErrorResponse } from '@angular/common/http';

interface ApiErrorBody {
  message?: string;
}

/** Backend error bodies are always `{timestamp, status, error, message}` (see ApiExceptionHandler in each service). */
export function extractErrorMessage(error: unknown, fallback = 'Une erreur est survenue'): string {
  if (error instanceof HttpErrorResponse) {
    const body = error.error as ApiErrorBody | null;
    if (body?.message) {
      return body.message;
    }
    if (error.status === 0) {
      return 'Service injoignable';
    }
  }
  return fallback;
}
