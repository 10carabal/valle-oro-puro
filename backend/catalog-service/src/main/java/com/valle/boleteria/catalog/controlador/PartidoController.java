package com.valle.boleteria.catalog.controlador;

import com.valle.boleteria.catalog.dto.LocalidadRequest;
import com.valle.boleteria.catalog.dto.LocalidadResponse;
import com.valle.boleteria.catalog.dto.PartidoActualizarRequest;
import com.valle.boleteria.catalog.dto.PartidoRequest;
import com.valle.boleteria.catalog.dto.PartidoResponse;
import com.valle.boleteria.catalog.entidad.EstadoPartido;
import com.valle.boleteria.catalog.servicio.CatalogoService;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/**
 * API de partidos. Los GET son PÚBLICOS (Angular y compras consultan sin token);
 * las escrituras requieren rol ADMIN (ver SecurityConfig).
 */
@RestController
@RequestMapping("/api/catalog/partidos")
public class PartidoController {

    private final CatalogoService servicio;

    public PartidoController(CatalogoService servicio) {
        this.servicio = servicio;
    }

    /** Listado con precios y disponibilidad. Ambos filtros son opcionales. */
    @GetMapping
    public List<PartidoResponse> listar(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fecha,
            @RequestParam(required = false) EstadoPartido estado) {
        return servicio.listar(estado, fecha);
    }

    @GetMapping("/{id}")
    public PartidoResponse obtener(@PathVariable Long id) {
        return servicio.obtener(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PartidoResponse crear(@Valid @RequestBody PartidoRequest request) {
        return servicio.crear(request);
    }

    @PutMapping("/{id}")
    public PartidoResponse actualizar(@PathVariable Long id,
                                      @Valid @RequestBody PartidoActualizarRequest request) {
        return servicio.actualizar(id, request);
    }

    /** Cancelación lógica (no borra): conserva el historial de ventas. */
    @PutMapping("/{id}/cancelar")
    public PartidoResponse cancelar(@PathVariable Long id) {
        return servicio.cancelar(id);
    }

    @PostMapping("/{id}/localidades")
    @ResponseStatus(HttpStatus.CREATED)
    public LocalidadResponse crearLocalidad(@PathVariable Long id,
                                            @Valid @RequestBody LocalidadRequest request) {
        return servicio.crearLocalidad(id, request);
    }
}
