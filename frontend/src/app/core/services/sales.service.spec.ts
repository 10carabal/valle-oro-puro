import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { SalesService, EstadoReserva } from './sales.service';

/** Tests HTTP del flujo de compra (tarea 4.4) contra el contrato de sales-service. */
describe('SalesService', () => {
  let servicio: SalesService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    servicio = TestBed.inject(SalesService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('reservar hace POST a /api/sales/reservas con el body {localidadId, cantidad}', () => {
    let respuesta: { reservaId?: number; estado?: string } | undefined;
    servicio.reservar(10, 2).subscribe((r) => (respuesta = r));

    const req = httpMock.expectOne('/api/sales/reservas');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ localidadId: 10, cantidad: 2 });
    req.flush({
      reservaId: 7,
      estado: 'PENDING',
      localidadId: 10,
      cantidad: 2,
      total: 30000,
      partidoId: 1,
      rival: 'Cali Hornets',
    });

    expect(respuesta?.reservaId).toBe(7);
    expect(respuesta?.estado).toBe('PENDING');
  });

  it('pagar hace POST a /api/sales/reservas/{id}/pagar', () => {
    let respuesta: { estado?: string; entrada?: { codigo: string } } | undefined;
    servicio.pagar(7).subscribe((r) => (respuesta = r));

    const req = httpMock.expectOne('/api/sales/reservas/7/pagar');
    expect(req.request.method).toBe('POST');
    req.flush({
      reservaId: 7,
      estado: 'CONFIRMED',
      entrada: { codigo: 'ABC-123', emitidaEn: '2026-10-31T10:00:00' },
    });

    expect(respuesta?.estado).toBe('CONFIRMED');
    expect(respuesta?.entrada?.codigo).toBe('ABC-123');
  });

  it('cancelar hace POST a /api/sales/reservas/{id}/cancelar', () => {
    let respuesta: { estado?: string } | undefined;
    servicio.cancelar(7).subscribe((r) => (respuesta = r));

    const req = httpMock.expectOne('/api/sales/reservas/7/cancelar');
    expect(req.request.method).toBe('POST');
    req.flush({
      reservaId: 7,
      estado: 'CANCELLED',
      localidadId: 10,
      cantidad: 2,
      total: 30000,
      partidoId: 1,
      rival: 'Cali Hornets',
    });

    expect(respuesta?.estado).toBe('CANCELLED');
  });

  it('misReservas hace GET a /api/sales/reservas SIN parámetros', () => {
    let filas: unknown[] | undefined;
    servicio.misReservas().subscribe((r) => (filas = r));

    const req = httpMock.expectOne('/api/sales/reservas');
    expect(req.request.method).toBe('GET');
    expect(req.request.params.has('estado')).toBe(false);
    req.flush([
      {
        id: 7,
        partidoId: 1,
        rival: 'Cali Hornets',
        localidadId: 10,
        cantidad: 2,
        total: 30000,
        estado: 'CONFIRMED',
        creadoEn: '2026-10-31T09:00:00',
        entrada: { codigo: 'ABC-123', emitidaEn: '2026-10-31T10:00:00' },
      },
    ]);

    expect(filas?.length).toBe(1);
  });

  it('misReservas con estado envía el query param estado', () => {
    let filas: unknown[] | undefined;
    const estado: EstadoReserva = 'PENDING';
    servicio.misReservas(estado).subscribe((r) => (filas = r));

    // Ojo: `expectOne(string)` compara con la URL SIN query params → usamos predicado.
    const req = httpMock.expectOne(
      (r) => r.url === '/api/sales/reservas' && r.params.get('estado') === 'PENDING',
    );
    expect(req.request.method).toBe('GET');
    expect(req.request.params.get('estado')).toBe('PENDING');
    req.flush([]);

    expect(filas).toEqual([]);
  });
});
