package com.valle.boleteria.catalog.servicio;

import com.valle.boleteria.catalog.dto.LocalidadRequest;
import com.valle.boleteria.catalog.dto.LocalidadResponse;
import com.valle.boleteria.catalog.dto.PartidoActualizarRequest;
import com.valle.boleteria.catalog.dto.PartidoRequest;
import com.valle.boleteria.catalog.dto.PartidoResponse;
import com.valle.boleteria.catalog.entidad.EstadoPartido;
import com.valle.boleteria.catalog.entidad.Localidad;
import com.valle.boleteria.catalog.entidad.Partido;
import com.valle.boleteria.catalog.repositorio.LocalidadRepository;
import com.valle.boleteria.catalog.repositorio.PartidoRepository;
import com.valle.boleteria.common.dto.CupoResponse;
import com.valle.boleteria.common.dto.LocalidadInfo;
import com.valle.boleteria.common.error.ConflictException;
import com.valle.boleteria.common.error.NotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

/**
 * Lógica del catálogo. Las escrituras de inventario ({@link #reservar} /
 * {@link #liberar}) son transaccionales: la escritura en BD ES la transacción y el
 * mecanismo de concurrencia es el UPDATE condicional atómico del repositorio.
 */
@Service
public class CatalogoService {

    private final PartidoRepository partidoRepository;
    private final LocalidadRepository localidadRepository;

    public CatalogoService(PartidoRepository partidoRepository, LocalidadRepository localidadRepository) {
        this.partidoRepository = partidoRepository;
        this.localidadRepository = localidadRepository;
    }

    // ------------------------------------------------------------------ partidos

    /** Listado público con precios y disponibilidad; fecha y estado son opcionales. */
    @Transactional(readOnly = true)
    public List<PartidoResponse> listar(EstadoPartido estado, LocalDate fecha) {
        return partidoRepository.buscar(estado, fecha).stream()
                .map(CatalogoService::aResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public PartidoResponse obtener(Long id) {
        return aResponse(partido(id));
    }

    /** Todo partido nace PROGRAMADO (el estado no lo decide el cliente). */
    @Transactional
    public PartidoResponse crear(PartidoRequest request) {
        Partido partido = new Partido(request.rival(), request.fecha(), request.hora(), request.escenario());
        return aResponse(partidoRepository.save(partido));
    }

    /** Actualiza rival/fecha/hora/escenario y, opcionalmente, el estado. */
    @Transactional
    public PartidoResponse actualizar(Long id, PartidoActualizarRequest request) {
        Partido partido = partido(id);
        partido.actualizar(request.rival(), request.fecha(), request.hora(), request.escenario());
        if (request.estado() != null) {
            partido.cambiarEstado(request.estado());
        }
        return aResponse(partido);
    }

    /**
     * Cancelación LÓGICA: el partido pasa a CANCELADO pero la fila y su inventario
     * se conservan, de modo que las ventas históricas siguen siendo reportables.
     */
    @Transactional
    public PartidoResponse cancelar(Long id) {
        Partido partido = partido(id);
        partido.cambiarEstado(EstadoPartido.CANCELADO);
        return aResponse(partido);
    }

    // ------------------------------------------------------------- localidades

    @Transactional
    public LocalidadResponse crearLocalidad(Long partidoId, LocalidadRequest request) {
        Partido partido = partido(partidoId);
        Localidad localidad = new Localidad(request.nombre(), request.precio(), request.cupo());
        partido.agregarLocalidad(localidad);          // bidireccionalidad
        localidadRepository.save(localidad);          // IDENTITY → el id queda asignado ya
        return aResponse(localidad);
    }

    /**
     * Edita nombre/precio/cupo. Regla de integridad: el cupo nunca puede quedar
     * por debajo de lo ya vendido (se validaría el CHECK de la BD).
     */
    @Transactional
    public LocalidadResponse actualizarLocalidad(Long id, LocalidadRequest request) {
        Localidad localidad = localidad(id);
        if (request.cupo() < localidad.getVendidos()) {
            throw new ConflictException("cupo menor a lo ya vendido: cupo=" + request.cupo()
                    + ", vendidos=" + localidad.getVendidos());
        }
        localidad.actualizar(request.nombre(), request.precio(), request.cupo());
        return aResponse(localidad);
    }

    @Transactional(readOnly = true)
    public LocalidadInfo obtenerLocalidad(Long id) {
        Localidad localidad = localidadRepository.buscarConPartido(id)
                .orElseThrow(() -> new NotFoundException("Localidad no encontrada: " + id));
        return new LocalidadInfo(localidad.getId(), localidad.getPartido().getId(),
                localidad.getPartido().getRival(), localidad.getNombre(),
                localidad.getPrecio().doubleValue(), localidad.getCupo(), localidad.getVendidos());
    }

    // ------------------------------------------------- inventario (caso crítico)

    /**
     * RESERVA DE CUPO — el caso crítico de sobreventa, resuelto con un ÚNICO
     * UPDATE condicional atómico (ver {@link LocalidadRepository#sumarVendidos}).
     *
     * No se lee "vendidos" para decidir en Java: la condición
     * {@code vendidos + cantidad <= cupo} viaja dentro del UPDATE, que en Postgres
     * serializa a los competidores por la fila. Si otra transacción tomó el último
     * cupo mientras tanto, esta actualiza 0 filas → ConflictException → 409.
     * El CHECK {@code vendidos <= cupo} de la migración es la red de seguridad final.
     */
    @Transactional
    public CupoResponse reservar(Long localidadId, int cantidad) {
        Localidad localidad = localidadRepository.buscarConPartido(localidadId)
                .orElseThrow(() -> new NotFoundException("Localidad no encontrada: " + localidadId));
        // Un partido cancelado (o ya jugado) NO admite compra: el inventario queda
        // congelado aunque a la localidad le quede cupo. Sin esta guarda, la UI
        // deshabilitaba el botón pero la API seguía vendiendo.
        if (localidad.getPartido().getEstado() != EstadoPartido.PROGRAMADO) {
            throw new ConflictException("El partido no admite compra (estado "
                    + localidad.getPartido().getEstado() + ")");
        }
        int filas = localidadRepository.sumarVendidos(localidadId, cantidad);
        if (filas == 0) {
            throw new ConflictException("No hay cupo disponible para esta localidad");
        }
        // clearAutomatically=true dejó la entidad desenganchada del contexto; se
        // relée para responder con los valores ya confirmados por la BD.
        localidad = localidad(localidadId);
        return CupoResponse.de(localidadId, localidad.getCupo(), localidad.getVendidos());
    }

    /**
     * LIBERACIÓN (compensación, ej. partido cancelado): vendidos baja con piso en 0
     * — el UPDATE condicional jamás deja vendidos negativos.
     */
    @Transactional
    public CupoResponse liberar(Long localidadId, int cantidad) {
        Localidad localidad = localidad(localidadId); // 404 si no existe
        int filas = localidadRepository.restarVendidos(localidadId, cantidad);
        if (filas == 0 && localidad.getVendidos() > 0 && localidad.getVendidos() < cantidad) {
            // Se pidió liberar más de lo vendido: se libera solo lo vendido (piso en 0).
            localidadRepository.restarVendidos(localidadId, localidad.getVendidos());
        }
        localidad = localidad(localidadId);
        return CupoResponse.de(localidadId, localidad.getCupo(), localidad.getVendidos());
    }

    // ------------------------------------------------------------------ privados

    private Partido partido(Long id) {
        return partidoRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Partido no encontrado: " + id));
    }

    private Localidad localidad(Long id) {
        return localidadRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Localidad no encontrada: " + id));
    }

    private static PartidoResponse aResponse(Partido partido) {
        List<LocalidadResponse> localidades = partido.getLocalidades().stream()
                .map(CatalogoService::aResponse)
                .toList();
        return new PartidoResponse(partido.getId(), partido.getRival(), partido.getFecha(),
                partido.getHora(), partido.getEscenario(), partido.getEstado(), localidades);
    }

    private static LocalidadResponse aResponse(Localidad localidad) {
        return new LocalidadResponse(localidad.getId(), localidad.getNombre(), localidad.getPrecio(),
                localidad.getCupo(), localidad.getVendidos(), localidad.getDisponibles());
    }
}
