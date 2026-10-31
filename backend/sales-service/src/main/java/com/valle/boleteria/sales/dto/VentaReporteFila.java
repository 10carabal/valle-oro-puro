package com.valle.boleteria.sales.dto;

import java.math.BigDecimal;

/**
 * Proyección por interfaz de la Native Query del reporte de ventas
 * ({@code ReservaRepository#reporteVentas}). Los alias de columna de la query
 * ("partidoId", "rival", ...) mapean 1:1 con estas propiedades y Jackson
 * serializa directamente el contrato JSON del endpoint admin.
 */
public interface VentaReporteFila {

    Long getPartidoId();

    String getRival();

    Long getLocalidadId();

    /** SUM(cantidad) de las reservas CONFIRMED del grupo. */
    Long getCantidadVendida();

    /** SUM(total) de las reservas CONFIRMED del grupo. */
    BigDecimal getTotalRecaudado();
}
