package com.valle.boleteria.sales.seguridad;

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
 * Seguridad stateless del servicio de ventas: sin sesiones; el JWT emitido por
 * auth (validado con el secreto compartido) define identidad y rol.
 * Reglas: el reporte de ventas y la cancelación masiva por partido son solo
 * ADMIN; el resto de /api/sales/** es para cualquier usuario autenticado (la
 * pertenencia de cada reserva se valida en el servicio). Token ausente/inválido
 * → 401 con el cuerpo ApiError uniforme del equipo.
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
                        .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                        // Reporte de ventas: requisito de la prueba, solo ADMIN
                        .requestMatchers(HttpMethod.GET, "/api/sales/reportes/**").hasRole("ADMIN")
                        // Cancelación masiva por partido: la dispara el panel admin
                        .requestMatchers(HttpMethod.POST, "/api/sales/reservas/cancelar-por-partido/*")
                                .hasRole("ADMIN")
                        .anyRequest().authenticated())
                .exceptionHandling(e -> e.authenticationEntryPoint((req, res, ex) -> {
                    res.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                    res.setContentType(MediaType.APPLICATION_JSON_VALUE);
                    mapper.writeValue(res.getOutputStream(), ApiError.of(401, "Unauthorized",
                            "No autenticado: envíe un token JWT válido", req.getRequestURI()));
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
