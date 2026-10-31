package com.valle.boleteria.catalog.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.time.LocalTime;

/** Body de POST /api/catalog/partidos (solo ADMIN). El estado nace PROGRAMADO. */
public record PartidoRequest(
        @NotBlank(message = "rival es obligatorio") String rival,
        @NotNull(message = "fecha es obligatoria") LocalDate fecha,
        @NotNull(message = "hora es obligatoria") LocalTime hora,
        @NotBlank(message = "escenario es obligatorio") String escenario) {
}
