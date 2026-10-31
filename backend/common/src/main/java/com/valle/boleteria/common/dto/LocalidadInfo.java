package com.valle.boleteria.common.dto;

/**
 * Datos de localidad que sales necesita para construir la reserva
 * (precio para el total y datos del partido para el snapshot del reporte).
 */
public record LocalidadInfo(Long id, Long partidoId, String rival, String nombre,
                            double precio, int cupo, int vendidos) {
}
