package com.valle.boleteria.catalog.dto;

import com.valle.boleteria.catalog.entidad.EstadoPartido;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/** Salida pública de partidos: el listado y el detalle comparten este formato. */
public record PartidoResponse(
        Long id,
        String rival,
        LocalDate fecha,
        LocalTime hora,
        String escenario,
        EstadoPartido estado,
        List<LocalidadResponse> localidades) {
}
