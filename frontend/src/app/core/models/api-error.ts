import { HttpErrorResponse } from '@angular/common/http';

/**
 * Error JSON uniforme que devuelven los 3 microservicios
 * (backend/common → error.ApiError + GlobalExceptionHandler).
 */
export interface ApiError {
  timestamp: string;
  status: number;
  error: string;
  message: string;
  path: string;
}

/** Extrae el message del ApiError; si el cuerpo no lo trae, usa un mensaje genérico. */
export function mensajeApi(error: HttpErrorResponse, fallback = 'Ocurrió un error inesperado. Intenta de nuevo.'): string {
  const cuerpo: unknown = error.error;
  if (cuerpo && typeof cuerpo === 'object' && 'message' in cuerpo) {
    const mensaje = (cuerpo as Partial<ApiError>).message;
    if (typeof mensaje === 'string' && mensaje.trim().length > 0) {
      return mensaje;
    }
  }
  return fallback;
}
