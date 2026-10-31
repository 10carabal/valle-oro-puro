package com.valle.boleteria.common.dto;

import jakarta.validation.constraints.Min;

/**
 * Contrato sales → catalog: solicitar cupo para una localidad.
 * El UPDATE atómico de inventario vive en catalog (dueño de la localidad).
 */
public record ReservarCupoRequest(@Min(value = 1, message = "cantidad debe ser >= 1") int cantidad) {
}
