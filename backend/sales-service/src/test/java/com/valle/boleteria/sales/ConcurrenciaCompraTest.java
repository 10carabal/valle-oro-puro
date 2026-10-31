package com.valle.boleteria.sales;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.valle.boleteria.common.security.JwtService;
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
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.client.RestClient;import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.endsWith;
import static org.springframework.test.web.client.ExpectedCount.manyTimes;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * CASO CRÍTICO A NIVEL DE SALES — 10 hilos ejecutando el flujo completo
 * (reserva → pagar) contra una localidad de CUPO 1.
 *
 * QUÉ PRUEBA ESTE TEST (la frontera de sales): que la saga de compra degraada
 * correctamente bajo concurrencia — de 10 flujos simultáneos exactamente UNO
 * termina CONFIRMED con entrada, los otros 9 reciben 409 y quedan REJECTED;
 * que el contador del mock (cupo) jamás se pasa del cupo; y que existe
 * exactamente UNA entrada emitida. Simula la semántica de catálogo con un
 * AtomicInteger + CAS (compare-and-set), la misma idea que su UPDATE atómico.
 *
 * CUÁL ES LA GARANTÍA FUERTE (documentado en el README): la no sobreventa la
 * garantiza catalog-service con su UPDATE condicional
 * {@code ... SET vendidos = vendidos + :qty WHERE vendidos + :qty <= cupo} en
 * PostgreSQL (más el CHECK vendidos <= cupo), verificado con su propio test
 * multihilo contra la BD real. Este test complementario valida que, ante ese
 * 409, sales no deja reservas huérfanas ni emite entradas duplicadas.
 *
 * OJO con el pool: cada hilo perdido ocupa DOS conexiones (transacción
 * principal + REQUIRES_NEW del REJECTED), por eso se amplía Hikari a 40 para
 * 10 hilos en este test.
 */
@Testcontainers
@SpringBootTest(properties = "spring.datasource.hikari.maximum-pool-size=40")
@AutoConfigureMockMvc
class ConcurrenciaCompraTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    private static final int HILOS = 10;
    private static final int CUPO = 1;

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
        catalogo.reset();
    }

    @Test
    void diezHilosSobreCupoUnoExactamenteUnoConfirmaLaCompra() throws Exception {
        String token = jwtService.generarToken("comprador@prueba", "USER");

        // Localidad de CUPO 1 (la "última entrada" del caso crítico)
        catalogo.expect(manyTimes(), requestTo(endsWith("/localidades/7")))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("""
                        {"id":7,"partidoId":100,"rival":"Última Entrada","nombre":"VIP",
                         "precio":50000.0,"cupo":1,"vendidos":0}
                        """, MediaType.APPLICATION_JSON));

        // Réplica de la semántica de cupo de catalog: CAS sobre el contador.
        AtomicInteger vendidos = new AtomicInteger(0);
        catalogo.expect(manyTimes(), requestTo(endsWith("/reservar")))
                .andRespond(req -> {
                    // El body del request mock es legible (MockClientHttpRequest)
                    int cantidad = objectMapper.readTree(
                            ((MockClientHttpRequest) req).getBodyAsString())
                            .get("cantidad").asInt();
                    while (true) {
                        int actual = vendidos.get();
                        if (actual + cantidad > CUPO) {
                            // "No hay cupo": el mismo 409 que devolvería el
                            // UPDATE atómico de catalog (0 filas afectadas).
                            return withStatus(HttpStatus.CONFLICT)
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .body("""
                                            {"status":409,"error":"Conflict","message":"No hay cupo disponible"}
                                            """)
                                    .createResponse(req);
                        }
                        if (vendidos.compareAndSet(actual, actual + cantidad)) {
                            break;
                        }
                    }
                    return withSuccess("""
                            {"localidadId":7,"cupo":1,"vendidos":%d,"disponibles":%d}
                            """.formatted(vendidos.get(), CUPO - vendidos.get()), MediaType.APPLICATION_JSON)
                            .createResponse(req);
                });

        // Barrera de largada: los 10 hilos quedan listos y arrancan juntos.
        ExecutorService pool = Executors.newFixedThreadPool(HILOS);
        CountDownLatch listos = new CountDownLatch(HILOS);
        CountDownLatch largada = new CountDownLatch(1);
        List<Future<Integer>> resultados = new ArrayList<>();
        for (int i = 0; i < HILOS; i++) {
            resultados.add(pool.submit(() -> {
                listos.countDown();
                largada.await();
                // Flujo completo: reserva → si 201, pagar. Devuelve el estado FINAL.
                MockHttpServletResponse reserva = mockMvc.perform(post("/api/sales/reservas")
                                .header("Authorization", "Bearer " + token)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"localidadId\":7,\"cantidad\":1}"))
                        .andReturn().getResponse();
                if (reserva.getStatus() != 201) {
                    return reserva.getStatus();
                }
                long reservaId = objectMapper
                        .readTree(reserva.getContentAsString(StandardCharsets.UTF_8))
                        .get("reservaId").asLong();
                return mockMvc.perform(post("/api/sales/reservas/" + reservaId + "/pagar")
                                .header("Authorization", "Bearer " + token))
                        .andReturn().getResponse().getStatus();
            }));
        }

        assertThat(listos.await(20, TimeUnit.SECONDS)).as("los 10 hilos quedaron listos").isTrue();
        largada.countDown();
        pool.shutdown();
        assertThat(pool.awaitTermination(120, TimeUnit.SECONDS)).as("los 10 flujos terminaron").isTrue();

        // Resultados: exactamente UNA compra exitosa; el resto, 409 de cupo.
        int exitos = 0;
        for (Future<Integer> futuro : resultados) {
            int estado = futuro.get();
            assertThat(estado).as("solo 200 (pagado) o 409 (sin cupo); nunca 5xx").isIn(200, 409);
            if (estado != 409) {
                exitos++;
            }
        }
        assertThat(exitos).as("de 10 hilos sobre cupo 1, gana EXACTAMENTE uno").isEqualTo(1);

        // El contador del mock nunca superó el cupo (CAS replicando el UPDATE).
        assertThat(vendidos.get()).isEqualTo(CUPO);

        // Estado en BD: 1 CONFIRMED + 9 REJECTED; exactamente 1 entrada.
        List<Reserva> reservas = reservaRepository.findAll();
        assertThat(reservas).hasSize(HILOS);
        assertThat(reservas.stream().filter(r -> r.getEstado() == EstadoReserva.CONFIRMED).count())
                .isEqualTo(1);
        assertThat(reservas.stream().filter(r -> r.getEstado() == EstadoReserva.REJECTED).count())
                .isEqualTo(HILOS - 1);
        assertThat(entradaRepository.count()).as("una sola entrada emitida").isEqualTo(1);
    }

    /**
     * Misma infraestructura de mock que en {@code VentaFlujoTest}: el
     * MockRestServiceServer se construye con el builder ANTES de crear el
     * RestClient que usa la aplicación (sin red, sin WireMock).
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
