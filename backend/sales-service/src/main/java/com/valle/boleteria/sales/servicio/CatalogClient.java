package com.valle.boleteria.sales.servicio;

import com.valle.boleteria.common.dto.CupoResponse;
import com.valle.boleteria.common.dto.LocalidadInfo;
import com.valle.boleteria.common.dto.ReservarCupoRequest;
import com.valle.boleteria.common.error.ConflictException;
import com.valle.boleteria.common.error.NotFoundException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

/**
 * Puente HTTP sales → catalog. Propagación de JWT (decisión cerrada): cada
 * llamada reenvía la MISMA cabecera Authorization del usuario; la autorización
 * la valida el backend de catalog (sin tokens de servicio).
 *
 * Mapeo de errores del contrato fijado en memory.md §3:
 * GET localidad 404 → NotFoundException (404 al cliente),
 * POST reservar 409 → ConflictException (409 "No hay cupo disponible").
 */
@Component
public class CatalogClient {

    private static final String RUTA_LOCALIDADES = "/api/catalog/localidades";

    private final RestClient restClient;

    public CatalogClient(RestClient restClient) {
        this.restClient = restClient;
    }

    /** Datos de la localidad (precio, partido y rival para el snapshot). */
    public LocalidadInfo localidad(Long localidadId, String authorization) {
        try {
            return restClient.get()
                    .uri(RUTA_LOCALIDADES + "/{id}", localidadId)
                    .header(HttpHeaders.AUTHORIZATION, authorization)
                    .retrieve()
                    .body(LocalidadInfo.class);
        } catch (RestClientResponseException e) {
            throw mapear(e, localidadId, "consultando la localidad");
        }
    }

    /** Reserva de cupo en el inventario de catalog (anti-sobreventa allá). */
    public CupoResponse reservarCupo(Long localidadId, int cantidad, String authorization) {
        return post(localidadId, "/reservar", cantidad, authorization);
    }

    /** Compensación: libera el cupo que tomó la reserva al cancelarse. */
    public CupoResponse liberarCupo(Long localidadId, int cantidad, String authorization) {
        return post(localidadId, "/liberar", cantidad, authorization);
    }

    private CupoResponse post(Long localidadId, String accion, int cantidad, String authorization) {
        try {
            return restClient.post()
                    .uri(RUTA_LOCALIDADES + "/{id}" + accion, localidadId)
                    .header(HttpHeaders.AUTHORIZATION, authorization)
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
