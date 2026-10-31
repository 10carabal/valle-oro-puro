package com.valle.boleteria.auth.dto;

/** Respuesta de GET /api/auth/yo: identidad del usuario autenticado según el token. */
public record YoResponse(String email, String rol) {
}
