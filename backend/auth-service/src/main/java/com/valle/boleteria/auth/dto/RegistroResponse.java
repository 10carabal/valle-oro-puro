package com.valle.boleteria.auth.dto;

/** Respuesta de POST /api/auth/registro (201). */
public record RegistroResponse(String email, String nombre, String rol) {
}
