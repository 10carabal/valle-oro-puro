package com.valle.boleteria.sales.controlador;

import com.valle.boleteria.sales.dto.CanceladasResponse;
import com.valle.boleteria.sales.dto.PagoResponse;
import com.valle.boleteria.sales.dto.ReservaConEntradaResponse;
import com.valle.boleteria.sales.dto.ReservaRequest;
import com.valle.boleteria.sales.dto.ReservaResponse;
import com.valle.boleteria.sales.entidad.EstadoReserva;
import com.valle.boleteria.sales.servicio.VentaService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * API de ventas. Todo requiere autenticación (el subject del JWT es el dueño).
 * Las llamadas internas a catalog viajan con un token de SERVICIO firmado por
 * sales (ver {@code CatalogClient}); el JWT del usuario NO se propaga.
 */
@RestController
@RequestMapping("/api/sales/reservas")
public class ReservaController {

    private final VentaService servicio;

    public ReservaController(VentaService servicio) {
        this.servicio = servicio;
    }

    /** Crea la reserva (saga paso 1): 201 PENDING o 409 si no hay cupo. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ReservaResponse reservar(@Valid @RequestBody ReservaRequest request,
                                    @AuthenticationPrincipal String email) {
        return servicio.reservar(request, email);
    }

    /** Pago simulado del dueño: CONFIRMED + entrada con código único. */
    @PostMapping("/{id}/pagar")
    public PagoResponse pagar(@PathVariable Long id, @AuthenticationPrincipal String email) {
        return servicio.pagar(id, email);
    }

    /** Cancela y libera cupo (compensación): dueño o ADMIN. */
    @PostMapping("/{id}/cancelar")
    public ReservaResponse cancelar(@PathVariable Long id,
                                    @AuthenticationPrincipal String email,
                                    Authentication authentication) {
        return servicio.cancelar(id, email, esAdmin(authentication));
    }

    /** Cancelación masiva de un partido (lo usa el panel admin). */
    @PostMapping("/cancelar-por-partido/{partidoId}")
    public CanceladasResponse cancelarPorPartido(@PathVariable Long partidoId) {
        return servicio.cancelarPorPartido(partidoId);
    }

    /** Mis reservas (JPQL parametrizada) con la entrada emitida, si tiene. */
    @GetMapping
    public List<ReservaConEntradaResponse> misReservas(
            @AuthenticationPrincipal String email,
            @RequestParam(required = false) EstadoReserva estado) {
        return servicio.misReservas(email, estado);
    }

    private static boolean esAdmin(Authentication authentication) {
        return authentication.getAuthorities().contains(new SimpleGrantedAuthority("ROLE_ADMIN"));
    }
}
