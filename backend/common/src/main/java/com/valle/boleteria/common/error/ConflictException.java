package com.valle.boleteria.common.error;

/** 409: conflicto de estado (ej. sin cupo disponible, código duplicado). */
public class ConflictException extends RuntimeException {

    public ConflictException(String mensaje) {
        super(mensaje);
    }
}
