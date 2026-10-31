package com.valle.boleteria.auth;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Servicio de autenticación (puerto 8081): registro, login y emisión de JWT.
 * Se escanea también {@code com.valle.boleteria.common} para reutilizar el
 * manejo centralizado de errores (@RestControllerAdvice) y el filtro JWT.
 */
@SpringBootApplication(scanBasePackages = {"com.valle.boleteria.auth", "com.valle.boleteria.common"})
public class AuthApplication {

    public static void main(String[] args) {
        SpringApplication.run(AuthApplication.class, args);
    }
}
