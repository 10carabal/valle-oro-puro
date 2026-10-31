package com.valle.boleteria.sales.entidad;

/**
 * Ciclo de vida de una reserva dentro de la saga de compra:
 *
 * <pre>
 * PENDING ──pagar──▶ CONFIRMED ──cancelar──▶ CANCELLED (libera cupo en catalog)
 *    │
 *    └──sin cupo en catalog──▶ REJECTED (trazabilidad del intento fallido)
 * </pre>
 *
 * PENDING y CONFIRMED son cancelables porque ambos CONSUMEN cupo en catalog.
 * REJECTED nunca llegó a tomar cupo, por eso no es cancelable.
 */
public enum EstadoReserva {
    PENDING,
    CONFIRMED,
    REJECTED,
    CANCELLED
}
