import { Routes } from '@angular/router';
import { adminGuard } from './core/guards/admin.guard';
import { authGuard } from './core/guards/auth.guard';

/**
 * Router standalone. Las pantallas se cargan con lazy loading (loadComponent).
 * Guards: adminGuard protege /admin (sin sesión → /login; USER → /partidos);
 * authGuard protege /mis-entradas (sin sesión → /login?returnUrl=…).
 */
export const routes: Routes = [
  { path: '', pathMatch: 'full', redirectTo: 'partidos' },
  {
    path: 'partidos',
    title: 'Partidos · Boletería Valle',
    loadComponent: () => import('./features/partidos/partidos-lista').then((m) => m.PartidosLista),
  },
  {
    path: 'partidos/:id',
    title: 'Detalle del partido · Boletería Valle',
    loadComponent: () => import('./features/partidos/partido-detalle').then((m) => m.PartidoDetalle),
  },
  {
    path: 'login',
    title: 'Ingresar · Boletería Valle',
    loadComponent: () => import('./features/login/login').then((m) => m.Login),
  },
  {
    path: 'registro',
    title: 'Crear cuenta · Boletería Valle',
    loadComponent: () => import('./features/registro/registro').then((m) => m.Registro),
  },
  {
    // Tarea 5.2/5.3: el panel admin (children stubs) crecerá aquí.
    path: 'admin',
    canActivate: [adminGuard],
    loadComponent: () => import('./features/admin/admin').then((m) => m.Admin),
    children: [
      { path: '', pathMatch: 'full', redirectTo: 'partidos' },
      {
        path: 'partidos',
        title: 'Admin · Partidos',
        loadComponent: () => import('./features/admin/admin-partidos').then((m) => m.AdminPartidos),
      },
      {
        path: 'reportes',
        title: 'Admin · Reportes',
        loadComponent: () => import('./features/admin/admin-reportes').then((m) => m.AdminReportes),
      },
    ],
  },
  {
    // Tarea 4.4: pantalla "Mis entradas" (reservas y códigos del usuario).
    path: 'mis-entradas',
    title: 'Mis entradas · Boletería Valle',
    canActivate: [authGuard],
    loadComponent: () => import('./features/mis-entradas/mis-entradas').then((m) => m.MisEntradas),
  },
  // El flujo de compra vive en el detalle del partido (tarea 4.4).
  { path: '**', redirectTo: 'partidos' },
];
