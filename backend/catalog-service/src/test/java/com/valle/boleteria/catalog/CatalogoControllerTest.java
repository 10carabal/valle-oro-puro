package com.valle.boleteria.catalog;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.valle.boleteria.catalog.dto.LocalidadResponse;
import com.valle.boleteria.catalog.dto.PartidoResponse;
import com.valle.boleteria.catalog.entidad.Partido;
import com.valle.boleteria.catalog.repositorio.PartidoRepository;
import com.valle.boleteria.catalog.servicio.CatalogoService;
import com.valle.boleteria.common.security.JwtService;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDate;
import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Contratos HTTP completos del catálogo sobre PostgreSQL real:
 * formato de los GET públicos, CRUD de ADMIN (201/200), seguridad
 * (401 sin token, 403 con rol USER) y el inventario /reservar /liberar (409).
 */
@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
class CatalogoControllerTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private PartidoRepository partidoRepository;

    @Autowired
    private CatalogoService servicio;

    // ------------------------------------------------------- consultas públicas

    @Test
    void listadoPublicoConFiltroDeFechaIncluyeLocalidades() throws Exception {
        // Se toma la fecha REAL del partido semilla (evita dependencia de TZ)
        Partido caimanes = partidoPorRival("Caimanes de Barranquilla");

        mockMvc.perform(get("/api/catalog/partidos").param("fecha", caimanes.getFecha().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size()").value(1))
                .andExpect(jsonPath("$[0].id").value(caimanes.getId()))
                .andExpect(jsonPath("$[0].rival").value("Caimanes de Barranquilla"))
                .andExpect(jsonPath("$[0].escenario").value("Coliseo El Pueblo, Cali"))
                .andExpect(jsonPath("$[0].estado").value("PROGRAMADO"))
                .andExpect(jsonPath("$[0].localidades.size()").value(3))
                // la VIP del primer partido es la "última entrada" del caso crítico
                .andExpect(jsonPath("$[0].localidades[?(@.nombre == 'VIP')].cupo", Matchers.hasItem(1)))
                .andExpect(jsonPath("$[0].localidades[?(@.nombre == 'General')].disponibles", Matchers.hasItem(100)));

        // Filtro por estado, público y sin token (los rivales semilla, orden independiente)
        mockMvc.perform(get("/api/catalog/partidos").param("estado", "PROGRAMADO"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].rival", Matchers.hasItems(
                        "Caimanes de Barranquilla", "Soacha Búfalos", "Pilares de Yumbo")));
    }

    @Test
    void detallePublicoY404SiNoExiste() throws Exception {
        Partido caimanes = partidoPorRival("Caimanes de Barranquilla");

        mockMvc.perform(get("/api/catalog/partidos/" + caimanes.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rival").value("Caimanes de Barranquilla"))
                .andExpect(jsonPath("$.localidades").isArray());

        mockMvc.perform(get("/api/catalog/partidos/999999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void localidadPublicaDevuelveLocalidadInfo() throws Exception {
        // El detalle del partido ya trae las localidades mapeadas (JOIN FETCH en servicio)
        PartidoResponse caimanes = servicio.obtener(partidoPorRival("Caimanes de Barranquilla").getId());
        long vipId = caimanes.localidades().stream()
                .filter(l -> l.nombre().equals("VIP"))
                .findFirst().orElseThrow().id();

        mockMvc.perform(get("/api/catalog/localidades/" + vipId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(vipId))
                .andExpect(jsonPath("$.partidoId").value(caimanes.id()))
                .andExpect(jsonPath("$.rival").value("Caimanes de Barranquilla"))
                .andExpect(jsonPath("$.nombre").value("VIP"))
                .andExpect(jsonPath("$.precio").value(60000))
                .andExpect(jsonPath("$.cupo").value(1))
                .andExpect(jsonPath("$.vendidos").value(0));
    }

    // ------------------------------------------------------------ seguridad

    @Test
    void escrituraDePartido401SinToken403ConUser201ConAdmin() throws Exception {
        String body = """
                {"rival":"Rival Nuevo","fecha":"%s","hora":"19:30:00","escenario":"Coliseo Nuevo"}
                """.formatted(LocalDate.now().plusDays(10));

        mockMvc.perform(post("/api/catalog/partidos")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));

        mockMvc.perform(post("/api/catalog/partidos")
                        .header("Authorization", "Bearer " + token("user@prueba", "USER"))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));

        mockMvc.perform(post("/api/catalog/partidos")
                        .header("Authorization", "Bearer " + token("admin@prueba", "ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.rival").value("Rival Nuevo"))
                .andExpect(jsonPath("$.hora").value("19:30:00"))
                .andExpect(jsonPath("$.estado").value("PROGRAMADO"))
                .andExpect(jsonPath("$.localidades").isEmpty());
    }

    // ----------------------------------------------------------- CRUD admin

    @Test
    void adminActualizaCancelaYAgregaLocalidades() throws Exception {
        long id = crearPartidoAdmin("Rival Gestión", LocalDate.now().plusDays(12));

        // Actualizar (incluye cambio de estado)
        mockMvc.perform(put("/api/catalog/partidos/" + id)
                        .header("Authorization", admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"rival":"Rival Cambiado","fecha":"%s","hora":"20:00:00",
                                 "escenario":"Otro Coliseo","estado":"JUGADO"}
                                """.formatted(LocalDate.now().plusDays(13))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rival").value("Rival Cambiado"))
                .andExpect(jsonPath("$.estado").value("JUGADO"));

        // Cancelar = lógico: estado CANCELADO y la fila NO se borra
        mockMvc.perform(put("/api/catalog/partidos/" + id + "/cancelar").header("Authorization", admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("CANCELADO"));
        assertThat(partidoRepository.findById(id))
                .as("la cancelación es lógica: conserva la fila para reportes históricos")
                .isPresent();

        // Agregar localidad al partido
        MvcResult creada = mockMvc.perform(post("/api/catalog/partidos/" + id + "/localidades")
                        .header("Authorization", admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"Balcón\",\"precio\":45000,\"cupo\":5}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.nombre").value("Balcón"))
                .andExpect(jsonPath("$.cupo").value(5))
                .andExpect(jsonPath("$.vendidos").value(0))
                .andExpect(jsonPath("$.disponibles").value(5))
                .andReturn();
        long localidadId = objectMapper.readTree(creada.getResponse().getContentAsString())
                .get("id").asLong();

        // La localidad creada aparece en el detalle del partido
        mockMvc.perform(get("/api/catalog/partidos/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.localidades.size()").value(1))
                .andExpect(jsonPath("$.localidades[0].id").value(localidadId));
    }

    // ------------------------------------------------------ inventario cupo

    @Test
    void reservarLiberaCupoYResponde409CuandoNoHay() throws Exception {
        long partidoId = crearPartidoAdmin("Rival Cupos", LocalDate.now().plusDays(15));
        long localidadId = crearLocalidadAdmin(partidoId, "Platea", 10000, 2);

        // Cualquier rol autenticado puede reservar (así actúa sales-service)
        mockMvc.perform(post("/api/catalog/localidades/" + localidadId + "/reservar")
                        .header("Authorization", header("user@prueba", "USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"cantidad\":1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.localidadId").value(localidadId))
                .andExpect(jsonPath("$.cupo").value(2))
                .andExpect(jsonPath("$.vendidos").value(1))
                .andExpect(jsonPath("$.disponibles").value(1));

        // Segunda reserva agota el cupo
        mockMvc.perform(post("/api/catalog/localidades/" + localidadId + "/reservar")
                        .header("Authorization", header("user@prueba", "USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"cantidad\":1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.vendidos").value(2))
                .andExpect(jsonPath("$.disponibles").value(0));

        // Tercera → 409 con el cuerpo ApiError estándar
        mockMvc.perform(post("/api/catalog/localidades/" + localidadId + "/reservar")
                        .header("Authorization", header("user@prueba", "USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"cantidad\":1}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.message")
                        .value("No hay cupo disponible para esta localidad"));

        // Liberar con más cantidad de la vendida: piso en 0 (nunca negativo)
        mockMvc.perform(post("/api/catalog/localidades/" + localidadId + "/liberar")
                        .header("Authorization", header("user@prueba", "USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"cantidad\":5}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.vendidos").value(0))
                .andExpect(jsonPath("$.disponibles").value(2));
    }

    @Test
    void actualizarLocalidadNoBajaElCupoDeLoYaVendido() throws Exception {
        long partidoId = crearPartidoAdmin("Rival Ocupado", LocalDate.now().plusDays(16));
        long localidadId = crearLocalidadAdmin(partidoId, "Ocupada", 10000, 5);

        // Se venden las 5 entradas
        mockMvc.perform(post("/api/catalog/localidades/" + localidadId + "/reservar")
                        .header("Authorization", admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"cantidad\":5}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.vendidos").value(5));

        // cupo=4 < vendidos=5 → 409 (regla de integridad del inventario)
        mockMvc.perform(put("/api/catalog/localidades/" + localidadId)
                        .header("Authorization", admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"Ocupada\",\"precio\":12000,\"cupo\":4}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.message", Matchers.containsString("cupo menor a lo ya vendido")));

        // cupo == vendidos sí se permite
        mockMvc.perform(put("/api/catalog/localidades/" + localidadId)
                        .header("Authorization", admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"Ocupada\",\"precio\":12000,\"cupo\":5}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.precio").value(12000))
                .andExpect(jsonPath("$.disponibles").value(0));
    }

    @Test
    void reservarSinTokenEs401() throws Exception {
        long partidoId = crearPartidoAdmin("Rival Sin Token", LocalDate.now().plusDays(17));
        long localidadId = crearLocalidadAdmin(partidoId, "Segura", 10000, 3);

        mockMvc.perform(post("/api/catalog/localidades/" + localidadId + "/reservar")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"cantidad\":1}"))
                .andExpect(status().isUnauthorized());

        // La localidad quedó intacta
        mockMvc.perform(get("/api/catalog/localidades/" + localidadId))
                .andExpect(jsonPath("$.vendidos").value(0));
    }

    // ----------------------------------------------------------------- utils

    private String token(String email, String rol) {
        return jwtService.generarToken(email, rol);
    }

    private String header(String email, String rol) {
        return "Bearer " + token(email, rol);
    }

    private String admin() {
        return header("admin@prueba", "ADMIN");
    }

    private Partido partidoPorRival(String rival) {
        return partidoRepository.findAll().stream()
                .filter(p -> p.getRival().equals(rival))
                .findFirst().orElseThrow();
    }

    private long crearPartidoAdmin(String rival, LocalDate fecha) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/catalog/partidos")
                        .header("Authorization", admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"rival":"%s","fecha":"%s","hora":"18:00:00","escenario":"Coliseo Test"}
                                """.formatted(rival, fecha)))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
    }

    private long crearLocalidadAdmin(long partidoId, String nombre, double precio, int cupo) throws Exception {
        LocalidadResponse creada = servicio.crearLocalidad(partidoId,
                new com.valle.boleteria.catalog.dto.LocalidadRequest(
                        nombre, java.math.BigDecimal.valueOf(precio), cupo));
        return creada.id();
    }
}
