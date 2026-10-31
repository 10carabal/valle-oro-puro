package com.valle.boleteria.auth;

import com.valle.boleteria.common.security.JwtService;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Prueba unitaria del JwtService real (sin Docker ni contexto Spring):
 * el token generado se valida y lee bien; un token firmado con otro secreto no.
 */
class JwtServiceTest {

    private static final String SECRETO_PRUEBA = "secreto-de-prueba-de-32-bytes-minimo!!"; // >= 32 bytes

    private final JwtService jwtService = new JwtService(SECRETO_PRUEBA);

    @Test
    void tokenGenerado_seValidaYContieneEmailYRol() {
        String token = jwtService.generarToken("prueba@valle.test", "ADMIN");

        assertThat(jwtService.esValido(token)).isTrue();

        Claims claims = jwtService.validar(token);
        assertThat(claims.getSubject()).isEqualTo("prueba@valle.test");
        assertThat(claims.get("rol", String.class)).isEqualTo("ADMIN");
        assertThat(claims.getExpiration()).isAfter(claims.getIssuedAt());

        assertThat(jwtService.extraerEmail(token)).isEqualTo("prueba@valle.test");
        assertThat(jwtService.extraerRol(token)).isEqualTo("ADMIN");
    }

    @Test
    void tokenFirmadoConOtroSecreto_esInvalido() {
        JwtService otroServicio = new JwtService("otro-secreto-completamente-diferente-32b!");
        String tokenAjeno = otroServicio.generarToken("prueba@valle.test", "USER");

        assertThat(jwtService.esValido(tokenAjeno)).isFalse();
        assertThatThrownBy(() -> jwtService.validar(tokenAjeno))
                .isInstanceOf(JwtException.class);
    }

    @Test
    void secretoMenorA32Bytes_fallaAlCrearElServicio() {
        assertThatThrownBy(() -> new JwtService("corto"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("32 bytes");
    }
}
