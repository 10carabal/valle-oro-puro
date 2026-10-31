package com.valle.boleteria.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Body de POST /api/auth/registro. */
public record RegistroRequest(
        @NotBlank(message = "el email es obligatorio")
        @Email(message = "el email no tiene un formato válido")
        String email,

        @NotBlank(message = "la contraseña es obligatoria")
        @Size(min = 6, message = "la contraseña debe tener al menos 6 caracteres")
        String password,

        @NotBlank(message = "el nombre es obligatorio")
        String nombre) {
}
