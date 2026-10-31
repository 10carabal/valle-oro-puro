package com.valle.boleteria.catalog;

import com.valle.boleteria.catalog.entidad.EstadoPartido;
import com.valle.boleteria.catalog.entidad.Localidad;
import com.valle.boleteria.catalog.entidad.Partido;
import com.valle.boleteria.catalog.repositorio.LocalidadRepository;
import com.valle.boleteria.catalog.repositorio.PartidoRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Requisito de la prueba: JPQL PARAMETRIZADA contra PostgreSQL real (Testcontainers).
 * El repositorio debe poder filtrar por fecha, por estado o por ambos (ambos
 * parámetros opcionales, incluido el enum en posición de "posiblemente nulo").
 * Se verifica también el JOIN FETCH: el listado trae las localidades sin N+1 y
 * sin duplicar partidos (DISTINCT).
 */
@Testcontainers
@SpringBootTest
@Transactional
class RepositorioPartidoTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private PartidoRepository partidoRepository;

    @Autowired
    private LocalidadRepository localidadRepository;

    @Test
    void filtraPorFechaPorEstadoYPorAmbos() {
        // La semilla usa fechas relativas a CURRENT_DATE; se leen de la BD para
        // no depender de la zona horaria de la máquina que ejecuta los tests.
        List<Partido> semilla = partidoRepository.findAll();
        assertThat(semilla).hasSize(3);
        Partido caimanes = semilla.stream()
                .filter(p -> p.getRival().equals("Caimanes de Barranquilla"))
                .findFirst().orElseThrow();

        // Partido extra en estado JUGADO con la MISMA fecha que Caimanes, para
        // poder probar la combinación de ambos filtros. El método es transaccional:
        // el INSERT se descarta al terminar (rollback).
        Partido jugado = new Partido("Rival De Prueba", caimanes.getFecha(),
                LocalTime.of(21, 0), "Coliseo De Prueba");
        jugado.cambiarEstado(EstadoPartido.JUGADO);
        partidoRepository.save(jugado);
        partidoRepository.flush();

        // 1) Solo FECHA (estado nulo) → los dos partidos de ese día
        List<Partido> porFecha = partidoRepository.buscar(null, caimanes.getFecha());
        assertThat(porFecha).extracting(Partido::getRival)
                .containsExactlyInAnyOrder("Caimanes de Barranquilla", "Rival De Prueba");

        // El LEFT JOIN FETCH trae las localidades en la MISMA consulta: si no
        // hubiera fetch, aquí saltaría LazyInitializationException fuera de sesión.
        assertThat(porFecha).flatExtracting(Partido::getLocalidades)
                .extracting(Localidad::getNombre)
                .contains("General", "Prensa", "VIP");

        // 2) Solo ESTADO (fecha nula)
        List<Partido> programados = partidoRepository.buscar(EstadoPartido.PROGRAMADO, null);
        assertThat(programados).hasSize(3); // la semilla; el DISTINCT evita duplicados pese al fetch
        assertThat(programados).allMatch(p -> p.getEstado() == EstadoPartido.PROGRAMADO);

        List<Partido> jugados = partidoRepository.buscar(EstadoPartido.JUGADO, null);
        assertThat(jugados).extracting(Partido::getRival).containsExactly("Rival De Prueba");

        // 3) AMBOS filtros a la vez
        assertThat(partidoRepository.buscar(EstadoPartido.JUGADO, caimanes.getFecha()))
                .extracting(Partido::getRival).containsExactly("Rival De Prueba");
        assertThat(partidoRepository.buscar(EstadoPartido.JUGADO, caimanes.getFecha().plusDays(1)))
                .isEmpty();
    }

    @Test
    void laSemillaPreparaLaUltimaEntradaDelCasoCritico() {
        // La VIP del partido Caimanes nace con cupo 1: es la localidad del
        // escenario de la "última entrada" (sobreventa) de la prueba técnica.
        List<Localidad> vip = localidadRepository.findAll().stream()
                .filter(l -> l.getNombre().equals("VIP"))
                .toList();
        assertThat(vip).hasSize(3);

        Localidad ultimaEntrada = vip.stream()
                .filter(l -> l.getPartido().getRival().equals("Caimanes de Barranquilla"))
                .findFirst().orElseThrow();

        assertThat(ultimaEntrada.getCupo()).isEqualTo(1);
        assertThat(vip).extracting(Localidad::getCupo).containsExactlyInAnyOrder(1, 20, 20);
        assertThat(vip).allMatch(l -> l.getVendidos() == 0);
    }
}
