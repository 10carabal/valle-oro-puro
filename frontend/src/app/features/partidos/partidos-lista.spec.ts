import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { Partido } from '../../core/models/partido';
import { PartidosLista } from './partidos-lista';

describe('PartidosLista', () => {
  let fixture: ComponentFixture<PartidosLista>;
  let httpMock: HttpTestingController;

  const partidoEjemplo: Partido = {
    id: 1,
    rival: 'Cali Hornets',
    fecha: '2026-10-30',
    hora: '19:00',
    escenario: 'Coliseo Evangelista',
    estado: 'PROGRAMADO',
    localidades: [
      { id: 10, nombre: 'General', precio: 15000, cupo: 100, vendidos: 30, disponibles: 70 },
      { id: 11, nombre: 'VIP', precio: 50000, cupo: 20, vendidos: 20, disponibles: 0 },
    ],
  };

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [PartidosLista],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    }).compileComponents();
    httpMock = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(PartidosLista);
    await fixture.whenStable();
  });

  afterEach(() => httpMock.verify());

  it('pide la lista al backend y renderiza rival, precio en COP y disponibilidad', async () => {
    const req = httpMock.expectOne((r) => r.url === '/api/catalog/partidos');
    expect(req.request.method).toBe('GET');
    req.flush([partidoEjemplo]);
    await fixture.whenStable();
    fixture.detectChanges();

    const texto = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(texto).toContain('Cali Hornets');
    expect(texto).toContain('Coliseo Evangelista');
    expect(texto).toContain('PROGRAMADO');
    expect(texto).toContain('15.000'); // precio COP formateado (es-CO)
    expect(texto).toContain('70'); // disponibles de General
    expect(texto).toContain('Agotado'); // VIP con disponibles = 0
  });

  it('muestra el estado vacío cuando el backend no devuelve partidos', async () => {
    httpMock.expectOne((r) => r.url === '/api/catalog/partidos').flush([]);
    await fixture.whenStable();
    fixture.detectChanges();

    const texto = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(texto).toContain('No hay partidos para ese filtro');
  });
});
