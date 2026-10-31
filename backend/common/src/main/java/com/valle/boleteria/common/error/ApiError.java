package com.valle.boleteria.common.error;

import java.time.Instant;

/** Cuerpo de error estandarizado para TODA la API (manejo centralizado de errores). */
public record ApiError(Instant timestamp, int status, String error, String message, String path) {

    public static ApiError of(int status, String error, String message, String path) {
        return new ApiError(Instant.now(), status, error, message, path);
    }
}
