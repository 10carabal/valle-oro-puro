package com.valle.boleteria.sales;

import com.valle.boleteria.sales.entidad.EstadoReserva;
import com.valle.boleteria.sales.entidad.Reserva;
import com.valle.boleteria.sales.repositorio.EntradaRepository;
import com.valle.boleteria.sales.repositorio.ReservaRepository;
import com.valle.boleteria.sales.servicio.VentaService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.endsWith;
import static org.springframework.test.web.client.ExpectedCount.manyTimes;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * Expiración de reservas PENDING (job programado): una reserva sin pago no puede
 * retener el cupo para siempre.
 *
 * <p>El {@code @Scheduled} se neutraliza en el test con un intervalo de una hora;
 * se llama {@code expirarPendientes(cutoff)} directamente y se envejece
 * {@code creado_en} con JdbcTemplate (sin esperas).</p>
 */
@Testcontainers
@SpringBootTest(properties = "sales.reservas.expiracion.intervalo-ms=3600000")
class ExpiracionPendientesTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private VentaService servicio;

    @Autowired
    private ReservaRepository reservas;

    @Autowired
    private EntradaRepository entradas;

    @Autowired
    private JdbcTemplate jdbc;

    private MockRestServiceServer catalogo;
    private final AtomicInteger liberaciones = new AtomicInteger();

    @BeforeEach
    void preparar() {
        catalogo = CatalogoMockConfig.catalogoMock;
        catalogo.reset();
        liberaciones.set(0);
        entradas.deleteAll();
        reservas.deleteAll();

        catalogo.expect(manyTimes(), requestTo(endsWith("/liberar")))
                .andExpect(method(HttpMethod.POST))
                .andRespond(req -> {
                    liberaciones.incrementAndGet();
                    return withSuccess("""
                            {"localidadId":10,"cupo":10,"vendidos":0,"disponibles":10}
                            """, MediaType.APPLICATION_JSON).createResponse(req);
                });
    }

    @Test
    void expiraUnaPendienteViejaYLiberaElCupo() {
        Reserva vieja = reservaConEstado(EstadoReserva.PENDING);
        envejecer(vieja, 30);

        int expiradas = servicio.expirarPendientes(LocalDateTime.now().minusMinutes(10));

        assertThat(expiradas).isEqualTo(1);
        assertThat(reservas.findById(vieja.getId()).orElseThrow().getEstado())
                .isEqualTo(EstadoReserva.CANCELLED);
        assertThat(liberaciones.get()).as("el cupo se devuelve a catalog").isEqualTo(1);
    }

    @Test
    void noTocaPendientesNuevasNiConfirmadasViejas() {
        Reserva nueva = reservaConEstado(EstadoReserva.PENDING);          // creada "ahora"
        Reserva confirmadaVieja = reservaConEstado(EstadoReserva.CONFIRMED);
        envejecer(confirmadaVieja, 60);

        int expiradas = servicio.expirarPendientes(LocalDateTime.now().minusMinutes(10));

        assertThat(expiradas).isZero();
        assertThat(reservas.findById(nueva.getId()).orElseThrow().getEstado())
                .isEqualTo(EstadoReserva.PENDING);
        assertThat(reservas.findById(confirmadaVieja.getId()).orElseThrow().getEstado())
                .isEqualTo(EstadoReserva.CONFIRMED);
        assertThat(liberaciones.get()).isZero();
    }

    // ------------------------------------------------------------------ utils

    private Reserva reservaConEstado(EstadoReserva estado) {
        return reservas.save(new Reserva(1L, "Rival de Prueba", 10L, "cliente@prueba", 1,
                BigDecimal.valueOf(10000), estado));
    }

    /** Envejece la reserva moviendo creado_en al pasado (sin esperas reales). */
    private void envejecer(Reserva reserva, int minutos) {
        jdbc.update("UPDATE sales.reservas SET creado_en = ? WHERE id = ?",
                LocalDateTime.now().minusMinutes(minutos), reserva.getId());
    }

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
