import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthService } from '../services/auth.service';

/** Sin sesión → /login (guardando la URL pendiente en returnUrl para volver tras ingresar). */
export const authGuard: CanActivateFn = (_ruta, estado) => {
  const auth = inject(AuthService);
  const router = inject(Router);

  if (auth.estaLogueado()) {
    return true;
  }
  return router.createUrlTree(['/login'], { queryParams: { returnUrl: estado.url } });
};
