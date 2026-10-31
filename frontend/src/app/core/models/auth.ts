export type Rol = 'USER' | 'ADMIN';

/** Sesión del usuario en el frontend (se persiste en localStorage junto al token). */
export interface UsuarioSesion {
  email: string;
  rol: Rol;
  nombre?: string;
}

/** POST /api/auth/login y también body de /api/auth/registro más el nombre. */
export interface CredencialesLogin {
  email: string;
  password: string;
}

export interface RegistroRequest {
  email: string;
  password: string;
  nombre: string;
}

/** Respuesta de POST /api/auth/login → 200 */
export interface LoginResponse {
  token: string;
  email: string;
  rol: Rol;
  nombre: string;
}

/** Respuesta de POST /api/auth/registro → 201 (no trae token) */
export interface RegistroResponse {
  email: string;
  nombre: string;
  rol: Rol;
}
