import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { LocalidadInfo, Partido } from '../models/partido';

/**
 * Consumo público de catalog-service (:8082) vía proxy.
 * Los filtros fecha (YYYY-MM-DD) y estado se pasan como query params opcionales.
 */
@Injectable({ providedIn: 'root' })
export class CatalogService {
  private readonly http = inject(HttpClient);

  getPartidos(fecha?: string, estado?: string): Observable<Partido[]> {
    let params = new HttpParams();
    if (fecha) {
      params = params.set('fecha', fecha);
    }
    if (estado) {
      params = params.set('estado', estado);
    }
    return this.http.get<Partido[]>(`${environment.apiCatalog}/partidos`, { params });
  }

  getPartido(id: number | string): Observable<Partido> {
    return this.http.get<Partido>(`${environment.apiCatalog}/partidos/${id}`);
  }

  getLocalidad(id: number | string): Observable<LocalidadInfo> {
    return this.http.get<LocalidadInfo>(`${environment.apiCatalog}/localidades/${id}`);
  }
}
