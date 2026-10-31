package com.valle.boleteria.sales.controlador;

import com.valle.boleteria.sales.dto.VentaReporteFila;
import com.valle.boleteria.sales.servicio.VentaService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Reporte de ventas (solo ADMIN, validado en SecurityConfig).
 * Implementado con la Native Query {@code ReservaRepository#reporteVentas}
 * (SUM + GROUP BY sobre el snapshot desnormalizado, requisito de la prueba).
 */
@RestController
@RequestMapping("/api/sales/reportes")
public class ReporteController {

    private final VentaService servicio;

    public ReporteController(VentaService servicio) {
        this.servicio = servicio;
    }

    /** Cantidad vendida y total recaudado por partido y localidad (?partidoId= opcional). */
    @GetMapping("/ventas")
    public List<VentaReporteFila> ventas(@RequestParam(required = false) Long partidoId) {
        return servicio.reporteVentas(partidoId);
    }
}
