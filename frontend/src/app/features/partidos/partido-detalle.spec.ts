import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, provideRouter } from '@angular/router';
import { of } from 'rxjs';
import { Partido } from '../../core/models/partido';
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

  beforeEach(async () => {
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

  afterEach(() => httpMock.verify());

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
});
