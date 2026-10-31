package com.valle.boleteria.sales.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/** Body de POST /api/sales/reservas. */
public record ReservaRequest(@NotNull(message = "localidadId es obligatorio") Long localidadId,
                             @Min(value = 1, message = "cantidad debe ser >= 1") int cantidad) {
}
