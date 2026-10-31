package com.valle.boleteria.sales.servicio;

import com.valle.boleteria.common.dto.LocalidadInfo;
import com.valle.boleteria.common.error.ConflictException;
import com.valle.boleteria.common.error.NotFoundException;
import com.valle.boleteria.sales.dto.CanceladasResponse;
import com.valle.boleteria.sales.dto.PagoResponse;
import com.valle.boleteria.sales.dto.EntradaResponse;
import com.valle.boleteria.sales.dto.ReservaConEntradaResponse;
import com.valle.boleteria.sales.dto.ReservaRequest;
import com.valle.boleteria.sales.dto.ReservaResponse;
import com.valle.boleteria.sales.dto.VentaReporteFila;
import com.valle.boleteria.sales.entidad.Entrada;
import com.valle.boleteria.sales.entidad.EstadoReserva;
import com.valle.boleteria.sales.entidad.Reserva;
import com.valle.boleteria.sales.repositorio.EntradaRepository;
import com.valle.boleteria.sales.repositorio.ReservaRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Saga de compra (el corazón de sales-service):
 *
 * <ol>
 *   <li>RESERVAR: snapshot de catalog (precio/partido/rival) → cupo atómico en
 *       catalog → reserva PENDING en sales. Si catalog dice 409, se registra la
 *       reserva REJECTED en una transacción independiente (trazabilidad) y se
 *       propaga el 409. Cualquier fallo inesperado revierte la transacción:
 *       nunca queda reserva huérfana.</li>
 *   <li>PAGAR (pago simulado): solo el dueño; PENDING → CONFIRMED + entrada con
 *       código UUID, reserva y entrada en la MISMA transacción.</li>
 *   <li>CANCELAR: dueño o ADMIN; PENDING/CONFIRMED → CANCELLED + compensación
 *       (POST /liberar en catalog por cada cupo tomado).</li>
 * </ol>
 */
@Service
public class VentaService {

    private final ReservaRepository reservaRepository;
    private final EntradaRepository entradaRepository;
    private final CatalogClient catalog;
    private final TrazabilidadReservas trazabilidad;

    public VentaService(ReservaRepository reservaRepository, EntradaRepository entradaRepository,
                        CatalogClient catalog, TrazabilidadReservas trazabilidad) {
        this.reservaRepository = reservaRepository;
        this.entradaRepository = entradaRepository;
        this.catalog = catalog;
        this.trazabilidad = trazabilidad;
    }

    // ------------------------------------------------------------- reserva

    /**
     * Paso 1-3 de la saga. Toda la secuencia corre en UNA transacción de sales;
     * las llamadas a catalog van por HTTP y NO se pueden deshacer, por eso el
     * orden es "primero el cupo, después persistir": si persistir falla, la
     * transacción revierte y la compensación del cupo queda para
     * {@code cancelar-por-partido} (caso extremo documentado en el README).
     */
    @Transactional
    public ReservaResponse reservar(ReservaRequest request, String usuarioEmail, String authorization) {
        LocalidadInfo info = catalog.localidad(request.localidadId(), authorization); // 404 si no existe

        try {
            catalog.reservarCupo(request.localidadId(), request.cantidad(), authorization);
        } catch (ConflictException sinCupo) {
            // Se persiste el intento fallido en otra transacción (REQUIRES_NEW):
            // el 409 relanzado hará rollback de ESTA y la fila se perdería.
            trazabilidad.registrarRechazo(new Reserva(info.partidoId(), info.rival(), info.id(),
                    usuarioEmail, request.cantidad(), BigDecimal.valueOf(info.precio()),
                    EstadoReserva.REJECTED));
            throw sinCupo;
        }

        Reserva reserva = new Reserva(info.partidoId(), info.rival(), info.id(), usuarioEmail,
                request.cantidad(), BigDecimal.valueOf(info.precio()), EstadoReserva.PENDING);
        return ReservaResponse.de(reservaRepository.save(reserva));
    }

    // --------------------------------------------------------------- pagar

    /** Pago simulado (sin pasarela real): CONFIRMED + entrada con UUID único. */
    @Transactional
    public PagoResponse pagar(Long reservaId, String usuarioEmail) {
        Reserva reserva = reserva(reservaId);
        if (!reserva.getUsuarioEmail().equals(usuarioEmail)) {
            throw new AccessDeniedException("Solo el dueño de la reserva puede pagarla");
        }
        if (reserva.getEstado() != EstadoReserva.PENDING) {
            throw new ConflictException("La reserva no está pendiente de pago");
        }
        reserva.confirmar();
        // Reserva y entrada se confirman juntas: o ambas quedan en BD o ninguna.
        Entrada entrada = entradaRepository.save(new Entrada(reserva, UUID.randomUUID().toString()));
        return new PagoResponse(reserva.getId(), reserva.getEstado().name(), EntradaResponse.de(entrada));
    }

    // ------------------------------------------------------------- cancelar

    /** Dueño o ADMIN. PENDING y CONFIRMED consumieron cupo: ambos se compensan. */
    @Transactional
    public ReservaResponse cancelar(Long reservaId, String usuarioEmail, boolean esAdmin, String authorization) {
        Reserva reserva = reserva(reservaId);
        if (!esAdmin && !reserva.getUsuarioEmail().equals(usuarioEmail)) {
            throw new AccessDeniedException("Solo el dueño de la reserva o un administrador puede cancelarla");
        }
        if (reserva.getEstado() == EstadoReserva.CANCELLED) {
            throw new ConflictException("La reserva ya fue cancelada");
        }
        if (reserva.getEstado() == EstadoReserva.REJECTED) {
            throw new ConflictException("La reserva fue rechazada (sin cupo) y no puede cancelarse");
        }
        // Compensación de la saga: el cupo tomado en catalog se devuelve aquí.
        catalog.liberarCupo(reserva.getLocalidadId(), reserva.getCantidad(), authorization);
        reserva.cancelar();
        return ReservaResponse.de(reserva);
    }

    /**
     * Cancelación masiva (ADMIN) tras cancelar un partido en catalog: cancela las
     * reservas PENDING/CONFIRMED del partido y libera el cupo de cada una.
     */
    @Transactional
    public CanceladasResponse cancelarPorPartido(Long partidoId, String authorization) {
        List<Reserva> vivas = reservaRepository.findByPartidoIdAndEstadoIn(
                partidoId, List.of(EstadoReserva.PENDING, EstadoReserva.CONFIRMED));
        for (Reserva reserva : vivas) {
            catalog.liberarCupo(reserva.getLocalidadId(), reserva.getCantidad(), authorization);
            reserva.cancelar();
        }
        return new CanceladasResponse(vivas.size());
    }

    // ------------------------------------------------------------ consultas

    /** Mis reservas con su entrada (JPQL parametrizada, estado opcional). */
    @Transactional(readOnly = true)
    public List<ReservaConEntradaResponse> misReservas(String usuarioEmail, EstadoReserva estado) {
        return reservaRepository.buscarPorUsuario(usuarioEmail, estado).stream()
                .map(ReservaConEntradaResponse::de)
                .toList();
    }

    /** Reporte de ventas (Native Query) con filtro opcional por partido. */
    @Transactional(readOnly = true)
    public List<VentaReporteFila> reporteVentas(Long partidoId) {
        return reservaRepository.reporteVentas(partidoId);
    }

    // ------------------------------------------------------------------ util

    private Reserva reserva(Long id) {
        return reservaRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Reserva no encontrada: " + id));
    }
}
