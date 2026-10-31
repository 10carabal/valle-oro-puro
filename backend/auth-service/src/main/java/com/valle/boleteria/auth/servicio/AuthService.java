package com.valle.boleteria.auth.servicio;

import com.valle.boleteria.auth.dto.LoginRequest;
import com.valle.boleteria.auth.dto.LoginResponse;
import com.valle.boleteria.auth.dto.RegistroRequest;
import com.valle.boleteria.auth.dto.RegistroResponse;
import com.valle.boleteria.auth.entidad.Rol;
import com.valle.boleteria.auth.entidad.Usuario;
import com.valle.boleteria.auth.repositorio.UsuarioRepository;
import com.valle.boleteria.common.error.ConflictException;
import com.valle.boleteria.common.security.JwtService;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Lógica de registro y login. Las contraseñas se guardan solo como hash BCrypt;
 * el login exitoso emite un JWT HS256 (sub = email, claim "rol").
 */
@Service
public class AuthService {

    private final UsuarioRepository usuarios;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(UsuarioRepository usuarios, PasswordEncoder passwordEncoder, JwtService jwtService) {
        this.usuarios = usuarios;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    /** Registra un usuario nuevo con rol USER. Email repetido → 409. */
    @Transactional
    public RegistroResponse registrar(RegistroRequest solicitud) {
        String email = solicitud.email().toLowerCase();
        if (usuarios.existsByEmail(email)) {
            throw new ConflictException("El email " + email + " ya está registrado");
        }
        Usuario usuario = new Usuario(email, passwordEncoder.encode(solicitud.password()),
                solicitud.nombre(), Rol.USER);
        usuarios.save(usuario);
        return new RegistroResponse(usuario.getEmail(), usuario.getNombre(), usuario.getRol().name());
    }

    /** Valida credenciales y emite el token. Credenciales mal → BadCredentialsException (401). */
    @Transactional(readOnly = true)
    public LoginResponse login(LoginRequest solicitud) {
        String email = solicitud.email().toLowerCase();
        Usuario usuario = usuarios.findByEmail(email)
                .filter(u -> passwordEncoder.matches(solicitud.password(), u.getPasswordHash()))
                .orElseThrow(() -> new BadCredentialsException("Credenciales inválidas"));

        String token = jwtService.generarToken(usuario.getEmail(), usuario.getRol().name());
        return new LoginResponse(token, usuario.getEmail(), usuario.getRol().name(), usuario.getNombre());
    }
}
