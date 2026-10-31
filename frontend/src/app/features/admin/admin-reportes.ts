import { HttpErrorResponse } from '@angular/common/http';
import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { mensajeApi } from '../../core/models/api-error';
import { Partido } from '../../core/models/partido';
import { AdminService, FilaReporteVenta } from '../../core/services/admin.service';
import { CatalogService } from '../../core/services/catalog.service';
import { CopPipe } from '../../shared/pipes/cop';

/**
 * Tarea 5.3 — Reporte de ventas (Native Query del sales-service, solo ADMIN).
 * Selector opcional de partido: cambia el query param `partidoId` del reporte.
 * Los TOTALES se calculan en frontend sumando las filas devueltas.
 */
@Component({
  selector: 'app-admin-reportes',
  imports: [CopPipe],
  templateUrl: './admin-reportes.html',
  styleUrl: './admin-reportes.scss',
})
export class AdminReportes implements OnInit {
  private readonly admin = inject(AdminService);
  private readonly catalogo = inject(CatalogService);

  // --- selector de partido (lectura con CatalogService) ---
  readonly partidos = signal<Partido[]>([]);
  readonly cargandoPartidos = signal(true);
  readonly errorPartidos = signal<string | null>(null);

  // --- reporte ---
  readonly filas = signal<FilaReporteVenta[]>([]);
  readonly cargando = signal(false);
  readonly error = signal<string | null>(null);
  readonly partidoId = signal<number | ''>('');

  readonly totalCantidad = computed(() =>
    this.filas().reduce((acumulado, fila) => acumulado + fila.cantidadVendida, 0),
  );
  readonly totalRecaudado = computed(() =>
    this.filas().reduce((acumulado, fila) => acumulado + fila.totalRecaudado, 0),
  );

  ngOnInit(): void {
    this.cargarPartidos();
    this.cargarReporte(); // primero sin filtro: reporte general
  }

  /** Cambia el filtro ("" = todos) y vuelve a pedir el reporte. */
  cambiarPartido(valor: string): void {
    this.partidoId.set(valor === '' ? '' : Number(valor));
    this.cargarReporte();
  }

  trackPorFila(_index: number, fila: FilaReporteVenta): string {
    return `${fila.partidoId}-${fila.localidadId}`;
  }

  private cargarPartidos(): void {
    this.catalogo.getPartidos().subscribe({
      next: (partidos) => {
        this.partidos.set(partidos);
        this.cargandoPartidos.set(false);
      },
      error: (err: HttpErrorResponse) => {
        this.errorPartidos.set(mensajeApi(err));
        this.cargandoPartidos.set(false);
      },
    });
  }

  private cargarReporte(): void {
    this.cargando.set(true);
    this.error.set(null);
    const actual = this.partidoId();
    const filtro = actual === '' ? undefined : actual;
    this.admin.reporteVentas(filtro).subscribe({
      next: (filas) => {
        this.filas.set(filas);
        this.cargando.set(false);
      },
      error: (err: HttpErrorResponse) => {
        this.error.set(mensajeApi(err));
        this.cargando.set(false);
      },
    });
  }
}
