import { Component } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';

/** Shell del panel admin (protegido por adminGuard). Las pantallas son children. */
@Component({
  selector: 'app-admin',
  imports: [RouterOutlet, RouterLink, RouterLinkActive],
  template: `
    <section class="pantalla">
      <h1>Panel admin</h1>
      <nav class="admin-nav">
        <a routerLink="/admin/partidos" routerLinkActive="activo">Partidos</a>
        <a routerLink="/admin/reportes" routerLinkActive="activo">Reportes</a>
      </nav>
      <router-outlet />
    </section>
  `,
})
export class Admin {}
