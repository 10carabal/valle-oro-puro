package com.valle.boleteria.catalog.dto;

import com.valle.boleteria.catalog.entidad.EstadoPartido;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * Body de PUT /api/catalog/partidos/{id} (solo ADMIN). Si {@code estado} viene
 * nulo se conserva el estado actual del partido.
 */
public record PartidoActualizarRequest(
        @NotBlank(message = "rival es obligatorio") String rival,
        @NotNull(message = "fecha es obligatoria") LocalDate fecha,
        LocalTime hora,
        String escenario,
        EstadoPartido estado) {
}
