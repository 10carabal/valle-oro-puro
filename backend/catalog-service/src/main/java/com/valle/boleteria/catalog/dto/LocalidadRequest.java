package com.valle.boleteria.catalog.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/** Body de POST /partidos/{id}/localidades y PUT /localidades/{id} (solo ADMIN). */
public record LocalidadRequest(
        @NotBlank(message = "nombre es obligatorio") String nombre,
        @NotNull(message = "precio es obligatorio") BigDecimal precio,
        @Min(value = 1, message = "cupo debe ser >= 1") int cupo) {
}
