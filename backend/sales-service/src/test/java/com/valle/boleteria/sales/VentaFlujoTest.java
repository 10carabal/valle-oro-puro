package com.valle.boleteria.sales;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.valle.boleteria.common.security.JwtService;
import com.valle.boleteria.sales.entidad.Entrada;
import com.valle.boleteria.sales.entidad.EstadoReserva;
import com.valle.boleteria.sales.entidad.Reserva;
import com.valle.boleteria.sales.repositorio.EntradaRepository;
import com.valle.boleteria.sales.repositorio.ReservaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.client.match.MockRestRequestMatchers;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.client.RestClient;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.endsWith;
import static org.springframework.test.web.client.ExpectedCount.manyTimes;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Flujo de compra END-TO-END sobre PostgreSQL real (Testcontainers) con el
 * catálogo MOCKEADO vía MockRestServiceServer (sin WireMock, sin red).
 *
 * NOTA DE DISEÑO DE TESTS: la garantía REAL de no sobreventa vive en
 * catalog-service (su UPDATE condicional atómico + su test multihilo); aquí se
 * prueba la SAGA de sales: orquestación, propagación de JWT, estados, payload
 * exacto, compensación y permisos. El mock del catálogo replica la semántica de
 * cupo con un AtomicInteger (CAS) para poder simular también el caso de "sin
 * cupo" de forma determinista.
 */
@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
class VentaFlujoTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    /** Longitudes fijas para que los matchers de URL sean disjuntos. */
    private static final String LOCALIDAD = "http://catalogo-test/api/catalog/localidades/7";
    private static final long LOCALIDAD_ID = 7;
    private static final long PARTIDO_ID = 100;
    private static final String RIVAL = "Rival de Prueba";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private ReservaRepository reservaRepository;

    @Autowired
    private EntradaRepository entradaRepository;

    private MockRestServiceServer catalogo;

    @BeforeEach
    void prepararCatalogoMock() {
        catalogo = CatalogoMockConfig.catalogoMock;
        catalogo.reset(); // aislamiento entre tests (el server vive en el contexto)
    }

    // ------------------------------------------------------ 1. flujo completo

    @Test
    void flujoDeCompraCompletoReservaPagaYListaLaEntrada() throws Exception {
        String token = token("cliente@prueba", "USER");
        AtomicReference<String> bearerVistoEnCatalog = new AtomicReference<>();

        catalogo.expect(manyTimes(), requestTo(endsWith("/localidades/7")))
                .andExpect(method(HttpMethod.GET))
                .andRespond(req -> {
                    // Propagación de JWT: catalog debe recibir el Bearer del USUARIO
                    bearerVistoEnCatalog.set(req.getHeaders().getFirst(HttpHeaders.AUTHORIZATION));
                    return withSuccess(localidadInfoJson(LOCALIDAD_ID, PARTIDO_ID, 20000.0, 10, 0), MediaType.APPLICATION_JSON)
                            .createResponse(req);
                });
        catalogo.expect(manyTimes(), requestTo(endsWith("/reservar")))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess(cupoJson(LOCALIDAD_ID, 10, 2, 8), MediaType.APPLICATION_JSON));

        // Reserva → 201 PENDING con el total correcto (20000 x 2)
        MvcResult creada = mockMvc.perform(post("/api/sales/reservas")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"localidadId\":7,\"cantidad\":2}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.estado").value("PENDING"))
                .andExpect(jsonPath("$.localidadId").value(LOCALIDAD_ID))
                .andExpect(jsonPath("$.cantidad").value(2))
                .andExpect(jsonPath("$.total").value(40000))
                .andExpect(jsonPath("$.partidoId").value(PARTIDO_ID))
                .andExpect(jsonPath("$.rival").value(RIVAL))
                .andReturn();
        long reservaId = leerId(creada, "reservaId");

        assertThat(bearerVistoEnCatalog.get())
                .as("sales reenvía la cabecera Authorization del usuario a catalog")
                .isEqualTo("Bearer " + token);

        // Pago simulado → CONFIRMED + entrada con código UUID no nulo
        MvcResult pagada = mockMvc.perform(post("/api/sales/reservas/" + reservaId + "/pagar")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reservaId").value(reservaId))
                .andExpect(jsonPath("$.estado").value("CONFIRMED"))
                .andExpect(jsonPath("$.entrada.codigo").isNotEmpty())
                .andExpect(jsonPath("$.entrada.emitidaEn").isNotEmpty())
                .andReturn();
        String codigo = objectMapper.readTree(codigoJson(pagada))
                .get("entrada").get("codigo").asText();
        assertThatCode(() -> UUID.fromString(codigo))
                .as("el código de la entrada es un UUID")
                .doesNotThrowAnyException();

        // GET /reservas lista la reserva CON su entrada
        mockMvc.perform(get("/api/sales/reservas").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size()").value(1))
                .andExpect(jsonPath("$[0].id").value(reservaId))
                .andExpect(jsonPath("$[0].estado").value("CONFIRMED"))
                .andExpect(jsonPath("$[0].total").value(40000))
                .andExpect(jsonPath("$[0].entrada.codigo").value(codigo));

        // Y la reserva + entrada quedaron en la MISMA transacción de BD
        assertThat(reservaRepository.findById(reservaId)).hasValueSatisfying(r ->
                assertThat(r.getEstado()).isEqualTo(EstadoReserva.CONFIRMED));
        assertThat(entradaRepository.findAll())
                .as("exactamente UNA entrada para esta reserva")
                .filteredOn(entrada -> entrada.getReserva().getId().equals(reservaId))
                .hasSize(1);
    }

    // ------------------------------------------------------------- 2. sin cupo

    @Test
    void sinCupoResponde409YLaReservaQuedaRejected() throws Exception {
        String token = token("sin-cupo@prueba", "USER");

        catalogo.expect(requestTo(endsWith("/localidades/7")))
                .andRespond(withSuccess(localidadInfoJson(LOCALIDAD_ID, PARTIDO_ID, 20000.0, 10, 10), MediaType.APPLICATION_JSON));
        // El inventario es de catalog: el 409 viene de ALLÁ (en el test real lo
        // produce su UPDATE atómico; aquí el mock responde 409 directo).
        catalogo.expect(manyTimes(), requestTo(endsWith("/reservar")))
                .andRespond(withStatus(HttpStatus.CONFLICT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("""
                                {"status":409,"error":"Conflict","message":"No hay cupo disponible para esta localidad"}
                                """));

        mockMvc.perform(post("/api/sales/reservas")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"localidadId\":7,\"cantidad\":1}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.message").value("No hay cupo disponible"));

        // Diseño elegido: el intento fallido se persiste REJECTED (trazabilidad
        // de la saga) en una transacción independiente, y JAMÁS aparece CONFIRMED.
        // (Los tests comparten BD: se filtra por el usuario de ESTE test.)
        var reservas = reservaRepository.findAll().stream()
                .filter(r -> r.getUsuarioEmail().equals("sin-cupo@prueba"))
                .toList();
        assertThat(reservas).hasSize(1);
        assertThat(reservas.get(0).getEstado()).isEqualTo(EstadoReserva.REJECTED);
        assertThat(entradaRepository.findAll())
                .as("sin confirmación no hay entrada para la reserva rechazada")
                .noneMatch(entrada -> entrada.getReserva().getId().equals(reservas.get(0).getId()));
    }

    // ------------------------------------------------------- 3. compensación

    @Test
    void cancelarReservaPendienteLiberaElCupoEnCatalog() throws Exception {
        String token = token("cancelador@prueba", "USER");
        AtomicInteger liberaciones = new AtomicInteger();

        catalogo.expect(requestTo(endsWith("/localidades/7")))
                .andRespond(withSuccess(localidadInfoJson(LOCALIDAD_ID, PARTIDO_ID, 20000.0, 10, 0), MediaType.APPLICATION_JSON));
        catalogo.expect(manyTimes(), requestTo(endsWith("/reservar")))
                .andRespond(withSuccess(cupoJson(LOCALIDAD_ID, 10, 2, 8), MediaType.APPLICATION_JSON));
        catalogo.expect(requestTo(endsWith("/liberar")))
                .andExpect(method(HttpMethod.POST))
                // OJO: jsonPath del lado del REQUEST viene de MockRestRequestMatchers
                // (en Spring 6.2 devuelve JsonPathRequestMatchers: hay que encadenar .value)
                .andExpect(MockRestRequestMatchers.jsonPath("$.cantidad").value(2))
                .andRespond(req -> {
                    liberaciones.incrementAndGet();
                    return withSuccess(cupoJson(LOCALIDAD_ID, 10, 0, 10), MediaType.APPLICATION_JSON).createResponse(req);
                });

        long reservaId = crearReserva(token, 2);

        mockMvc.perform(post("/api/sales/reservas/" + reservaId + "/cancelar")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("CANCELLED"));

        assertThat(reservaRepository.findById(reservaId)).hasValueSatisfying(r ->
                assertThat(r.getEstado()).isEqualTo(EstadoReserva.CANCELLED));
        assertThat(liberaciones.get())
                .as("la cancelación dispara EXACTAMENTE una liberación de cupo en catalog")
                .isEqualTo(1);

        // Cancelar dos veces → 409
        mockMvc.perform(post("/api/sales/reservas/" + reservaId + "/cancelar")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));
    }

    // ---------------------------------------------------------- 4. permisos

    @Test
    void permisosDePagroCancelacionYReporte() throws Exception {
        String dueno = token("dueno@prueba", "USER");
        String otro = token("otro@prueba", "USER");
        String admin = token("admin@prueba", "ADMIN");

        catalogo.expect(manyTimes(), requestTo(endsWith("/localidades/7")))
                .andRespond(withSuccess(localidadInfoJson(LOCALIDAD_ID, PARTIDO_ID, 20000.0, 10, 0), MediaType.APPLICATION_JSON));
        catalogo.expect(manyTimes(), requestTo(endsWith("/reservar")))
                .andRespond(withSuccess(cupoJson(LOCALIDAD_ID, 10, 1, 9), MediaType.APPLICATION_JSON));
        // El admin al final cancela la reserva del dueño → compensación /liberar
        catalogo.expect(manyTimes(), requestTo(endsWith("/liberar")))
                .andRespond(withSuccess(cupoJson(LOCALIDAD_ID, 10, 0, 10), MediaType.APPLICATION_JSON));

        long reservaId = crearReserva(dueno, 1);

        // Pagar/cancelar la reserva de OTRO usuario → 403 (AccessDeniedException)
        mockMvc.perform(post("/api/sales/reservas/" + reservaId + "/pagar")
                        .header("Authorization", "Bearer " + otro))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));
        mockMvc.perform(post("/api/sales/reservas/" + reservaId + "/cancelar")
                        .header("Authorization", "Bearer " + otro))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));

        // El reporte (y la cancelación masiva) son solo ADMIN
        mockMvc.perform(get("/api/sales/reportes/ventas").header("Authorization", "Bearer " + dueno))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/sales/reservas/cancelar-por-partido/" + PARTIDO_ID)
                        .header("Authorization", "Bearer " + dueno))
                .andExpect(status().isForbidden());

        // Sin token → 401 con el cuerpo ApiError uniforme
        mockMvc.perform(post("/api/sales/reservas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"localidadId\":7,\"cantidad\":1}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));

        // Token inválido → 401
        mockMvc.perform(get("/api/sales/reservas").header("Authorization", "Bearer no-valido"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));

        // La reserva dueña quedó intacta (PENDING) tras todos los intentos ajenos
        assertThat(reservaRepository.findById(reservaId)).hasValueSatisfying(r ->
                assertThat(r.getEstado()).isEqualTo(EstadoReserva.PENDING));
        // El admin SÍ puede ver y cancelar la reserva de otro
        mockMvc.perform(get("/api/sales/reportes/ventas").header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/sales/reservas/" + reservaId + "/cancelar")
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("CANCELLED"));
    }

    // ---------------------------------------------- 5. cancelación por partido

    @Test
    void cancelarPorPartidoCancelaTodasLasReservasVivasDelPartido() throws Exception {
        String user = token("fan@prueba", "USER");
        String admin = token("admin@prueba", "ADMIN");
        AtomicInteger liberaciones = new AtomicInteger();

        // Localidad/partido DEDICADOS de este test: así {canceladas:2} es exacto
        // aunque otros tests compartan la BD con reservas del partido 100.
        long localidadId = 8;
        long partidoId = 200;

        catalogo.expect(manyTimes(), requestTo(endsWith("/localidades/8")))
                .andRespond(withSuccess(localidadInfoJson(localidadId, partidoId, 20000.0, 10, 0), MediaType.APPLICATION_JSON));
        catalogo.expect(manyTimes(), requestTo(endsWith("/reservar")))
                .andRespond(withSuccess(cupoJson(localidadId, 10, 2, 8), MediaType.APPLICATION_JSON));
        catalogo.expect(manyTimes(), requestTo(endsWith("/liberar")))
                .andExpect(MockRestRequestMatchers.jsonPath("$.cantidad").value(1))
                .andRespond(req -> {
                    liberaciones.incrementAndGet();
                    return withSuccess(cupoJson(localidadId, 10, 1, 9), MediaType.APPLICATION_JSON).createResponse(req);
                });

        long reservaPendiente = crearReserva(user, localidadId, 1);
        long reservaPagada = crearReserva(user, localidadId, 1);
        mockMvc.perform(post("/api/sales/reservas/" + reservaPagada + "/pagar")
                        .header("Authorization", "Bearer " + user))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/sales/reservas/cancelar-por-partido/" + partidoId)
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.canceladas").value(2));

        assertThat(reservaRepository.findById(reservaPendiente)).hasValueSatisfying(r ->
                assertThat(r.getEstado()).isEqualTo(EstadoReserva.CANCELLED));
        assertThat(reservaRepository.findById(reservaPagada)).hasValueSatisfying(r ->
                assertThat(r.getEstado()).isEqualTo(EstadoReserva.CANCELLED));
        assertThat(liberaciones.get())
                .as("cada reserva cancelada devuelve SU cupo a catalog (PENDING y CONFIRMED)")
                .isEqualTo(2);
    }

    // ----------------------------------------------- 6. Native Query (reporte)

    @Test
    void reporteNativoAgrupaSoloLasConfirmadas() throws Exception {
        String admin = token("admin@prueba", "ADMIN");

        // Los tests comparten BD: se limpia para que el assert sea EXACTO
        // (entradas primero: tienen FK hacia reservas).
        entradaRepository.deleteAll();
        reservaRepository.deleteAll();

        // Seed directo en BD (sin pasar por HTTP): 2 CONFIRMED de la MISMA
        // localidad + 1 CANCELLED + 1 REJECTED que NO deben contar.
        guardarReserva("a@prueba", PARTIDO_ID, RIVAL, LOCALIDAD_ID, 2, 10000.0, EstadoReserva.CONFIRMED);
        guardarReserva("b@prueba", PARTIDO_ID, RIVAL, LOCALIDAD_ID, 3, 20000.0, EstadoReserva.CONFIRMED);
        guardarReserva("c@prueba", PARTIDO_ID, RIVAL, LOCALIDAD_ID, 5, 5000.0, EstadoReserva.CANCELLED);
        guardarReserva("d@prueba", PARTIDO_ID, RIVAL, 11, 7, 1000.0, EstadoReserva.REJECTED);

        // Exactamente: 2+3=5 entradas y 2*10000 + 3*20000 = 80000 recaudados
        mockMvc.perform(get("/api/sales/reportes/ventas").header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size()").value(1))
                .andExpect(jsonPath("$[0].partidoId").value(PARTIDO_ID))
                .andExpect(jsonPath("$[0].rival").value(RIVAL))
                .andExpect(jsonPath("$[0].localidadId").value(LOCALIDAD_ID))
                .andExpect(jsonPath("$[0].cantidadVendida").value(5))
                .andExpect(jsonPath("$[0].totalRecaudado").value(80000));

        // Filtro opcional ?partidoId= (misma fila; y otro partido → vacío)
        mockMvc.perform(get("/api/sales/reportes/ventas").param("partidoId", String.valueOf(PARTIDO_ID))
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size()").value(1))
                .andExpect(jsonPath("$[0].cantidadVendida").value(5))
                .andExpect(jsonPath("$[0].totalRecaudado").value(80000));

        mockMvc.perform(get("/api/sales/reportes/ventas").param("partidoId", "9999")
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size()").value(0));
    }

    // --------------------------------------------------- 8. código único UUID

    @Test
    void dosPagosNoProducenCodigosRepetidos() throws Exception {
        String uno = token("uno@prueba", "USER");
        String dos = token("dos@prueba", "USER");

        catalogo.expect(manyTimes(), requestTo(endsWith("/localidades/7")))
                .andRespond(withSuccess(localidadInfoJson(LOCALIDAD_ID, PARTIDO_ID, 20000.0, 10, 0), MediaType.APPLICATION_JSON));
        catalogo.expect(manyTimes(), requestTo(endsWith("/reservar")))
                .andRespond(withSuccess(cupoJson(LOCALIDAD_ID, 10, 2, 8), MediaType.APPLICATION_JSON));

        String codigoUno = pagarYObtenerCodigo(crearReserva(uno, 1), uno);
        String codigoDos = pagarYObtenerCodigo(crearReserva(dos, 1), dos);

        assertThat(codigoUno)
                .as("dos pagos nunca comparten código")
                .isNotEqualTo(codigoDos);
        assertThatCode(() -> UUID.fromString(codigoUno)).doesNotThrowAnyException();
        assertThatCode(() -> UUID.fromString(codigoDos)).doesNotThrowAnyException();

        // Y la restricción UNIQUE de BD es la garantía FÍSICA (no solo la app):
        // intentar reusar un código viola el UNIQUE de sales.entradas.
        Reserva reservaDuplicada = guardarReserva("e@prueba", PARTIDO_ID, RIVAL, LOCALIDAD_ID,
                1, 20000.0, EstadoReserva.CONFIRMED);
        assertThatThrownBy(() -> entradaRepository.saveAndFlush(
                new Entrada(reservaDuplicada, codigoUno)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    // ------------------------------------------------------------------ util

    private String token(String email, String rol) {
        return jwtService.generarToken(email, rol);
    }

    private long crearReserva(String tokenUsuario, int cantidad) throws Exception {
        return crearReserva(tokenUsuario, LOCALIDAD_ID, cantidad);
    }

    private long crearReserva(String tokenUsuario, long localidadId, int cantidad) throws Exception {
        MvcResult creada = mockMvc.perform(post("/api/sales/reservas")
                        .header("Authorization", "Bearer " + tokenUsuario)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"localidadId\":" + localidadId + ",\"cantidad\":" + cantidad + "}"))
                .andExpect(status().isCreated())
                .andReturn();
        return leerId(creada, "reservaId");
    }

    private String pagarYObtenerCodigo(long reservaId, String tokenUsuario) throws Exception {
        MvcResult pagada = mockMvc.perform(post("/api/sales/reservas/" + reservaId + "/pagar")
                        .header("Authorization", "Bearer " + tokenUsuario))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(codigoJson(pagada))
                .get("entrada").get("codigo").asText();
    }

    private Reserva guardarReserva(String email, long partidoId, String rival, long localidadId,
                                   int cantidad, double precio, EstadoReserva estado) {
        Reserva reserva = new Reserva(partidoId, rival, localidadId, email, cantidad,
                BigDecimal.valueOf(precio), estado);
        if (estado == EstadoReserva.CONFIRMED) {
            reserva.confirmar();
        }
        return reservaRepository.save(reserva);
    }

    private long leerId(MvcResult resultado, String campo) throws Exception {
        return objectMapper.readTree(codigoJson(resultado)).get(campo).asLong();
    }

    private String codigoJson(MvcResult resultado) throws Exception {
        return resultado.getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    private static String localidadInfoJson(long localidadId, long partidoId, double precio, int cupo, int vendidos) {
        return """
                {"id":%d,"partidoId":%d,"rival":"Rival de Prueba","nombre":"VIP",
                 "precio":%s,"cupo":%d,"vendidos":%d}
                """.formatted(localidadId, partidoId, precio, cupo, vendidos);
    }

    private static String cupoJson(long localidadId, int cupo, int vendidos, int disponibles) {
        return """
                {"localidadId":%d,"cupo":%d,"vendidos":%d,"disponibles":%d}
                """.formatted(localidadId, cupo, vendidos, disponibles);
    }

    /**
     * El RestClient de la app se construye con esta MockRestServiceServer en su
     * builder: bindTo(RestClient.Builder) instala la request factory mock ANTES
     * de crear el cliente, así el servicio usa el mock sin cambiar su código.
     */
    @TestConfiguration(proxyBeanMethods = false)
    static class CatalogoMockConfig {

        static MockRestServiceServer catalogoMock;

        @Bean
        @Primary
        RestClient restClientCatalogoMock(RestClient.Builder builder) {
            catalogoMock = MockRestServiceServer.bindTo(builder).build();
            return builder.baseUrl("http://catalogo-test").build();
        }
    }
}
