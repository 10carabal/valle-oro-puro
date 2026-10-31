import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { AdminService } from '../../core/services/admin.service';

/**
 * Tests HTTP de los endpoints admin (tareas 5.2/5.3).
 * Nota: el spec vive en features/admin/ junto a las pantallas que lo usan
 * (el servicio es nuevo y está en core/services/admin.service.ts).
 */
describe('AdminService', () => {
  let servicio: AdminService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    servicio = TestBed.inject(AdminService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('crearPartido hace POST a /api/catalog/partidos con el body exacto', () => {
    const datos = { rival: 'Cali Hornets', fecha: '2030-05-10', hora: '19:00', escenario: 'Coliseo' };
    let respuesta: { id?: number } | undefined;
    servicio.crearPartido(datos).subscribe((r) => (respuesta = r));

    const req = httpMock.expectOne('/api/catalog/partidos');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual(datos);
    req.flush({ id: 1, ...datos, estado: 'PROGRAMADO', localidades: [] });

    expect(respuesta?.id).toBe(1);
  });

  it('editarPartido hace PUT a /api/catalog/partidos/{id} incluyendo estado', () => {
    const datos = { rival: 'Cali Hornets', fecha: '2030-05-10', hora: '20:00', escenario: 'Coliseo', estado: 'JUGADO' as const };
    servicio.editarPartido(7, datos).subscribe();

    const req = httpMock.expectOne('/api/catalog/partidos/7');
    expect(req.request.method).toBe('PUT');
    expect(req.request.body).toEqual(datos);
    req.flush({ id: 7, ...datos, localidades: [] });
  });

  it('cancelarPartido hace PUT a /api/catalog/partidos/{id}/cancelar', () => {
    servicio.cancelarPartido(3).subscribe();

    const req = httpMock.expectOne('/api/catalog/partidos/3/cancelar');
    expect(req.request.method).toBe('PUT');
    req.flush({ id: 3, rival: 'Rival', fecha: '2030-01-01', hora: '19:00', escenario: 'Coliseo', estado: 'CANCELADO', localidades: [] });
  });

  it('crearLocalidad hace POST a /api/catalog/partidos/{id}/localidades', () => {
    servicio.crearLocalidad(3, { nombre: 'VIP', precio: 50000, cupo: 20 }).subscribe();

    const req = httpMock.expectOne('/api/catalog/partidos/3/localidades');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ nombre: 'VIP', precio: 50000, cupo: 20 });
    req.flush({ id: 30, nombre: 'VIP', precio: 50000, cupo: 20, vendidos: 0, disponibles: 20 });
  });

  it('editarLocalidad hace PUT a /api/catalog/localidades/{id}', () => {
    servicio.editarLocalidad(30, { nombre: 'VIP', precio: 60000, cupo: 25 }).subscribe();

    const req = httpMock.expectOne('/api/catalog/localidades/30');
    expect(req.request.method).toBe('PUT');
    expect(req.request.body).toEqual({ nombre: 'VIP', precio: 60000, cupo: 25 });
    req.flush({ id: 30, nombre: 'VIP', precio: 60000, cupo: 25, vendidos: 5, disponibles: 20 });
  });

  it('cancelarReservasPorPartido hace POST a /api/sales/reservas/cancelar-por-partido/{id}', () => {
    let respuesta: { canceladas: number } | undefined;
    servicio.cancelarReservasPorPartido(3).subscribe((r) => (respuesta = r));

    const req = httpMock.expectOne('/api/sales/reservas/cancelar-por-partido/3');
    expect(req.request.method).toBe('POST');
    req.flush({ canceladas: 4 });

    expect(respuesta?.canceladas).toBe(4);
  });

  it('reporteVentas hace GET a /api/sales/reportes/ventas CON partidoId cuando se selecciona', () => {
    let filas: unknown[] | undefined;
    servicio.reporteVentas(9).subscribe((r) => (filas = r));

    const req = httpMock.expectOne((r) => r.url === '/api/sales/reportes/ventas');
    expect(req.request.method).toBe('GET');
    expect(req.request.params.get('partidoId')).toBe('9');
    req.flush([{ partidoId: 9, rival: 'Rival', localidadId: 10, cantidadVendida: 2, totalRecaudado: 30000 }]);

    expect(filas?.length).toBe(1);
  });

  it('reporteVentas sin selección NO envía el parámetro partidoId', () => {
    servicio.reporteVentas().subscribe();

    const req = httpMock.expectOne((r) => r.url === '/api/sales/reportes/ventas');
    expect(req.request.params.has('partidoId')).toBe(false);
    req.flush([]);
  });
});
