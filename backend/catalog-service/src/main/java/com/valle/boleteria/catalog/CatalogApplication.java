package com.valle.boleteria.catalog;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Servicio de catálogo (puerto 8082): partidos, localidades, precios e inventario
 * de entradas. Es el DUEÑO del cupo: aquí vive el mecanismo anti-sobreventa del
 * caso crítico (UPDATE condicional atómico en {@code LocalidadRepository}).
 * Se escanea también {@code com.valle.boleteria.common} para reutilizar el
 * manejo centralizado de errores (@RestControllerAdvice) y la seguridad JWT.
 */
@SpringBootApplication(scanBasePackages = {"com.valle.boleteria.catalog", "com.valle.boleteria.common"})
public class CatalogApplication {

    public static void main(String[] args) {
        SpringApplication.run(CatalogApplication.class, args);
    }
}
