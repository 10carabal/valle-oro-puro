package com.valle.boleteria.catalog.dto;

import java.math.BigDecimal;

/** Localidad dentro de un partido, con la disponibilidad calculada. */
public record LocalidadResponse(
        Long id,
        String nombre,
        BigDecimal precio,
        int cupo,
        int vendidos,
        int disponibles) {
}
