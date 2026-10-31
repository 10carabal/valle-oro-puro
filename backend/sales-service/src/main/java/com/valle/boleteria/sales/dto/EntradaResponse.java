package com.valle.boleteria.sales.dto;

import com.valle.boleteria.sales.entidad.Entrada;

import java.time.LocalDateTime;

/** Entrada emitida con su código único (UUID). */
public record EntradaResponse(String codigo, LocalDateTime emitidaEn) {

    public static EntradaResponse de(Entrada entrada) {
        return new EntradaResponse(entrada.getCodigoUnico(), entrada.getEmitidaEn());
    }
}
