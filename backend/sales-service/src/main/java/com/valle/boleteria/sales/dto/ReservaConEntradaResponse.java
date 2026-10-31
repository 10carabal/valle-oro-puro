package com.valle.boleteria.sales.dto;

import com.valle.boleteria.sales.entidad.Reserva;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Fila de GET /api/sales/reservas: la reserva del usuario con su entrada
 * (null mientras no se pague).
 */
public record ReservaConEntradaResponse(Long id, Long partidoId, String rival, Long localidadId,
                                       int cantidad, BigDecimal total, String estado,
                                       LocalDateTime creadoEn, EntradaResponse entrada) {

    public static ReservaConEntradaResponse de(Reserva reserva) {
        EntradaResponse entrada = reserva.getEntrada() != null
                ? EntradaResponse.de(reserva.getEntrada())
                : null;
        return new ReservaConEntradaResponse(reserva.getId(), reserva.getPartidoId(), reserva.getRivalSnapshot(),
                reserva.getLocalidadId(), reserva.getCantidad(), reserva.getTotal(),
                reserva.getEstado().name(), reserva.getCreadoEn(), entrada);
    }
}
