import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of } from 'rxjs';
import { ReservaResumen, SalesService } from '../../core/services/sales.service';
import { MisEntradas } from './mis-entradas';

describe('MisEntradas', () => {
  let fixture: ComponentFixture<MisEntradas>;
  let httpMock: HttpTestingController;

  const confirmada: ReservaResumen = {
    id: 7,
    partidoId: 1,
    rival: 'Cali Hornets',
    localidadId: 10,
    cantidad: 2,
    total: 30000,
    estado: 'CONFIRMED',
    creadoEn: '2026-10-31T09:00:00',
    entrada: { codigo: 'ABC-1234', emitidaEn: '2026-10-31T10:00:00' },
  };

  const pendiente: ReservaResumen = {
    id: 8,
    partidoId: 2,
    rival: 'Buenos Aires',
    localidadId: 11,
    cantidad: 1,
    total: 15000,
    estado: 'PENDING',
    creadoEn: '2026-10-31T11:00:00',
    entrada: null,
  };

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [MisEntradas],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    }).compileComponents();
    httpMock = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(MisEntradas);
    await fixture.whenStable();
  });

  afterEach(() => {
    httpMock.verify();
    vi.restoreAllMocks();
  });

  const elemento = (): HTMLElement => fixture.nativeElement as HTMLElement;

  /** Intl (pipe cop) separa el "$" con un espacio NO separable: lo normalizamos al comparar. */
  const legible = (texto: string | null | undefined): string =>
    (texto ?? '').replace(/[\u00A0\u202F]/g, ' ');

  function boton(texto: string): HTMLButtonElement {
    const encontrado = [...elemento().querySelectorAll('button')].find(
      (b) => b.textContent?.trim() === texto,
    );
    expect(encontrado, `No existe el botón "${texto}"`).toBeTruthy();
    return encontrado as HTMLButtonElement;
  }

  function botones(texto: string): HTMLButtonElement[] {
    return [...elemento().querySelectorAll('button')].filter(
      (b) => b.textContent?.trim() === texto,
    );
  }

  /** GET /api/sales/reservas (carga inicial). */
  function flushReservas(reservas: ReservaResumen[]): Promise<void> {
    httpMock.expectOne('/api/sales/reservas').flush(reservas);
    return fixture.whenStable();
  }

  it('renderiza las reservas: código de la CONFIRMED, estados y botón Pagar de la PENDING', async () => {
    await flushReservas([confirmada, pendiente]);
    fixture.detectChanges();

    const texto = elemento().textContent ?? '';
    expect(texto).toContain('Cali Hornets');
    expect(texto).toContain('Buenos Aires');

    // entrada emitida con su código y fecha (solo campos del contrato)
    expect(texto).toContain('ABC-1234');
    expect(texto).toContain('2026-10-31T10:00:00');

    // estados con etiqueta clara
    expect(texto).toContain('CONFIRMED');
    expect(texto).toContain('Confirmada · entrada emitida');
    expect(texto).toContain('PENDING');
    expect(texto).toContain('Pendiente de pago');

    // totales con pipe cop (Espacio NO separable de Intl → normalizado)
    expect(legible(texto)).toContain('$ 30.000');
    expect(legible(texto)).toContain('$ 15.000');

    // Pagar solo para la PENDING; Cancelar para PENDING y CONFIRMED
    expect(botones('Pagar ahora').length).toBe(1);
    expect(botones('Cancelar reserva').length).toBe(2);
    expect(texto).not.toContain('No encontramos');
  });

  it('cancelar con confirm() TRUE llama a cancelar y recarga la lista', async () => {
    await flushReservas([confirmada, pendiente]);
    fixture.detectChanges();

    const confirmSpy = vi.spyOn(window, 'confirm').mockReturnValue(true);
    const ventas = TestBed.inject(SalesService);
    const cancelarSpy = vi.spyOn(ventas, 'cancelar').mockReturnValue(
      of({
        reservaId: 7,
        estado: 'CANCELLED',
        localidadId: 10,
        cantidad: 2,
        total: 30000,
        partidoId: 1,
        rival: 'Cali Hornets',
      }),
    );

    botones('Cancelar reserva')[0].click(); // la primera tarjeta es la CONFIRMED (id 7)
    fixture.detectChanges();

    expect(confirmSpy).toHaveBeenCalledTimes(1);
    expect(cancelarSpy).toHaveBeenCalledTimes(1);
    expect(cancelarSpy).toHaveBeenCalledWith(7);

    // recarga tras el éxito
    await flushReservas([{ ...confirmada, estado: 'CANCELLED', entrada: null }]);
    fixture.detectChanges();

    expect(elemento().textContent).toContain('Cancelada');
    expect(httpMock.match(() => true).length).toBe(0);
  });

  it('si el usuario NO confirma no se llama a cancelar', async () => {
    await flushReservas([confirmada]);
    fixture.detectChanges();

    vi.spyOn(window, 'confirm').mockReturnValue(false);
    const cancelarSpy = vi.spyOn(TestBed.inject(SalesService), 'cancelar');

    botones('Cancelar reserva')[0].click();
    fixture.detectChanges();

    expect(cancelarSpy).not.toHaveBeenCalled();
    expect(httpMock.match(() => true).length).toBe(0);
  });

  it('pagar una reserva PENDING la confirma y recarga con su código', async () => {
    await flushReservas([pendiente]);
    fixture.detectChanges();

    const ventas = TestBed.inject(SalesService);
    vi.spyOn(ventas, 'pagar').mockReturnValue(
      of({
        reservaId: 8,
        estado: 'CONFIRMED',
        entrada: { codigo: 'XYZ-9876', emitidaEn: '2026-10-31T13:00:00' },
      }),
    );

    boton('Pagar ahora').click();
    fixture.detectChanges();

    await flushReservas([
      { ...pendiente, estado: 'CONFIRMED', entrada: { codigo: 'XYZ-9876', emitidaEn: '2026-10-31T13:00:00' } },
    ]);
    fixture.detectChanges();

    const texto = elemento().textContent ?? '';
    expect(texto).toContain('XYZ-9876');
    expect(botones('Pagar ahora').length).toBe(0);
    expect(texto).toContain('Confirmada · entrada emitida');
  });

  it('muestra el estado vacío cuando no hay reservas', async () => {
    await flushReservas([]);
    fixture.detectChanges();

    expect(elemento().textContent).toContain('Aún no tienes entradas');
    expect(botones('Pagar ahora').length).toBe(0);
    expect(botones('Cancelar reserva').length).toBe(0);
  });

  it('muestra el error del backend si la lista no se puede cargar', async () => {
    httpMock.expectOne('/api/sales/reservas').flush(
      {
        timestamp: '2026-10-31T10:00:00',
        status: 503,
        error: 'Service Unavailable',
        message: 'sales no disponible',
        path: '/api/sales/reservas',
      },
      { status: 503, statusText: 'Service Unavailable' },
    );
    await fixture.whenStable();
    fixture.detectChanges();

    expect(fixture.componentInstance.error()).toBe('sales no disponible');
    expect(elemento().textContent).toContain('sales no disponible');
    expect(elemento().textContent).not.toContain('Aún no tienes entradas');
  });
});
