package com.valle.boleteria.auth.dto;

/** Respuesta de POST /api/auth/login (200): el token JWT para las demás llamadas. */
public record LoginResponse(String token, String email, String rol, String nombre) {
}
