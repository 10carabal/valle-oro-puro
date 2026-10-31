package com.valle.boleteria.catalog;

import com.valle.boleteria.catalog.dto.LocalidadRequest;
import com.valle.boleteria.catalog.dto.LocalidadResponse;
import com.valle.boleteria.catalog.dto.PartidoResponse;
import com.valle.boleteria.catalog.entidad.Localidad;
import com.valle.boleteria.catalog.repositorio.LocalidadRepository;
import com.valle.boleteria.catalog.repositorio.PartidoRepository;
import com.valle.boleteria.catalog.servicio.CatalogoService;
import com.valle.boleteria.common.error.ConflictException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ═══════════════════════════════════════════════════════════════════════════
 *  CASO CRÍTICO — anti-sobreventa a nivel de catálogo (prueba determinista)
 * ═══════════════════════════════════════════════════════════════════════════
 *
 * catalog-service es el DUEÑO del inventario de entradas. La reserva de cupo NO
 * se hace con "leer vendidos → sumar en Java → escribir" (eso revienta con
 * carreras) ni con bloqueo pesimista: la estrategia oficial cerrada en el plan es
 * el UPDATE condicional ATÓMICO de LocalidadRepository.sumarVendidos:
 *
 *     UPDATE catalog.localidades SET vendidos = vendidos + :cantidad
 *      WHERE id = :id AND vendidos + :cantidad <= cupo
 *
 * Postgres toma un row lock sobre la fila; las transacciones concurrentes esperan
 * y, al liberarse el lock, en READ COMMITTED REEVALÚAN el WHERE contra el valor ya
 * commitado. Solo UNA transacción puede "comprar" la última unidad de cupo; las
 * demás afectan 0 filas y el servicio lanza ConflictException → HTTP 409.
 * El CHECK "vendidos <= cupo" de V1__init_catalogo.sql es la red de seguridad
 * final (segunda capa) por si algún camino futuro esquivara el UPDATE.
 *
 * Este test dispara N=10 reservas de 1 entrada SIMULTÁNEAS (hilo por reserva,
 * barrera de salida común) sobre una localidad de cupo = 1 — la "última entrada"
 * del caso crítico — y exige el resultado exacto:
 *
 *     éxitos = 1,  ConflictException = 9,  vendidos final = 1
 *
 * Es la garantía, a nivel de catálogo, de que /reservar jamás vende dos veces la
 * misma entrada aunque 10 clientes la persigan al mismo tiempo.
 */
@Testcontainers
@SpringBootTest
class ConcurrenciaCupoTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    private static final int HILOS = 10;

    @Autowired
    private CatalogoService servicio;

    @Autowired
    private PartidoRepository partidoRepository;

    @Autowired
    private LocalidadRepository localidadRepository;

    private Long partidoDePrueba;

    @AfterEach
    void limpiar() {
        // Borra el partido de prueba (cascada → localidades) para que ningún otro
        // test del módulo vea datos de este escenario.
        if (partidoDePrueba != null) {
            partidoRepository.deleteById(partidoDePrueba);
            partidoDePrueba = null;
        }
    }

    @Test
    void diezReservasSimultaneasSobreCupoUnoSoloVendenUna() throws Exception {
        // Escenario de la "última entrada": una localidad VIP con UN cupo.
        PartidoResponse partido = servicio.crear(new com.valle.boleteria.catalog.dto.PartidoRequest(
                "Rival Concurso", LocalDate.now().plusDays(20), LocalTime.of(19, 0), "Coliseo Concurso"));
        partidoDePrueba = partido.id();
        LocalidadResponse ultimaEntrada = servicio.crearLocalidad(partidoDePrueba,
                new LocalidadRequest("VIP", new BigDecimal("60000"), 1));
        Long localidadId = ultimaEntrada.id();

        ExecutorService pool = Executors.newFixedThreadPool(HILOS);
        CountDownLatch listas = new CountDownLatch(HILOS); // todas las hebras listas
        CountDownLatch disparo = new CountDownLatch(1);    // señal de salida única
        AtomicInteger exitos = new AtomicInteger();
        AtomicInteger conflictos = new AtomicInteger();
        List<Throwable> inesperados = Collections.synchronizedList(new ArrayList<>());

        try {
            List<Future<?>> futuros = new ArrayList<>();
            for (int i = 0; i < HILOS; i++) {
                futuros.add(pool.submit(() -> {
                    listas.countDown();
                    disparo.await(); // todas arrancan a la vez (máxima contención)
                    try {
                        servicio.reservar(localidadId, 1);
                        exitos.incrementAndGet();
                    } catch (ConflictException esperado) {
                        // 409 legítimo: otra transacción se llevó el último cupo
                        conflictos.incrementAndGet();
                    } catch (Throwable inesperado) {
                        inesperados.add(inesperado);
                    }
                    return null;
                }));
            }

            // Barrera de arranque: nadie reserva hasta que todas estén esperando
            assertThat(listas.await(30, TimeUnit.SECONDS))
                    .as("las %d hebras deben quedar listas antes de reservar", HILOS).isTrue();
            disparo.countDown();

            for (Future<?> futuro : futuros) {
                futuro.get(60, TimeUnit.SECONDS); // falla el test si algo se cuelga
            }
        } finally {
            pool.shutdownNow();
        }

        // Resultado determinista del caso crítico
        assertThat(inesperados)
                .as("solo se admite éxito o ConflictException; ninguna otra excepción")
                .isEmpty();
        assertThat(exitos.get()).as("éxitos").isEqualTo(1);
        assertThat(conflictos.get()).as("conflictos 409").isEqualTo(HILOS - 1);

        // Consistencia final en BD: exactamente UNA entrada vendida, sin sobreventa
        Localidad estadoFinal = localidadRepository.findById(localidadId).orElseThrow();
        assertThat(estadoFinal.getVendidos())
                .as("la última entrada se vende exactamente una vez")
                .isEqualTo(1);
        assertThat(estadoFinal.getDisponibles()).isZero();
    }
}
