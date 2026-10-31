import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Partido } from '../../core/models/partido';
import { AdminPartidos } from './admin-partidos';

describe('AdminPartidos', () => {
  let fixture: ComponentFixture<AdminPartidos>;
  let componente: AdminPartidos;
  let httpMock: HttpTestingController;

  const partidoEjemplo: Partido = {
    id: 1,
    rival: 'Cali Hornets',
    fecha: '2030-05-10',
    hora: '19:00',
    escenario: 'Coliseo Evangelista',
    estado: 'PROGRAMADO',
    localidades: [{ id: 10, nombre: 'General', precio: 15000, cupo: 100, vendidos: 30, disponibles: 70 }],
  };

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [AdminPartidos],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    }).compileComponents();
    httpMock = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(AdminPartidos);
    componente = fixture.componentInstance;
    await fixture.whenStable();
  });

  afterEach(() => {
    httpMock.verify();
    vi.restoreAllMocks();
  });

  /** Carga inicial de la lista (una petición GET a catalog). */
  function flushLista(partidos: Partido[]): Promise<void> {
    httpMock.expectOne('/api/catalog/partidos').flush(partidos);
    return fixture.whenStable();
  }

  it('renderiza la lista admin con el partido y su localidad (precio COP, cupo y vendidos)', async () => {
    await flushLista([partidoEjemplo]);
    fixture.detectChanges();

    const texto = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(texto).toContain('Cali Hornets');
    expect(texto).toContain('PROGRAMADO');
    expect(texto).toContain('Coliseo Evangelista');

    const filas = (fixture.nativeElement as HTMLElement).querySelectorAll('.tabla-localidades tbody tr');
    expect(filas.length).toBe(1);
    expect(filas[0].textContent).toContain('General');
    expect(filas[0].textContent).toContain('15.000'); // precio formateado con el pipe cop
    expect(filas[0].textContent).toContain('100'); // cupo
    expect(filas[0].textContent).toContain('30'); // vendidos
  });

  it('con el formulario inválido NO se llama al HTTP y se ven los errores de campo', async () => {
    await flushLista([partidoEjemplo]);
    fixture.detectChanges();

    componente.guardarPartido(); // formulario vacío → inválido

    expect(componente.formPartido.invalid).toBe(true);
    httpMock.expectNone((r) => r.method === 'POST' || r.method === 'PUT');

    fixture.detectChanges();
    const texto = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(texto).toContain('El rival es obligatorio.');
  });

  it('rechaza una fecha pasada sin llamar al backend', async () => {
    await flushLista([partidoEjemplo]);
    fixture.detectChanges();

    componente.formPartido.setValue({
      rival: 'Rival X',
      fecha: '2000-01-01',
      hora: '19:00',
      escenario: 'Coliseo',
      estado: 'PROGRAMADO',
    });
    componente.guardarPartido();

    expect(componente.formPartido.invalid).toBe(true);
    expect(componente.formPartido.controls.fecha.hasError('fechaPasada')).toBe(true);
    httpMock.expectNone((r) => r.method === 'POST' || r.method === 'PUT');
  });

  it('crea un partido válido: POST con el body correcto, mensaje de éxito y recarga de lista', async () => {
    await flushLista([partidoEjemplo]);
    fixture.detectChanges();

    componente.formPartido.setValue({
      rival: 'Buenos Aires',
      fecha: '2030-01-01',
      hora: '18:00',
      escenario: 'Coliseo Mayor',
      estado: 'PROGRAMADO',
    });
    componente.guardarPartido();

    const post = httpMock.expectOne('/api/catalog/partidos');
    expect(post.request.method).toBe('POST');
    expect(post.request.body).toEqual({
      rival: 'Buenos Aires',
      fecha: '2030-01-01',
      hora: '18:00',
      escenario: 'Coliseo Mayor',
    });
    post.flush({
      id: 2,
      rival: 'Buenos Aires',
      fecha: '2030-01-01',
      hora: '18:00',
      escenario: 'Coliseo Mayor',
      estado: 'PROGRAMADO',
      localidades: [],
    });

    // recarga de la lista tras el alta
    httpMock.expectOne('/api/catalog/partidos').flush([]);
    await fixture.whenStable();
    fixture.detectChanges();

    expect(componente.exito()).toContain('Partido creado correctamente');
    expect((fixture.nativeElement as HTMLElement).textContent).toContain('Partido creado correctamente');
  });

  it('cancela en secuencia: PRIMERO catalog (PUT cancelar) y DESPUÉS sales (cancelar reservas)', async () => {
    await flushLista([partidoEjemplo]);
    fixture.detectChanges();

    const confirmSpy = vi.spyOn(window, 'confirm').mockReturnValue(true);
    componente.cancelarPartido(partidoEjemplo);
    expect(confirmSpy).toHaveBeenCalledTimes(1);

    const cancelar = httpMock.expectOne('/api/catalog/partidos/1/cancelar');
    expect(cancelar.request.method).toBe('PUT');
    cancelar.flush({ ...partidoEjemplo, estado: 'CANCELADO' });

    // tras catalog quedan dos peticiones: la recarga de lista y la de sales
    const reservas = httpMock.expectOne((r) => r.url.endsWith('/reservas/cancelar-por-partido/1'));
    expect(reservas.request.method).toBe('POST');
    reservas.flush({ canceladas: 4 });

    httpMock.expectOne('/api/catalog/partidos').flush([{ ...partidoEjemplo, estado: 'CANCELADO' }]);
    await fixture.whenStable();
    fixture.detectChanges();

    expect(componente.exito()).toBe('Partido cancelado. Reservas canceladas: 4');
    expect((fixture.nativeElement as HTMLElement).textContent).toContain('Reservas canceladas: 4');
  });

  it('si sales falla DESPUÉS de cancelar en catalog, avisa que el partido quedó cancelado pero las reservas requieren reintento', async () => {
    await flushLista([partidoEjemplo]);
    fixture.detectChanges();

    vi.spyOn(window, 'confirm').mockReturnValue(true);
    componente.cancelarPartido(partidoEjemplo);

    httpMock
      .expectOne('/api/catalog/partidos/1/cancelar')
      .flush({ ...partidoEjemplo, estado: 'CANCELADO' });

    const reservas = httpMock.expectOne((r) => r.url.endsWith('/reservas/cancelar-por-partido/1'));
    reservas.flush(
      { timestamp: '2026-10-28T10:00:00', status: 503, error: 'Service Unavailable', message: 'sales no disponible', path: '/api/sales/reservas/cancelar-por-partido/1' },
      { status: 503, statusText: 'Service Unavailable' },
    );
    httpMock.expectOne('/api/catalog/partidos').flush([{ ...partidoEjemplo, estado: 'CANCELADO' }]);
    await fixture.whenStable();
    fixture.detectChanges();

    expect(componente.error()).toBeNull();
    expect(componente.aviso()).toContain('El partido se canceló');
    expect((fixture.nativeElement as HTMLElement).textContent).toContain('Requiere reintento');
  });

  it('no cancela nada si el usuario rechaza la confirmación del navegador', async () => {
    await flushLista([partidoEjemplo]);
    fixture.detectChanges();

    vi.spyOn(window, 'confirm').mockReturnValue(false);
    componente.cancelarPartido(partidoEjemplo);

    httpMock.expectNone((r) => r.method === 'PUT' || r.method === 'POST');
  });

  it('gestiona localidades: abre la vista, crea con POST y muestra el 409 de cupo con mensajeApi', async () => {
    await flushLista([partidoEjemplo]);
    fixture.detectChanges();

    componente.abrirGestion(partidoEjemplo);
    httpMock.expectOne('/api/catalog/partidos/1').flush(partidoEjemplo);
    await fixture.whenStable();
    fixture.detectChanges();

    const texto = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(texto).toContain('Localidades · Cali Hornets');

    // creación de localidad
    componente.formLocalidad.setValue({ id: null, nombre: 'Palco', precio: 80000, cupo: 12 });
    componente.guardarLocalidad();

    const post = httpMock.expectOne('/api/catalog/partidos/1/localidades');
    expect(post.request.method).toBe('POST');
    expect(post.request.body).toEqual({ nombre: 'Palco', precio: 80000, cupo: 12 });
    post.flush({ id: 12, nombre: 'Palco', precio: 80000, cupo: 12, vendidos: 0, disponibles: 12 });
    httpMock.expectOne('/api/catalog/partidos/1').flush(partidoEjemplo); // refresca detalle
    httpMock.expectOne('/api/catalog/partidos').flush([partidoEjemplo]); // refresca lista
    await fixture.whenStable();
    fixture.detectChanges();

    expect(componente.exitoLocalidad()).toContain('Localidad creada correctamente');

    // edición con cupo por debajo de vendidos → 409 del backend
    componente.editarLocalidad(partidoEjemplo.localidades[0]);
    componente.formLocalidad.controls.cupo.setValue(5); // vendidos = 30
    componente.guardarLocalidad();

    const put = httpMock.expectOne('/api/catalog/localidades/10');
    expect(put.request.method).toBe('PUT');
    expect(put.request.body).toEqual({ nombre: 'General', precio: 15000, cupo: 5 });
    put.flush(
      { timestamp: '2026-10-28T10:00:00', status: 409, error: 'Conflict', message: 'cupo menor a lo ya vendido', path: '/api/catalog/localidades/10' },
      { status: 409, statusText: 'Conflict' },
    );
    await fixture.whenStable();
    fixture.detectChanges();

    expect(componente.errorLocalidad()).toBe('cupo menor a lo ya vendido');
    expect((fixture.nativeElement as HTMLElement).textContent).toContain('cupo menor a lo ya vendido');
  });

  it('la localidad agostada muestra el indicador Agotado', async () => {
    const agotado: Partido = {
      ...partidoEjemplo,
      localidades: [{ id: 11, nombre: 'VIP', precio: 50000, cupo: 20, vendidos: 20, disponibles: 0 }],
    };
    await flushLista([agotado]);
    fixture.detectChanges();

    expect((fixture.nativeElement as HTMLElement).querySelector('.agotado')?.textContent).toContain('Agotado');
  });
});
