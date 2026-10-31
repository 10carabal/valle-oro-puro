import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';

/**
 * Estados de una RESERVA (sales-service): PENDING → CONFIRMED al pagar.
 * Ojo: NO es el estado del partido (PROGRAMADO/JUGADO/CANCELADO de catalog).
 */
export type EstadoReserva = 'PENDING' | 'CONFIRMED' | 'REJECTED' | 'CANCELLED';

/** Entrada emitida al pagar (PagoResponse.entrada y ReservaConEntradaResponse.entrada). */
export interface EntradaEmitida {
  codigo: string;
  emitidaEn: string;
}

/** ReservaResponse: 201 de POST /reservas y 200 de POST /reservas/{id}/cancelar. */
export interface ReservaCreada {
  reservaId: number;
  estado: EstadoReserva;
  localidadId: number;
  cantidad: number;
  total: number;
  partidoId: number;
  rival: string;
}

/** PagoResponse: 200 de POST /reservas/{id}/pagar (pago simulado → CONFIRMED + entrada). */
export interface PagoReserva {
  reservaId: number;
  estado: EstadoReserva;
  entrada: EntradaEmitida;
}

/** ReservaConEntradaResponse: fila de GET /reservas (la reserva del usuario con su entrada). */
export interface ReservaResumen {
  id: number;
  partidoId: number;
  rival: string;
  localidadId: number;
  cantidad: number;
  total: number;
  estado: EstadoReserva;
  creadoEn: string;
  entrada: EntradaEmitida | null;
}

/**
 * Escrituras/lecturas del FLUJO DE COMPRA sobre sales-service (:8083).
 * El interceptor JWT adjunta el Bearer (todo requiere sesión); el backend es
 * quien valida la propiedad de la reserva y el cupo (404/409/403).
 */
@Injectable({ providedIn: 'root' })
export class SalesService {
  private readonly http = inject(HttpClient);

  /** POST /api/sales/reservas → 201 ReservaCreada · 404 localidad inexistente · 409 sin cupo. */
  reservar(localidadId: number, cantidad: number): Observable<ReservaCreada> {
    return this.http.post<ReservaCreada>(`${environment.apiSales}/reservas`, { localidadId, cantidad });
  }

  /** POST /api/sales/reservas/{id}/pagar → 200 con entrada · 409 si no está PENDING. */
  pagar(reservaId: number): Observable<PagoReserva> {
    return this.http.post<PagoReserva>(`${environment.apiSales}/reservas/${reservaId}/pagar`, {});
  }

  /** POST /api/sales/reservas/{id}/cancelar → 200 CANCELLED (libera cupo) · 409 si ya cerrada. */
  cancelar(reservaId: number): Observable<ReservaCreada> {
    return this.http.post<ReservaCreada>(`${environment.apiSales}/reservas/${reservaId}/cancelar`, {});
  }

  /** GET /api/sales/reservas[?estado=] → reservas del usuario con su entrada (null si no pagó). */
  misReservas(estado?: EstadoReserva): Observable<ReservaResumen[]> {
    let params = new HttpParams();
    if (estado) {
      params = params.set('estado', estado);
    }
    return this.http.get<ReservaResumen[]>(`${environment.apiSales}/reservas`, { params });
  }
}
