import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { App } from './app';
import { TOKEN_STORAGE_KEY, USER_STORAGE_KEY } from './core/services/auth.service';

describe('App', () => {
  beforeEach(async () => {
    localStorage.clear();
    await TestBed.configureTestingModule({
      imports: [App],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    }).compileComponents();
  });

  afterEach(() => localStorage.clear());

  it('crea la app', () => {
    const fixture = TestBed.createComponent(App);
    expect(fixture.componentInstance).toBeTruthy();
  });

  it('muestra el enlace a Partidos e Ingresar sin sesión, y oculta Admin', async () => {
    const fixture = TestBed.createComponent(App);
    await fixture.whenStable();
    fixture.detectChanges();

    const elemento = fixture.nativeElement as HTMLElement;
    expect(elemento.textContent).toContain('Partidos');
    expect(elemento.textContent).toContain('Ingresar');
    expect(elemento.textContent).not.toContain('Salir');
    expect(elemento.querySelector('a[href="/admin"]')).toBeNull();
  });

  it('con sesión muestra el usuario y Salir; el link Admin solo con rol ADMIN', async () => {
    localStorage.setItem(TOKEN_STORAGE_KEY, 'jwt-de-prueba');
    localStorage.setItem(
      USER_STORAGE_KEY,
      JSON.stringify({ email: 'admin@valle.test', rol: 'ADMIN', nombre: 'Admin' }),
    );

    const fixture = TestBed.createComponent(App);
    await fixture.whenStable();
    fixture.detectChanges();

    const elemento = fixture.nativeElement as HTMLElement;
    expect(elemento.textContent).toContain('Admin');
    expect(elemento.textContent).toContain('Salir');
    expect(elemento.textContent).not.toContain('Ingresar');
  });

  it('con sesión USER NO muestra el link Admin', async () => {
    localStorage.setItem(TOKEN_STORAGE_KEY, 'jwt-de-prueba');
    localStorage.setItem(
      USER_STORAGE_KEY,
      JSON.stringify({ email: 'usuario@valle.test', rol: 'USER', nombre: 'Usuario' }),
    );

    const fixture = TestBed.createComponent(App);
    await fixture.whenStable();
    fixture.detectChanges();

    const elemento = fixture.nativeElement as HTMLElement;
    expect(elemento.textContent).toContain('Usuario');
    expect(elemento.querySelector('a[href="/admin"]')).toBeNull();
  });
});
