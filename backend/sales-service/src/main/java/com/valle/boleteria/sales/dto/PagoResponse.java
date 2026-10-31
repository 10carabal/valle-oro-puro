package com.valle.boleteria.sales.dto;

/** Resultado del pago simulado: reserva CONFIRMED + entrada con código único. */
public record PagoResponse(Long reservaId, String estado, EntradaResponse entrada) {
}
