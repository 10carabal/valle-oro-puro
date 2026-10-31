package com.valle.boleteria.sales.servicio;

import com.valle.boleteria.common.dto.CupoResponse;
import com.valle.boleteria.common.dto.LocalidadInfo;
import com.valle.boleteria.common.dto.ReservarCupoRequest;
import com.valle.boleteria.common.error.ConflictException;
import com.valle.boleteria.common.error.NotFoundException;
import com.valle.boleteria.common.security.JwtService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

/**
 * Puente HTTP sales → catalog.
 *
 * <p><b>Identidad de servicio:</b> las llamadas internas viajan con un token
 * corto firmado por sales ({@code sub=sales-service}, {@code rol=SERVICE}, 60 s)
 * con el mismo secreto compartido. Catalog exige {@code ROLE_SERVICE} SOLO en
 * {@code /reservar} y {@code /liberar}: un usuario final con su propio JWT ya no
 * puede tocar el inventario. Sales sigue validando dueño/ADMIN antes de llamar.</p>
 *
 * <p>El GET de la localidad es público y no necesita cabecera.</p>
 *
 * <p>Mapeo de errores del contrato fijado en memory.md §3:
 * GET localidad 404 → NotFoundException (404 al cliente),
 * POST reservar 409 → ConflictException (409 "No hay cupo disponible").</p>
 */
@Component
public class CatalogClient {

    private static final String RUTA_LOCALIDADES = "/api/catalog/localidades";
    private static final String NOMBRE_SERVICIO = "sales-service";

    private final RestClient restClient;
    private final JwtService jwtService;

    public CatalogClient(RestClient restClient, JwtService jwtService) {
        this.restClient = restClient;
        this.jwtService = jwtService;
    }

    /** Datos de la localidad (precio, partido y rival para el snapshot). */
    public LocalidadInfo localidad(Long localidadId) {
        try {
            return restClient.get()
                    .uri(RUTA_LOCALIDADES + "/{id}", localidadId)
                    .retrieve()
                    .body(LocalidadInfo.class);
        } catch (RestClientResponseException e) {
            throw mapear(e, localidadId, "consultando la localidad");
        }
    }

    /** Reserva de cupo en el inventario de catalog (anti-sobreventa allá). */
    public CupoResponse reservarCupo(Long localidadId, int cantidad) {
        return post(localidadId, "/reservar", cantidad);
    }

    /** Compensación: libera el cupo que tomó la reserva al cancelarse. */
    public CupoResponse liberarCupo(Long localidadId, int cantidad) {
        return post(localidadId, "/liberar", cantidad);
    }

    private CupoResponse post(Long localidadId, String accion, int cantidad) {
        try {
            return restClient.post()
                    .uri(RUTA_LOCALIDADES + "/{id}" + accion, localidadId)
                    // Token de servicio fresco por llamada (vive 60 s).
                    .header(HttpHeaders.AUTHORIZATION,
                            "Bearer " + jwtService.generarTokenServicio(NOMBRE_SERVICIO))
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new ReservarCupoRequest(cantidad))
                    .retrieve()
                    .body(CupoResponse.class);
        } catch (RestClientResponseException e) {
            throw mapear(e, localidadId, "llamando a catalog" + accion);
        }
    }

    private RuntimeException mapear(RestClientResponseException e, Long localidadId, String contexto) {
        HttpStatusCode status = e.getStatusCode();
        if (status.value() == 404) {
            return new NotFoundException("Localidad no encontrada: " + localidadId);
        }
        if (status.value() == 409) {
            return new ConflictException("No hay cupo disponible");
        }
        // Caída o error inesperado de catalog: RuntimeException → la transacción
        // de sales hace rollback y NO queda reserva huérfana.
        return new RuntimeException("catalog devolvió " + status.value() + " " + contexto);
    }
}
