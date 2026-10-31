package com.valle.boleteria.common.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * Emisión y validación de JWT (HS256). El MISMO secreto (variable JWT_SECRET) lo comparten
 * auth (emisor) y catalog/sales (validadores): cualquier servicio puede verificar el token
 * sin llamar a auth.
 */
@Component
public class JwtService {

    public static final String ATRIBUTO_ROL = "rol";
    private static final long VALIDEZ_HORAS = 8;

    private final SecretKey clave;

    public JwtService(@Value("${jwt.secret}") String secreto) {
        if (secreto == null || secreto.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException("jwt.secret debe tener al menos 32 bytes");
        }
        this.clave = Keys.hmacShaKeyFor(secreto.getBytes(StandardCharsets.UTF_8));
    }

    /** Genera un token con subject = email y claim personalizado "rol". */
    public String generarToken(String email, String rol) {
        Date ahora = new Date();
        return Jwts.builder()
                .subject(email)
                .claim(ATRIBUTO_ROL, rol)
                .issuedAt(ahora)
                .expiration(new Date(ahora.getTime() + VALIDEZ_HORAS * 3_600_000L))
                .signWith(clave)
                .compact();
    }

    /**
     * Valida firma y expiración.
     *
     * @throws JwtException si el token es inválido o expiró
     */
    public Claims validar(String token) {
        return Jwts.parser().verifyWith(clave).build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public boolean esValido(String token) {
        try {
            validar(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    public String extraerEmail(String token) {
        return validar(token).getSubject();
    }

    public String extraerRol(String token) {
        return validar(token).get(ATRIBUTO_ROL, String.class);
    }
}
