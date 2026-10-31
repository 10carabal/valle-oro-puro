import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { AuthService } from '../services/auth.service';
import { jwtInterceptor } from './jwt.interceptor';

describe('jwtInterceptor', () => {
  let httpClient: HttpClient;
  let httpMock: HttpTestingController;
  const logoutSpy = vi.fn();
  const tokenSpy = vi.fn<() => string | null>(() => null);

  beforeEach(() => {
    tokenSpy.mockReturnValue(null);
    logoutSpy.mockClear();
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([jwtInterceptor])),
        provideHttpClientTesting(),
        { provide: AuthService, useValue: { token: tokenSpy, logout: logoutSpy } },
      ],
    });
    httpClient = TestBed.inject(HttpClient);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('adjunta Authorization: Bearer <token> a las peticiones a /api/ cuando hay token', () => {
    tokenSpy.mockReturnValue('jwt-de-prueba');

    httpClient.get('/api/catalog/partidos').subscribe();

    const req = httpMock.expectOne('/api/catalog/partidos');
    expect(req.request.headers.get('Authorization')).toBe('Bearer jwt-de-prueba');
    req.flush([]);
  });

  it('no adjunta Authorization cuando no hay token', () => {
    httpClient.get('/api/catalog/partidos').subscribe();

    const req = httpMock.expectOne('/api/catalog/partidos');
    expect(req.request.headers.has('Authorization')).toBe(false);
    req.flush([]);
  });

  it('ante un 401 fuera del login hace logout (limpia sesión y redirige a /login)', () => {
    tokenSpy.mockReturnValue('jwt-vencido');

    httpClient.get('/api/catalog/partidos').subscribe({ error: () => {} });
    const req = httpMock.expectOne('/api/catalog/partidos');
    req.flush({ message: 'Token inválido' }, { status: 401, statusText: 'Unauthorized' });

    expect(logoutSpy).toHaveBeenCalledTimes(1);
  });

  it('ante un 401 del propio login NO hace logout', () => {
    httpClient.post('/api/auth/login', {}).subscribe({ error: () => {} });
    const req = httpMock.expectOne('/api/auth/login');
    req.flush({ message: 'Credenciales inválidas' }, { status: 401, statusText: 'Unauthorized' });

    expect(logoutSpy).not.toHaveBeenCalled();
  });
});
