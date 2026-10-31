import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthService } from '../services/auth.service';

/**
 * Zona admin:
 * - sin sesión → /login (con returnUrl);
 * - con rol USER → /partidos (no ve el panel);
 * - con rol ADMIN → entra.
 */
export const adminGuard: CanActivateFn = (_ruta, estado) => {
  const auth = inject(AuthService);
  const router = inject(Router);

  if (!auth.estaLogueado()) {
    return router.createUrlTree(['/login'], { queryParams: { returnUrl: estado.url } });
  }
  if (!auth.esAdmin()) {
    return router.createUrlTree(['/partidos']);
  }
  return true;
};
