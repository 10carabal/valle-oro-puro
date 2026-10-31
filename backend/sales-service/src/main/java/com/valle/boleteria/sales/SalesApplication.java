package com.valle.boleteria.sales;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Servicio de ventas (puerto 8083): reservas, pago simulado, entradas con código
 * único y reporte de ventas (Native Query).
 *
 * Arquitectura: el INVENTARIO (cupo/vendidos) vive en catalog-service (dueño de la
 * localidad); sales ORQUESTA la saga de compra llamando a catalog con RestClient y
 * propagando el JWT del usuario. Se escanea también {@code com.valle.boleteria.common}
 * para reutilizar el manejo centralizado de errores (@RestControllerAdvice) y la
 * seguridad JWT.
 */
@SpringBootApplication(scanBasePackages = {"com.valle.boleteria.sales", "com.valle.boleteria.common"})
public class SalesApplication {

    public static void main(String[] args) {
        SpringApplication.run(SalesApplication.class, args);
    }
}
