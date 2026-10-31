import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { catchError, throwError } from 'rxjs';
import { AuthService } from '../services/auth.service';

/**
 * Interceptor JWT (funcional):
 * 1. Adjunta `Authorization: Bearer <token>` a toda petición a /api/ si hay sesión.
 * 2. ante un 401 (salvo el de la propia petición de login) cierra la sesión;
 *    `AuthService.logout()` limpia localStorage/signals y redirige a /login.
 */
export const jwtInterceptor: HttpInterceptorFn = (req, next) => {
  const auth = inject(AuthService);
  const token = auth.token();

  const esPeticionApi = req.url.startsWith('/api/');
  const esLogin = req.url.endsWith('/auth/login');

  const solicitud =
    token && esPeticionApi
      ? req.clone({ setHeaders: { Authorization: `Bearer ${token}` } })
      : req;

  return next(solicitud).pipe(
    catchError((error: unknown) => {
      if (error instanceof HttpErrorResponse && error.status === 401 && !esLogin) {
        auth.logout();
      }
      return throwError(() => error);
    }),
  );
};
