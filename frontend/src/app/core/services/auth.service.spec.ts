import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { AuthService, TOKEN_STORAGE_KEY, USER_STORAGE_KEY } from './auth.service';

describe('AuthService', () => {
  let servicio: AuthService;
  let httpMock: HttpTestingController;
  const routerSpy = { navigate: vi.fn() };

  beforeEach(() => {
    localStorage.clear();
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: Router, useValue: routerSpy },
      ],
    });
    servicio = TestBed.inject(AuthService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
    localStorage.clear();
    vi.clearAllMocks();
  });

  it('login() hace POST a /api/auth/login con las credenciales y guarda el token', () => {
    let respuestaToken: string | undefined;
    servicio
      .login({ email: 'usuario@valle.test', password: 'usuario123' })
      .subscribe((r) => (respuestaToken = r.token));

    const req = httpMock.expectOne('/api/auth/login');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ email: 'usuario@valle.test', password: 'usuario123' });
    req.flush({
      token: 'jwt-de-prueba',
      email: 'usuario@valle.test',
      rol: 'USER',
      nombre: 'Usuario',
    });

    expect(respuestaToken).toBe('jwt-de-prueba');
    expect(servicio.token()).toBe('jwt-de-prueba');
    expect(servicio.user()).toEqual({ email: 'usuario@valle.test', rol: 'USER', nombre: 'Usuario' });
    expect(servicio.estaLogueado()).toBe(true);
    expect(servicio.esAdmin()).toBe(false);
    expect(localStorage.getItem(TOKEN_STORAGE_KEY)).toBe('jwt-de-prueba');
    expect(localStorage.getItem(USER_STORAGE_KEY)).toContain('usuario@valle.test');
  });

  it('logout() limpia token, usuario y localStorage, y navega a /login', () => {
    servicio.login({ email: 'admin@valle.test', password: 'admin123' }).subscribe();
    httpMock
      .expectOne('/api/auth/login')
      .flush({ token: 'jwt-admin', email: 'admin@valle.test', rol: 'ADMIN', nombre: 'Admin' });
    expect(servicio.estaLogueado()).toBe(true);

    servicio.logout();

    expect(servicio.token()).toBeNull();
    expect(servicio.user()).toBeNull();
    expect(servicio.estaLogueado()).toBe(false);
    expect(servicio.esAdmin()).toBe(false);
    expect(localStorage.getItem(TOKEN_STORAGE_KEY)).toBeNull();
    expect(localStorage.getItem(USER_STORAGE_KEY)).toBeNull();
    expect(routerSpy.navigate).toHaveBeenCalledWith(['/login']);
  });

  it('esAdmin() es true solo con rol ADMIN', () => {
    servicio.login({ email: 'admin@valle.test', password: 'admin123' }).subscribe();
    httpMock
      .expectOne('/api/auth/login')
      .flush({ token: 'jwt-admin', email: 'admin@valle.test', rol: 'ADMIN', nombre: 'Admin' });

    expect(servicio.esAdmin()).toBe(true);
  });

  it('registro() hace POST a /api/auth/registro con {email,password,nombre}', () => {
    let registrado = false;
    servicio
      .registro({ email: 'nuevo@valle.test', password: 'secreto1', nombre: 'Nuevo' })
      .subscribe(() => (registrado = true));

    const req = httpMock.expectOne('/api/auth/registro');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ email: 'nuevo@valle.test', password: 'secreto1', nombre: 'Nuevo' });
    req.flush({ email: 'nuevo@valle.test', nombre: 'Nuevo', rol: 'USER' });

    expect(registrado).toBe(true);
    // El registro no crea sesión: solo el login guarda token.
    expect(servicio.token()).toBeNull();
  });
});
