import { TestBed } from '@angular/core/testing';
import { ActivatedRouteSnapshot, provideRouter, Router, RouterStateSnapshot, UrlTree } from '@angular/router';
import { AuthService } from '../services/auth.service';
import { adminGuard } from './admin.guard';
import { authGuard } from './auth.guard';

describe('authGuard y adminGuard', () => {
  const auth = {
    estaLogueado: vi.fn(() => false),
    esAdmin: vi.fn(() => false),
  };

  beforeEach(() => {
    auth.estaLogueado.mockReturnValue(false);
    auth.esAdmin.mockReturnValue(false);
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        { provide: AuthService, useValue: auth },
      ],
    });
  });

  /** Ejecuta un guard funcional dentro del contexto de inyección de TestBed. */
  function ejecutar(guard: typeof authGuard, url = '/admin'): unknown {
    const ruta = {} as ActivatedRouteSnapshot;
    const estado = { url } as RouterStateSnapshot;
    return TestBed.runInInjectionContext(() => guard(ruta, estado));
  }

  function serializar(resultado: unknown): string {
    return TestBed.inject(Router).serializeUrl(resultado as UrlTree);
  }

  it('authGuard sin sesión → redirige a /login con returnUrl', () => {
    const resultado = ejecutar(authGuard, '/partidos');
    expect(resultado).toBeInstanceOf(UrlTree);
    expect(serializar(resultado)).toContain('/login');
    expect(serializar(resultado)).toContain('returnUrl');
  });

  it('authGuard con sesión → permite el paso', () => {
    auth.estaLogueado.mockReturnValue(true);
    expect(ejecutar(authGuard)).toBe(true);
  });

  it('adminGuard sin sesión → redirige a /login', () => {
    const resultado = ejecutar(adminGuard);
    expect(resultado).toBeInstanceOf(UrlTree);
    expect(serializar(resultado)).toContain('/login');
  });

  it('adminGuard con rol USER → redirige a /partidos', () => {
    auth.estaLogueado.mockReturnValue(true);
    auth.esAdmin.mockReturnValue(false); // USER
    const resultado = ejecutar(adminGuard);
    expect(resultado).toBeInstanceOf(UrlTree);
    expect(serializar(resultado)).toContain('/partidos');
  });

  it('adminGuard con rol ADMIN → permite el paso', () => {
    auth.estaLogueado.mockReturnValue(true);
    auth.esAdmin.mockReturnValue(true);
    expect(ejecutar(adminGuard)).toBe(true);
  });
});
