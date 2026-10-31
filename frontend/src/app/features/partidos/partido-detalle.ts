import { HttpErrorResponse } from '@angular/common/http';
import { Component, OnInit, inject, signal } from '@angular/core';
import { FormControl, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { mensajeApi } from '../../core/models/api-error';
import { Localidad, Partido } from '../../core/models/partido';
import { AuthService } from '../../core/services/auth.service';
import { CatalogService } from '../../core/services/catalog.service';
import { EntradaEmitida, ReservaCreada, SalesService } from '../../core/services/sales.service';
import { ChipEstado } from '../../shared/chip-estado';
import { CopPipe } from '../../shared/pipes/cop';

/** Pasos del flujo de compra (tarea 4.4); `null` = panel cerrado. */
type PasoCompra = 1 | 2 | 3;

/**
 * Detalle de un partido: datos + localidades con precio y disponibilidad,
 * y FLUJO DE COMPRA (sales-service):
 *   paso 1 cantidad → POST /reservas (PENDING) → paso 2 pago simulado →
 *   POST /reservas/{id}/pagar → paso 3 con el código de la entrada.
 * El 409 de la reserva (sin cupo) se muestra amigable y refresca la disponibilidad.
 */
@Component({
  selector: 'app-partido-detalle',
  imports: [ReactiveFormsModule, RouterLink, ChipEstado, CopPipe],
  templateUrl: './partido-detalle.html',
  styleUrl: './partido-detalle.scss',
})
export class PartidoDetalle implements OnInit {
  private readonly catalogo = inject(CatalogService);
  private readonly ventas = inject(SalesService);
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly ruta = inject(ActivatedRoute);

  /** id del partido actual (guardado del paramMap; evita depender de snapshot). */
  private idPartido: string | null = null;

  readonly partido = signal<Partido | null>(null);
  readonly cargando = signal(true);

  // --- flujo de compra ---
  readonly paso = signal<PasoCompra | null>(null);
  readonly localidadCompra = signal<Localidad | null>(null);
  readonly reserva = signal<ReservaCreada | null>(null);
  readonly entrada = signal<EntradaEmitida | null>(null);
  readonly cargandoCompra = signal(false);
  readonly errorCompra = signal<string | null>(null);
  readonly cargandoPago = signal(false);
  readonly errorPago = signal<string | null>(null);

  /** Cantidad a comprar: requerida, mín 1 y máx = disponibles de la localidad. */
  readonly formCantidad = new FormControl(1, {
    nonNullable: true,
    validators: [Validators.required, Validators.min(1)],
  });

  /**
   * Total en vivo del resumen: precio unitario × cantidad.
   * Es un método (no `computed`) porque un FormControl no es un Signal:
   * se reevalúa en cada detección de cambios, incluida la del input.
   */
  total(): number {
    const precio = this.localidadCompra()?.precio ?? 0;
    const cantidad = Number(this.formCantidad.value) || 0;
    return precio * cantidad;
  }

  ngOnInit(): void {
    this.ruta.paramMap.subscribe((params) => {
      const id = params.get('id');
      this.idPartido = id;
      if (id) {
        this.cargar(id);
      } else {
        this.partido.set(null);
        this.cargando.set(false);
      }
    });
  }

  trackPorId(_index: number, localidad: { id: number }): number {
    return localidad.id;
  }

  /**
   * Abre el panel de compra para una localidad.
   * Sin sesión → /login?returnUrl=… (mismo patrón que el guard/login).
   */
  comprar(localidad: Localidad): void {
    if (!this.auth.estaLogueado()) {
      void this.router.navigate(['/login'], { queryParams: { returnUrl: this.router.url } });
      return;
    }

    this.localidadCompra.set(localidad);
    this.reserva.set(null);
    this.entrada.set(null);
    this.errorCompra.set(null);
    this.errorPago.set(null);
    this.formCantidad.setValue(1);
    this.formCantidad.setValidators([
      Validators.required,
      Validators.min(1),
      Validators.max(localidad.disponibles),
    ]);
    this.formCantidad.updateValueAndValidity();
    this.paso.set(1);
  }

  /** Cierra el panel de compra y limpia los mensajes. */
  cerrarPanel(): void {
    this.paso.set(null);
    this.localidadCompra.set(null);
    this.reserva.set(null);
    this.entrada.set(null);
    this.errorCompra.set(null);
    this.errorPago.set(null);
  }

  /** Errores de la cantidad (solo tras tocar/escribir el campo). */
  errorCantidad(): string | null {
    const control = this.formCantidad;
    if (!control.touched && !control.dirty) {
      return null;
    }
    if (control.hasError('required')) {
      return 'La cantidad es obligatoria.';
    }
    if (control.hasError('min')) {
      return 'Mínimo 1 entrada.';
    }
    if (control.hasError('max')) {
      return `Solo quedan ${this.localidadCompra()?.disponibles ?? 0} entradas en esta localidad.`;
    }
    return null;
  }

  /** Paso 1 → 2: crea la reserva (PENDING) en sales-service. */
  reservar(): void {
    const localidad = this.localidadCompra();
    if (!localidad || this.paso() !== 1) {
      return;
    }
    if (this.formCantidad.invalid) {
      this.formCantidad.markAllAsTouched();
      return;
    }

    this.cargandoCompra.set(true);
    this.errorCompra.set(null);
    this.ventas.reservar(localidad.id, this.formCantidad.getRawValue()).subscribe({
      next: (reserva) => {
        this.reserva.set(reserva);
        this.cargandoCompra.set(false);
        this.paso.set(2);
        // catalog ya descontó el cupo: reflejamos la disponibilidad real en la tabla.
        this.refrescarDisponibilidad();
      },
      error: (err: HttpErrorResponse) => {
        this.cargandoCompra.set(false);
        if (err.status === 409) {
          // Sin cupo: mensaje propio + refresco de disponibilidad (panel abierto).
          this.errorCompra.set('No quedan entradas disponibles en esta localidad');
          this.refrescarDisponibilidad();
        } else {
          this.errorCompra.set(mensajeApi(err));
        }
      },
    });
  }

  /** Paso 2 → 3: pago simulado → CONFIRMED + código de entrada. */
  pagar(): void {
    const reserva = this.reserva();
    if (!reserva || this.paso() !== 2) {
      return;
    }

    this.cargandoPago.set(true);
    this.errorPago.set(null);
    this.ventas.pagar(reserva.reservaId).subscribe({
      next: (pago) => {
        this.entrada.set(pago.entrada);
        this.cargandoPago.set(false);
        this.paso.set(3);
      },
      error: (err: HttpErrorResponse) => {
        // 409 (p.ej. la reserva ya no está PENDING): mostramos el message del ApiError.
        this.cargandoPago.set(false);
        this.errorPago.set(mensajeApi(err));
      },
    });
  }

  private cargar(id: string): void {
    this.cargando.set(true);
    this.catalogo.getPartido(id).subscribe({
      next: (partido) => {
        this.partido.set(partido);
        this.cargando.set(false);
      },
      error: () => {
        this.partido.set(null);
        this.cargando.set(false);
      },
    });
  }

  /** Recarga el detalle EN SEGUNDO PLANO para no cerrar el panel ni perder el mensaje. */
  private refrescarDisponibilidad(): void {
    const id = this.idPartido;
    if (!id) {
      return;
    }
    this.catalogo.getPartido(id).subscribe({
      next: (partido) => this.partido.set(partido),
      error: () => {
        // si el refresco falla, conservamos los datos actuales
      },
    });
  }
}
