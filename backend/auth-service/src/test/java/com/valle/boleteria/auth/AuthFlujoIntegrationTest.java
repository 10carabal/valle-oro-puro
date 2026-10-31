package com.valle.boleteria.auth;

import com.valle.boleteria.auth.dto.LoginRequest;
import com.valle.boleteria.auth.dto.LoginResponse;
import com.valle.boleteria.auth.dto.RegistroRequest;
import com.valle.boleteria.auth.dto.RegistroResponse;
import com.valle.boleteria.common.error.ApiError;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pruebas de integración del flujo completo de autenticación contra PostgreSQL real
 * (Testcontainers, imagen postgres:16-alpine). El esquema y el seed los aplica Flyway.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class AuthFlujoIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private TestRestTemplate rest;

    private static final ParameterizedTypeReference<LoginResponse> TIPO_LOGIN =
            new ParameterizedTypeReference<>() {
            };
    private static final ParameterizedTypeReference<ApiError> TIPO_ERROR =
            new ParameterizedTypeReference<>() {
            };

    private HttpEntity<Object> json(Object body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return new HttpEntity<>(body, headers);
    }

    private HttpEntity<Object> jsonConToken(Object body, String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(token);
        return new HttpEntity<>(body, headers);
    }

    @Test
    void flujoCompleto_registrar_login_y_consultarYo() {
        // 1. Registro → 201 con email, nombre y rol USER
        ResponseEntity<RegistroResponse> registro = rest.exchange("/api/auth/registro", HttpMethod.POST,
                json(new RegistroRequest("nuevo@valle.test", "secreto123", "Usuario Nuevo")),
                RegistroResponse.class);
        assertThat(registro.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(registro.getBody()).isNotNull();
        assertThat(registro.getBody().email()).isEqualTo("nuevo@valle.test");
        assertThat(registro.getBody().nombre()).isEqualTo("Usuario Nuevo");
        assertThat(registro.getBody().rol()).isEqualTo("USER");

        // 2. Login → 200 con token
        ResponseEntity<LoginResponse> login = rest.exchange("/api/auth/login", HttpMethod.POST,
                json(new LoginRequest("nuevo@valle.test", "secreto123")), TIPO_LOGIN);
        assertThat(login.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(login.getBody()).isNotNull();
        assertThat(login.getBody().token()).isNotBlank();
        assertThat(login.getBody().email()).isEqualTo("nuevo@valle.test");
        assertThat(login.getBody().rol()).isEqualTo("USER");
        assertThat(login.getBody().nombre()).isEqualTo("Usuario Nuevo");

        // 3. GET /api/auth/yo con Bearer → 200 y email correcto
        ResponseEntity<String> yo = rest.exchange("/api/auth/yo", HttpMethod.GET,
                jsonConToken(null, login.getBody().token()), String.class);
        assertThat(yo.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(yo.getBody()).contains("\"email\":\"nuevo@valle.test\"");
        assertThat(yo.getBody()).contains("\"rol\":\"USER\"");
    }

    @Test
    void emailDuplicado_devuelve409ConCuerpoApiError() {
        ResponseEntity<RegistroResponse> primero = rest.exchange("/api/auth/registro", HttpMethod.POST,
                json(new RegistroRequest("duplicado@valle.test", "secreto123", "Primero")),
                RegistroResponse.class);
        assertThat(primero.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        ResponseEntity<ApiError> segundo = rest.exchange("/api/auth/registro", HttpMethod.POST,
                json(new RegistroRequest("duplicado@valle.test", "otra123456", "Segundo")), TIPO_ERROR);
        assertThat(segundo.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(segundo.getBody()).isNotNull();
        assertThat(segundo.getBody().status()).isEqualTo(409);
        assertThat(segundo.getBody().error()).isEqualTo("Conflict");
        assertThat(segundo.getBody().message()).contains("ya está registrado");
        assertThat(segundo.getBody().path()).isEqualTo("/api/auth/registro");
    }

    @Test
    void loginConPasswordIncorrecta_devuelve401() {
        ResponseEntity<ApiError> respuesta = rest.exchange("/api/auth/login", HttpMethod.POST,
                json(new LoginRequest("admin@valle.test", "password-mala")), TIPO_ERROR);
        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(respuesta.getBody()).isNotNull();
        assertThat(respuesta.getBody().status()).isEqualTo(401);
        assertThat(respuesta.getBody().message()).isEqualTo("Credenciales inválidas");
    }

    @Test
    void yoSinToken_devuelve401() {
        ResponseEntity<ApiError> respuesta = rest.exchange("/api/auth/yo", HttpMethod.GET,
                null, TIPO_ERROR);
        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(respuesta.getBody()).isNotNull();
        assertThat(respuesta.getBody().status()).isEqualTo(401);
    }

    @Test
    void seed_adminValleTest_haceLoginConRolAdmin() {
        ResponseEntity<LoginResponse> login = rest.exchange("/api/auth/login", HttpMethod.POST,
                json(new LoginRequest("admin@valle.test", "admin123")), TIPO_LOGIN);
        assertThat(login.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(login.getBody()).isNotNull();
        assertThat(login.getBody().email()).isEqualTo("admin@valle.test");
        assertThat(login.getBody().rol()).isEqualTo("ADMIN");
        assertThat(login.getBody().token()).isNotBlank();
    }

    @Test
    void registroConBodyInvalido_devuelve400() {
        ResponseEntity<ApiError> respuesta = rest.exchange("/api/auth/registro", HttpMethod.POST,
                json(new RegistroRequest("no-es-email", "123", "")), TIPO_ERROR);
        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(respuesta.getBody()).isNotNull();
        assertThat(respuesta.getBody().status()).isEqualTo(400);
        assertThat(respuesta.getBody().message()).contains("email");
    }
}
