package com.valle.boleteria.catalog.seguridad;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.valle.boleteria.common.error.ApiError;
import com.valle.boleteria.common.security.JwtAuthenticationFilter;
import com.valle.boleteria.common.security.JwtService;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * Seguridad stateless del catálogo: sin sesiones, el JWT emitido por auth define el
 * rol. Reglas:
 * <ul>
 *   <li>GET /api/catalog/** → público (Angular y compras consultan sin token). Se
 *       permite SOLO el método GET: los POST/PUT sobre las mismas rutas no son
 *       públicos.</li>
 *   <li>Escrituras del catálogo (POST/PUT partidos y PUT localidades) → solo ADMIN.</li>
 *   <li>/reservar y /liberar → cualquier usuario autenticado (sales usa su JWT).</li>
 *   <li>Documentación OpenAPI → pública (demo).</li>
 * </ul>
 * Un token ausente/inválido en rutas protegidas responde 401 con el mismo cuerpo
 * ApiError que el GlobalExceptionHandler.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, JwtService jwtService, ObjectMapper mapper)
            throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .cors(c -> c.configurationSource(corsConfigurationSource()))
                .authorizeHttpRequests(auth -> auth
                        // Consultas públicas: solo GET (el inventario se lee, no se toca)
                        .requestMatchers(HttpMethod.GET,
                                "/api/catalog/partidos/**", "/api/catalog/localidades/**").permitAll()
                        .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                        // Administración del catálogo: solo ADMIN
                        .requestMatchers(HttpMethod.POST, "/api/catalog/partidos/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/api/catalog/partidos/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/api/catalog/localidades/**").hasRole("ADMIN")
                        // Inventario interno: SOLO el servicio de ventas (token de servicio).
                        // Un USER con su propio JWT ya NO puede reservar ni liberar cupo.
                        .requestMatchers(HttpMethod.POST,
                                "/api/catalog/localidades/*/reservar",
                                "/api/catalog/localidades/*/liberar").hasRole("SERVICE")
                        // Cualquier otra mutación del catálogo es de ADMIN (catch-all:
                        // un endpoint nuevo no queda abierto a cualquier autenticado).
                        .requestMatchers("/api/catalog/**").hasRole("ADMIN")
                        .anyRequest().authenticated())
                .exceptionHandling(e -> e
                        .authenticationEntryPoint((req, res, ex) -> {
                            res.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                            res.setContentType(MediaType.APPLICATION_JSON_VALUE);
                            mapper.writeValue(res.getOutputStream(), ApiError.of(401, "Unauthorized",
                                    "No autenticado: envíe un token JWT válido", req.getRequestURI()));
                        })
                        // Un token válido sin rol suficiente → 403 con el MISMO cuerpo ApiError
                        .accessDeniedHandler((req, res, ex) -> {
                            res.setStatus(HttpServletResponse.SC_FORBIDDEN);
                            res.setContentType(MediaType.APPLICATION_JSON_VALUE);
                            mapper.writeValue(res.getOutputStream(), ApiError.of(403, "Forbidden",
                                    "No tiene permisos para este recurso", req.getRequestURI()));
                        }))
                .addFilterBefore(new JwtAuthenticationFilter(jwtService),
                        UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    /** CORS de demo: todos los orígenes/métodos/cabeceras (front en otro puerto). */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOriginPatterns(List.of("*"));
        config.setAllowedMethods(List.of("*"));
        config.setAllowedHeaders(List.of("*"));
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
