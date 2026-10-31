package com.valle.boleteria.catalog.controlador;

import com.valle.boleteria.catalog.dto.LocalidadRequest;
import com.valle.boleteria.catalog.dto.LocalidadResponse;
import com.valle.boleteria.catalog.servicio.CatalogoService;
import com.valle.boleteria.common.dto.CupoResponse;
import com.valle.boleteria.common.dto.LocalidadInfo;
import com.valle.boleteria.common.dto.ReservarCupoRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * API de localidades (inventario de entradas). El GET es público; editar cupos es
 * solo ADMIN; reservar/liberar puede cualquier usuario autenticado (sales actúa
 * con su JWT). El anti-sobreventa vive detrás de /reservar.
 */
@RestController
@RequestMapping("/api/catalog/localidades")
public class LocalidadController {

    private final CatalogoService servicio;

    public LocalidadController(CatalogoService servicio) {
        this.servicio = servicio;
    }

    /** Datos que sales necesita para construir la reserva (precio + partido). */
    @GetMapping("/{id}")
    public LocalidadInfo obtener(@PathVariable Long id) {
        return servicio.obtenerLocalidad(id);
    }

    /** Edita nombre/precio/cupo; 409 si el cupo quedaría por debajo de lo vendido. */
    @PutMapping("/{id}")
    public LocalidadResponse actualizar(@PathVariable Long id,
                                        @Valid @RequestBody LocalidadRequest request) {
        return servicio.actualizarLocalidad(id, request);
    }

    /** Reserva de cupo (contrato sales → catalog): 200 con el cupo o 409 si no hay. */
    @PostMapping("/{id}/reservar")
    public CupoResponse reservar(@PathVariable Long id, @Valid @RequestBody ReservarCupoRequest request) {
        return servicio.reservar(id, request.cantidad());
    }

    /** Liberación de cupo (compensación); vendidos baja con piso en 0. */
    @PostMapping("/{id}/liberar")
    public CupoResponse liberar(@PathVariable Long id, @Valid @RequestBody ReservarCupoRequest request) {
        return servicio.liberar(id, request.cantidad());
    }
}
