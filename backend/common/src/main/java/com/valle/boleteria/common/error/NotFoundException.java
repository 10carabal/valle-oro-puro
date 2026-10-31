package com.valle.boleteria.common.error;

/** 404: recurso inexistente. Lanzada por los servicios y traducida por {@link GlobalExceptionHandler}. */
public class NotFoundException extends RuntimeException {

    public NotFoundException(String mensaje) {
        super(mensaje);
    }
}
