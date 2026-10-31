package com.valle.boleteria.common.dto;

/** Resultado de reservar/liberar cupo en catalog. */
public record CupoResponse(Long localidadId, int cupo, int vendidos, int disponibles) {

    public static CupoResponse de(Long localidadId, int cupo, int vendidos) {
        return new CupoResponse(localidadId, cupo, vendidos, cupo - vendidos);
    }
}
