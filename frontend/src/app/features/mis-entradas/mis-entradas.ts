import { HttpErrorResponse } from '@angular/common/http';
import { Component, OnInit, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { mensajeApi } from '../../core/models/api-error';
import { EstadoReserva, ReservaResumen, SalesService } from '../../core/services/sales.service';
import { ChipEstado } from '../../shared/chip-estado';
import { CopPipe } from '../../shared/pipes/cop';

/**
 * Tarea 4.4 — "Mis entradas": reservas del usuario (GET /api/sales/reservas)
 * con su entrada emitida. Puede PAGAR una reserva PENDING y CANCELAR una
 * PENDING/CONFIRMED (confirmación nativa + recarga).
 * Solo se usan campos del contrato de sales: NO hay fecha/hora del partido aquí.
 */
@Component({
  selector: 'app-mis-entradas',
  imports: [RouterLink, ChipEstado, CopPipe],
  templateUrl: './mis-entradas.html',
  styleUrl: './mis-entradas.scss',
})
export class MisEntradas implements OnInit {
  private readonly ventas = inject(SalesService);

  readonly reservas = signal<ReservaResumen[]>([]);
  readonly cargando = signal(true);
  readonly error = signal<string | null>(null);
  /** id de la reserva que está en proceso (pagar/cancelar) para deshabilitar botones. */
  readonly procesando = signal<number | null>(null);

  ngOnInit(): void {
    this.cargar();
  }

  cargar(): void {
    this.cargando.set(true);
    this.error.set(null);
    this.ventas.misReservas().subscribe({
      next: (reservas) => {
        this.reservas.set(reservas);
        this.cargando.set(false);
      },
      error: (err: HttpErrorResponse) => {
        this.error.set(mensajeApi(err));
        this.cargando.set(false);
      },
    });
  }

  /** Etiqueta clara del estado de la reserva (no confundir con el estado del partido). */
  etiquetaEstado(estado: EstadoReserva): string {
    switch (estado) {
      case 'PENDING':
        return 'Pendiente de pago';
      case 'CONFIRMED':
        return 'Confirmada · entrada emitida';
      case 'REJECTED':
        return 'Rechazada (sin cupo)';
      case 'CANCELLED':
        return 'Cancelada';
    }
  }

  puedePagar(reserva: ReservaResumen): boolean {
    return reserva.estado === 'PENDING';
  }

  puedeCancelar(reserva: ReservaResumen): boolean {
    return reserva.estado === 'PENDING' || reserva.estado === 'CONFIRMED';
  }

  /** Pago simulado de una reserva PENDING → CONFIRMED + código (recarga la lista). */
  pagar(reserva: ReservaResumen): void {
    this.procesando.set(reserva.id);
    this.error.set(null);
    this.ventas.pagar(reserva.id).subscribe({
      next: () => {
        this.procesando.set(null);
        this.cargar();
      },
      error: (err: HttpErrorResponse) => {
        this.procesando.set(null);
        this.error.set(mensajeApi(err));
      },
    });
  }

  /** Cancela y libera cupo; pide confirmación antes (y recarga tras el éxito). */
  cancelar(reserva: ReservaResumen): void {
    const confirmado = window.confirm(
      `¿Cancelar la reserva #${reserva.id} de ${reserva.rival}? Se liberará el cupo.`,
    );
    if (!confirmado) {
      return;
    }

    this.procesando.set(reserva.id);
    this.error.set(null);
    this.ventas.cancelar(reserva.id).subscribe({
      next: () => {
        this.procesando.set(null);
        this.cargar();
      },
      error: (err: HttpErrorResponse) => {
        this.procesando.set(null);
        this.error.set(mensajeApi(err));
      },
    });
  }

  trackPorId(_index: number, reserva: ReservaResumen): number {
    return reserva.id;
  }
}
