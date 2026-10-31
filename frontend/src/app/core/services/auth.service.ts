import { HttpClient } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { Observable, tap } from 'rxjs';
import { environment } from '../../../environments/environment';
import {
  CredencialesLogin,
  LoginResponse,
  RegistroRequest,
  RegistroResponse,
  UsuarioSesion,
} from '../models/auth';

export const TOKEN_STORAGE_KEY = 'basket_token';
export const USER_STORAGE_KEY = 'basket_usuario';

/**
 * Sesión del usuario: signals `token` y `user` (reaccionan en la UI),
 * persistencia en localStorage y llamadas a auth-service (:8081) vía proxy.
 */
@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http = inject(HttpClient);
  private readonly router = inject(Router);

  readonly token = signal<string | null>(localStorage.getItem(TOKEN_STORAGE_KEY));
  readonly user = signal<UsuarioSesion | null>(this.leerUsuarioDeStorage());

  readonly estaLogueado = computed(() => this.token() !== null);
  readonly esAdmin = computed(() => this.user()?.rol === 'ADMIN');

  /** POST /api/auth/login → 200 {token,email,rol,nombre} · 401 credenciales. Guarda la sesión. */
  login(credenciales: CredencialesLogin): Observable<LoginResponse> {
    return this.http
      .post<LoginResponse>(`${environment.apiAuth}/login`, credenciales)
      .pipe(tap((respuesta) => this.guardarSesion(respuesta)));
  }

  /** POST /api/auth/registro → 201 {email,nombre,rol} · 409 email duplicado. No crea sesión. */
  registro(datos: RegistroRequest): Observable<RegistroResponse> {
    return this.http.post<RegistroResponse>(`${environment.apiAuth}/registro`, datos);
  }

  /** Limpia la sesión (token + usuario) y redirige a /login. */
  logout(): void {
    localStorage.removeItem(TOKEN_STORAGE_KEY);
    localStorage.removeItem(USER_STORAGE_KEY);
    this.token.set(null);
    this.user.set(null);
    this.router.navigate(['/login']);
  }

  private guardarSesion(respuesta: LoginResponse): void {
    const usuario: UsuarioSesion = {
      email: respuesta.email,
      rol: respuesta.rol,
      nombre: respuesta.nombre,
    };
    localStorage.setItem(TOKEN_STORAGE_KEY, respuesta.token);
    localStorage.setItem(USER_STORAGE_KEY, JSON.stringify(usuario));
    this.token.set(respuesta.token);
    this.user.set(usuario);
  }

  private leerUsuarioDeStorage(): UsuarioSesion | null {
    const crudo = localStorage.getItem(USER_STORAGE_KEY);
    if (!crudo) {
      return null;
    }
    try {
      return JSON.parse(crudo) as UsuarioSesion;
    } catch {
      return null;
    }
  }
}
