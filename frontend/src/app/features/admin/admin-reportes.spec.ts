import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Partido } from '../../core/models/partido';
import { FilaReporteVenta } from '../../core/services/admin.service';
import { AdminReportes } from './admin-reportes';

describe('AdminReportes', () => {
  let fixture: ComponentFixture<AdminReportes>;
  let componente: AdminReportes;
  let httpMock: HttpTestingController;

  const filasEjemplo: FilaReporteVenta[] = [
    { partidoId: 1, rival: 'Cali Hornets', localidadId: 10, cantidadVendida: 3, totalRecaudado: 45000 },
    { partidoId: 1, rival: 'Cali Hornets', localidadId: 11, cantidadVendida: 2, totalRecaudado: 100000 },
  ];

  const partidosEjemplo: Partido[] = [
    {
      id: 1,
      rival: 'Cali Hornets',
      fecha: '2030-05-10',
      hora: '19:00',
      escenario: 'Coliseo',
      estado: 'PROGRAMADO',
      localidades: [],
    },
  ];

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [AdminReportes],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    }).compileComponents();
    httpMock = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(AdminReportes);
    componente = fixture.componentInstance;
    await fixture.whenStable();
  });

  afterEach(() => httpMock.verify());

  /** Carga inicial: selector de partidos + reporte general (sin filtro). */
  function flushInicial(filaReporte: FilaReporteVenta[]): Promise<void> {
    httpMock.expectOne((r) => r.url === '/api/catalog/partidos').flush(partidosEjemplo);
    httpMock.expectOne((r) => r.url === '/api/sales/reportes/ventas').flush(filaReporte);
    return fixture.whenStable();
  }

  it('renderiza las filas con total recaudado en COP y la fila de TOTALES', async () => {
    await flushInicial(filasEjemplo);
    fixture.detectChanges();

    const elemento = fixture.nativeElement as HTMLElement;
    const texto = elemento.textContent ?? '';
    expect(texto).toContain('Cali Hornets');
    expect(texto).toContain('45.000'); // total de la fila 1 (pipe cop)
    expect(texto).toContain('145.000'); // total de la fila 2

    const filas = elemento.querySelectorAll('.tabla-reporte tbody tr');
    expect(filas.length).toBe(2);
    expect(filas[0].textContent).toContain('10'); // localidadId

    const pie = elemento.querySelector('.tabla-reporte tfoot');
    expect(pie?.textContent).toContain('TOTALES');
    expect(pie?.textContent).toContain('5'); // 3 + 2 entradas
    expect(pie?.textContent).toContain('145.000'); // 45.000 + 100.000
    expect(componente.totalCantidad()).toBe(5);
    expect(componente.totalRecaudado()).toBe(145000);
  });

  it('al seleccionar un partido vuelve a pedir el reporte con el query param partidoId', async () => {
    await flushInicial([]);
    fixture.detectChanges();

    componente.cambiarPartido('7');

    const req = httpMock.expectOne((r) => r.url === '/api/sales/reportes/ventas');
    expect(req.request.method).toBe('GET');
    expect(req.request.params.get('partidoId')).toBe('7');
    req.flush(filasEjemplo);
    await fixture.whenStable();
    fixture.detectChanges();

    expect(componente.filas().length).toBe(2);
    expect((fixture.nativeElement as HTMLElement).querySelectorAll('.tabla-reporte tbody tr').length).toBe(2);
  });

  it('vuelve al reporte general sin partidoId cuando se limpia el filtro', async () => {
    await flushInicial([]);
    fixture.detectChanges();

    componente.cambiarPartido('7');
    httpMock
      .expectOne((r) => r.url === '/api/sales/reportes/ventas')
      .flush(filasEjemplo);
    await fixture.whenStable();

    componente.cambiarPartido('');
    const req = httpMock.expectOne((r) => r.url === '/api/sales/reportes/ventas');
    expect(req.request.params.has('partidoId')).toBe(false);
    req.flush([]);
    await fixture.whenStable();
    fixture.detectChanges();

    expect(componente.partidoId()).toBe('');
    expect((fixture.nativeElement as HTMLElement).textContent).toContain('Aún no hay ventas confirmadas');
  });

  it('muestra el estado vacío amigable cuando no hay ventas confirmadas', async () => {
    await flushInicial([]);
    fixture.detectChanges();

    const texto = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(texto).toContain('Aún no hay ventas confirmadas');
    expect(texto).not.toContain('TOTALES');
  });
});
