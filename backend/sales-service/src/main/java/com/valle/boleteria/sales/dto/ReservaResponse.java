package com.valle.boleteria.sales.dto;

import com.valle.boleteria.sales.entidad.Reserva;

import java.math.BigDecimal;

/** Confirmación de creación/cancelación de reserva (201/200). */
public record ReservaResponse(Long reservaId, String estado, Long localidadId,
                              int cantidad, BigDecimal total, Long partidoId, String rival) {

    public static ReservaResponse de(Reserva reserva) {
        return new ReservaResponse(reserva.getId(), reserva.getEstado().name(), reserva.getLocalidadId(),
                reserva.getCantidad(), reserva.getTotal(), reserva.getPartidoId(), reserva.getRivalSnapshot());
    }
}
