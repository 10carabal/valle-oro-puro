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
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
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
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * Carreras de estado sobre la MISMA reserva (bloqueo optimista {@code @Version}):
 *
 * <ul>
 *   <li>dos pagos simultáneos → exactamente uno gana y se emite UNA entrada;</li>
 *   <li>pagar vs cancelar → nunca queda CONFIRMED con el cupo liberado; el
 *       perdedor recibe un conflicto (409), jamás un 500.</li>
 * </ul>
 *
 * La compensación a catalog se simula con MockRestServiceServer (misma técnica
 * que {@code VentaFlujoTest}); lo que se prueba aquí es la serialización en BD.
 */
@Testcontainers
@SpringBootTest
class ConcurrenciaReservaTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private VentaService servicio;

    @Autowired
    private ReservaRepository reservas;

    @Autowired
    private EntradaRepository entradas;

    private MockRestServiceServer catalogo;

    @BeforeEach
    void preparar() {
        catalogo = CatalogoMockConfig.catalogoMock;
        catalogo.reset();
        entradas.deleteAll();
        reservas.deleteAll();
    }

    @Test
    void dosPagosSimultaneosSobreLaMismaReservaSoloEmitenUnaEntrada() throws Exception {
        Reserva reserva = nuevaPendiente();

        List<String> resultados = correrDos(
                () -> servicio.pagar(reserva.getId(), "cliente@prueba"),
                () -> servicio.pagar(reserva.getId(), "cliente@prueba"));

        assertThat(resultados.stream().filter("OK"::equals).count())
                .as("exactamente un pago gana").isEqualTo(1);
        assertThat(resultados)
                .as("el perdedor recibe un conflicto de negocio, nunca un 500")
                .allMatch(r -> r.equals("OK") || r.contains("Conflict") || r.contains("Optimistic")
                        || r.contains("DataIntegrity"));

        assertThat(reservas.findById(reserva.getId()).orElseThrow().getEstado())
                .isEqualTo(EstadoReserva.CONFIRMED);
        assertThat(entradas.count()).as("una sola entrada emitida").isEqualTo(1);
    }

    @Test
    void pagarYCancelarEnParaleloNuncaDejanUnaReservaConfirmadaConCupoLiberado() throws Exception {
        AtomicInteger liberaciones = new AtomicInteger();
        catalogo.expect(manyTimes(), requestTo(endsWith("/liberar")))
                .andExpect(method(HttpMethod.POST))
                .andRespond(req -> {
                    liberaciones.incrementAndGet();
                    return withSuccess("""
                            {"localidadId":10,"cupo":10,"vendidos":0,"disponibles":10}
                            """, MediaType.APPLICATION_JSON).createResponse(req);
                });

        Reserva reserva = nuevaPendiente();

        List<String> resultados = correrDos(
                () -> servicio.pagar(reserva.getId(), "cliente@prueba"),
                () -> servicio.cancelar(reserva.getId(), "cliente@prueba", false));

        EstadoReserva estadoFinal = reservas.findById(reserva.getId()).orElseThrow().getEstado();
        assertThat(estadoFinal).isIn(EstadoReserva.CONFIRMED, EstadoReserva.CANCELLED);
        assertThat(resultados.stream().filter("OK"::equals).count())
                .as("al menos una operación termina bien").isGreaterThanOrEqualTo(1);

        if (estadoFinal == EstadoReserva.CONFIRMED) {
            assertThat(liberaciones.get())
                    .as("no se libera cupo de una reserva CONFIRMED").isZero();
        } else {
            assertThat(liberaciones.get())
                    .as("la cancelación libera el cupo exactamente una vez").isEqualTo(1);
        }
    }

    // ------------------------------------------------------------------ utils

    private Reserva nuevaPendiente() {
        return reservas.save(new Reserva(1L, "Rival de Prueba", 10L, "cliente@prueba", 1,
                BigDecimal.valueOf(10000), EstadoReserva.PENDING));
    }

    /** Ejecuta dos tareas con barrera de largada y devuelve "OK" o el nombre de la excepción. */
    private List<String> correrDos(Runnable a, Runnable b) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch listos = new CountDownLatch(2);
        CountDownLatch salida = new CountDownLatch(1);

        List<Callable<String>> tareas = new ArrayList<>();
        for (Runnable tarea : List.of(a, b)) {
            tareas.add(() -> {
                listos.countDown();
                salida.await();
                try {
                    tarea.run();
                    return "OK";
                } catch (Exception e) {
                    return e.getClass().getSimpleName();
                }
            });
        }

        List<Future<String>> futuros = new ArrayList<>();
        for (Callable<String> tarea : tareas) {
            futuros.add(pool.submit(tarea));
        }
        assertThat(listos.await(20, TimeUnit.SECONDS)).as("ambos hilos listos").isTrue();
        salida.countDown();
        pool.shutdown();
        assertThat(pool.awaitTermination(60, TimeUnit.SECONDS)).as("ambos hilos terminaron").isTrue();

        List<String> resultados = new ArrayList<>();
        for (Future<String> futuro : futuros) {
            resultados.add(futuro.get());
        }
        return resultados;
    }

    /** Mismo mock de catalog que VentaFlujoTest: sin red, bindTo(builder). */
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
