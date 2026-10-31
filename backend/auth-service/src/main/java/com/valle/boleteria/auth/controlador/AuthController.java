package com.valle.boleteria.auth.controlador;

import com.valle.boleteria.auth.dto.LoginRequest;
import com.valle.boleteria.auth.dto.LoginResponse;
import com.valle.boleteria.auth.dto.RegistroRequest;
import com.valle.boleteria.auth.dto.RegistroResponse;
import com.valle.boleteria.auth.dto.YoResponse;
import com.valle.boleteria.auth.servicio.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoints de autenticación:
 * POST /api/auth/registro (público) · POST /api/auth/login (público) · GET /api/auth/yo (token).
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/registro")
    public ResponseEntity<RegistroResponse> registrar(@Valid @RequestBody RegistroRequest solicitud) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.registrar(solicitud));
    }

    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest solicitud) {
        return authService.login(solicitud);
    }

    /** Identidad del usuario autenticada por el filtro JWT (SecurityContext). */
    @GetMapping("/yo")
    public YoResponse yo(Authentication authentication) {
        String email = authentication.getName();
        String rol = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .map(a -> a.replace("ROLE_", ""))
                .findFirst()
                .orElse("USER");
        return new YoResponse(email, rol);
    }
}
