import { HttpErrorResponse, provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, provideRouter, Router } from '@angular/router';
import { of, throwError } from 'rxjs';
import { Partido } from '../../core/models/partido';
import { AuthService } from '../../core/services/auth.service';
import { SalesService } from '../../core/services/sales.service';
import { PartidoDetalle } from './partido-detalle';

describe('PartidoDetalle', () => {
  let fixture: ComponentFixture<PartidoDetalle>;
  let httpMock: HttpTestingController;

  const partidoEjemplo: Partido = {
    id: 1,
    rival: 'Cali Hornets',
    fecha: '2026-10-30',
    hora: '19:00',
    escenario: 'Coliseo Evangelista',
    estado: 'PROGRAMADO',
    localidades: [],
  };

  const localidadConCupo = {
    id: 10,
    nombre: 'General',
    precio: 15000,
    cupo: 100,
    vendidos: 30,
    disponibles: 70,
  };

  beforeEach(async () => {
    localStorage.clear();
    await TestBed.configureTestingModule({
      imports: [PartidoDetalle],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        { provide: ActivatedRoute, useValue: { paramMap: of(convertToParamMap({ id: '1' })) } },
      ],
    }).compileComponents();
    httpMock = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(PartidoDetalle);
    await fixture.whenStable();
  });

  afterEach(() => {
    httpMock.verify();
    vi.restoreAllMocks();
    localStorage.clear();
  });

  const elemento = (): HTMLElement => fixture.nativeElement as HTMLElement;

  /** Intl (pipe cop) separa el "$" con un espacio NO separable: lo normalizamos al comparar. */
  const legible = (texto: string | null | undefined): string =>
    (texto ?? '').replace(/[\u00A0\u202F]/g, ' ');

  /** Busca un botón por su texto exacto (recorta espacios). */
  function boton(texto: string): HTMLButtonElement {
    const encontrado = [...elemento().querySelectorAll('button')].find(
      (b) => b.textContent?.trim() === texto,
    );
    expect(encontrado, `No existe el botón "${texto}"`).toBeTruthy();
    return encontrado as HTMLButtonElement;
  }

  /** Carga inicial del detalle (una petición GET a catalog). */
  function cargarDetalle(partido: Partido): Promise<void> {
    httpMock.expectOne('/api/catalog/partidos/1').flush(partido);
    return fixture.whenStable();
  }

  /** Activa la sesión ANTES de pulsar Comprar (AuthService ya está inyectado). */
  function abrirSesion(): void {
    TestBed.inject(AuthService).token.set('jwt-de-prueba');
  }

  it('marca Agotado y deja el botón Comprar deshabilitado sin cupo', async () => {
    httpMock.expectOne('/api/catalog/partidos/1').flush({
      ...partidoEjemplo,
      localidades: [{ id: 11, nombre: 'VIP', precio: 50000, cupo: 10, vendidos: 10, disponibles: 0 }],
    });
    await fixture.whenStable();
    fixture.detectChanges();

    const elemento = fixture.nativeElement as HTMLElement;
    expect(elemento.textContent).toContain('VIP');
    expect(elemento.textContent).toContain('Agotado');
    const boton = elemento.querySelector('button.boton') as HTMLButtonElement | null;
    expect(boton).not.toBeNull();
    expect(boton?.disabled).toBe(true);
    expect(boton?.textContent?.trim()).toBe('Agotado');
  });

  it('permite ver disponibles cuando hay cupo', async () => {
    httpMock.expectOne('/api/catalog/partidos/1').flush({
      ...partidoEjemplo,
      localidades: [{ id: 10, nombre: 'General', precio: 15000, cupo: 100, vendidos: 30, disponibles: 70 }],
    });
    await fixture.whenStable();
    fixture.detectChanges();

    const elemento = fixture.nativeElement as HTMLElement;
    expect(elemento.textContent).toContain('70');
    expect(elemento.textContent).not.toContain('Agotado');
  });

  it('SIN sesión, pulsar Comprar navega a /login con returnUrl y no abre el panel', async () => {
    await cargarDetalle({ ...partidoEjemplo, localidades: [localidadConCupo] });
    fixture.detectChanges();

    const router = TestBed.inject(Router);
    const navegar = vi.spyOn(router, 'navigate').mockResolvedValue(true);

    boton('Comprar').click();

    expect(navegar).toHaveBeenCalledTimes(1);
    expect(navegar.mock.calls[0][0]).toEqual(['/login']);
    expect(navegar.mock.calls[0][1]).toEqual({ queryParams: { returnUrl: router.url } });

    fixture.detectChanges();
    expect(fixture.componentInstance.paso()).toBeNull();
    expect(elemento().querySelector('input#cantidad')).toBeNull();
  });

  it('flujo completo con sesión: cantidad → reserva → pago simulado → código de entrada', async () => {
    await cargarDetalle({ ...partidoEjemplo, localidades: [localidadConCupo] });
    fixture.detectChanges();
    abrirSesion();

    // --- paso 1: formulario con cantidad y resumen ---
    boton('Comprar').click();
    fixture.detectChanges();

    const input = elemento().querySelector('input#cantidad') as HTMLInputElement;
    expect(input).not.toBeNull();
    expect(input.min).toBe('1');
    expect(input.max).toBe('70');
    expect(input.required).toBe(true);
    expect(fixture.componentInstance.paso()).toBe(1);
    expect(elemento().textContent).toContain('Elegir cantidad');
    expect(legible(elemento().textContent)).toContain('$ 15.000'); // precio unitario (pipe cop)

    // cantidad = 2 → total calculado 2 × 15.000
    input.value = '2';
    input.dispatchEvent(new Event('input'));
    fixture.detectChanges();
    expect(legible(elemento().textContent)).toContain('$ 30.000');

    boton('Reservar').click();
    fixture.detectChanges();

    const reserva = httpMock.expectOne('/api/sales/reservas');
    expect(reserva.request.method).toBe('POST');
    expect(reserva.request.body).toEqual({ localidadId: 10, cantidad: 2 });
    reserva.flush({
      reservaId: 7,
      estado: 'PENDING',
      localidadId: 10,
      cantidad: 2,
      total: 30000,
      partidoId: 1,
      rival: 'Cali Hornets',
    });

    // refresco de disponibilidad tras reservar (catalog ya descontó el cupo)
    httpMock
      .expectOne('/api/catalog/partidos/1')
      .flush({
        ...partidoEjemplo,
        localidades: [{ ...localidadConCupo, vendidos: 32, disponibles: 68 }],
      });
    await fixture.whenStable();
    fixture.detectChanges();

    // --- paso 2: confirmar pago (simulado) ---
    expect(fixture.componentInstance.paso()).toBe(2);
    expect(elemento().textContent).toContain('Confirmar pago (simulado)');
    expect(legible(elemento().textContent)).toContain('$ 30.000');

    boton('Pagar ahora').click();
    fixture.detectChanges();

    const pago = httpMock.expectOne('/api/sales/reservas/7/pagar');
    expect(pago.request.method).toBe('POST');
    pago.flush({
      reservaId: 7,
      estado: 'CONFIRMED',
      entrada: { codigo: 'ABC-1234', emitidaEn: '2026-10-31T12:00:00' },
    });
    await fixture.whenStable();
    fixture.detectChanges();

    // --- paso 3: código de entrada ---
    expect(fixture.componentInstance.paso()).toBe(3);
    expect(elemento().textContent).toContain('¡Compra confirmada!');
    expect(elemento().textContent).toContain('ABC-1234');
    expect(elemento().textContent).toContain('2026-10-31T12:00:00');
    expect(elemento().querySelector('a[href="/mis-entradas"]')).not.toBeNull();
  });

  it('si la reserva responde 409 muestra el mensaje amigable y refresca la disponibilidad', async () => {
    await cargarDetalle({ ...partidoEjemplo, localidades: [localidadConCupo] });
    fixture.detectChanges();
    abrirSesion();

    const ventas = TestBed.inject(SalesService);
    vi.spyOn(ventas, 'reservar').mockReturnValue(
      throwError(
        () =>
          new HttpErrorResponse({
            status: 409,
            statusText: 'Conflict',
            error: { message: 'No hay cupo disponible' },
          }),
      ),
    );

    boton('Comprar').click();
    fixture.detectChanges();
    boton('Reservar').click();
    fixture.detectChanges();

    // mensaje propio (no el crudo del backend) y panel abierto
    expect(fixture.componentInstance.errorCompra()).toBe(
      'No quedan entradas disponibles en esta localidad',
    );
    expect(elemento().textContent).toContain('No quedan entradas disponibles en esta localidad');
    expect(fixture.componentInstance.paso()).toBe(1);

    // refresco en segundo plano de la disponibilidad
    const refresco = httpMock.expectOne('/api/catalog/partidos/1');
    expect(refresco.request.method).toBe('GET');
    refresco.flush({
      ...partidoEjemplo,
      localidades: [{ ...localidadConCupo, disponibles: 69, vendidos: 31 }],
    });
    await fixture.whenStable();
    fixture.detectChanges();

    expect(fixture.componentInstance.partido()?.localidades[0].disponibles).toBe(69);
    expect(fixture.componentInstance.paso()).toBe(1); // el panel NO se cierra
    expect(elemento().textContent).toContain('No quedan entradas disponibles en esta localidad');
  });
});
