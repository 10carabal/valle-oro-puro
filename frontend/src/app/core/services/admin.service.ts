import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { EstadoPartido, Localidad, Partido } from '../models/partido';

/** Body de POST /api/catalog/partidos (sin estado: el backend lo nace PROGRAMADO). */
export interface PartidoAlta {
  rival: string;
  fecha: string; // YYYY-MM-DD
  hora: string; // HH:mm
  escenario: string;
}

/** Body de PUT /api/catalog/partidos/{id}: el estado es opcional (si falta, se conserva). */
export interface PartidoEdicion extends PartidoAlta {
  estado?: EstadoPartido;
}

/** Body de POST /api/catalog/partidos/{id}/localidades y PUT /api/catalog/localidades/{id}. */
export interface LocalidadInput {
  nombre: string;
  precio: number;
  cupo: number;
}

/** GET /api/sales/reportes/ventas?partidoId= (Native Query, solo reservas CONFIRMED). */
export interface FilaReporteVenta {
  partidoId: number;
  rival: string;
  localidadId: number;
  cantidadVendida: number;
  totalRecaudado: number;
}

/** POST /api/sales/reservas/cancelar-por-partido/{partidoId}. */
export interface CanceladasResponse {
  canceladas: number;
}

/**
 * Escrituras del panel admin sobre catalog-service (:8082) y sales-service (:8083).
 * El interceptor JWT adjunta el Bearer con rol ADMIN; las peticiones de LECTURA
 * (listado/detalle de partidos) siguen siendo las de CatalogService — aquí no se
 * duplican para no tener dos fuentes de verdad.
 */
@Injectable({ providedIn: 'root' })
export class AdminService {
  private readonly http = inject(HttpClient);

  /** POST /api/catalog/partidos → 201 */
  crearPartido(datos: PartidoAlta): Observable<Partido> {
    return this.http.post<Partido>(`${environment.apiCatalog}/partidos`, datos);
  }

  /** PUT /api/catalog/partidos/{id} → 200 (estado opcional) */
  editarPartido(id: number, datos: PartidoEdicion): Observable<Partido> {
    return this.http.put<Partido>(`${environment.apiCatalog}/partidos/${id}`, datos);
  }

  /** PUT /api/catalog/partidos/{id}/cancelar → 200 (cancelación LÓGICA, no borra). */
  cancelarPartido(id: number): Observable<Partido> {
    return this.http.put<Partido>(`${environment.apiCatalog}/partidos/${id}/cancelar`, {});
  }

  /** POST /api/catalog/partidos/{id}/localidades → 201 */
  crearLocalidad(partidoId: number, datos: LocalidadInput): Observable<Localidad> {
    return this.http.post<Localidad>(
      `${environment.apiCatalog}/partidos/${partidoId}/localidades`,
      datos,
    );
  }

  /** PUT /api/catalog/localidades/{id} → 200 · 409 si cupo < vendidos. */
  editarLocalidad(id: number, datos: LocalidadInput): Observable<Localidad> {
    return this.http.put<Localidad>(`${environment.apiCatalog}/localidades/${id}`, datos);
  }

  /**
   * POST /api/sales/reservas/cancelar-por-partido/{partidoId} → {canceladas}.
   * Se llama SIEMPRE DESPUÉS de cancelar el partido en catalog (ver AdminPartidos).
   */
  cancelarReservasPorPartido(partidoId: number): Observable<CanceladasResponse> {
    return this.http.post<CanceladasResponse>(
      `${environment.apiSales}/reservas/cancelar-por-partido/${partidoId}`,
      {},
    );
  }

  /** GET /api/sales/reportes/ventas (partidoId opcional) → filas agregadas. */
  reporteVentas(partidoId?: number): Observable<FilaReporteVenta[]> {
    const params = partidoId != null ? new HttpParams().set('partidoId', String(partidoId)) : new HttpParams();
    return this.http.get<FilaReporteVenta[]>(`${environment.apiSales}/reportes/ventas`, { params });
  }
}
