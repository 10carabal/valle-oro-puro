package com.valle.boleteria.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/** Body de POST /api/auth/login. */
public record LoginRequest(
        @NotBlank(message = "el email es obligatorio")
        @Email(message = "el email no tiene un formato válido")
        String email,

        @NotBlank(message = "la contraseña es obligatoria")
        String password) {
}
