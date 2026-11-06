import { HttpErrorResponse } from '@angular/common/http';

/** El mensaje que la API manda en el ProblemDetail, o uno genérico. */
export function apiError(error: unknown, fallback = 'No se pudo completar la acción.'): string {
  return (error instanceof HttpErrorResponse && typeof error.error?.detail === 'string')
    ? error.error.detail
    : fallback;
}
