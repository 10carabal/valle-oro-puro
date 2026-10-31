package com.valle.boleteria.sales.repositorio;

import com.valle.boleteria.sales.dto.VentaReporteFila;
import com.valle.boleteria.sales.entidad.EstadoReserva;
import com.valle.boleteria.sales.entidad.Reserva;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface ReservaRepository extends JpaRepository<Reserva, Long> {

    /**
     * JPQL parametrizada (requisito de la prueba): lista "mis reservas" filtrando
     * por el usuario autenticado y, OPCIONALMENTE, por estado. El LEFT JOIN FETCH
     * trae la entrada (código) en la MISMA consulta → sin N+1.
     *
     * El CAST sobre el parámetro anulable es necesario en PostgreSQL (misma lección
     * aplicada en catalog): sin él, Hibernate 6 envía el NULL sin tipo declarado y
     * PG responde 42P18 "could not determine data type". El CAST fija el tipo sin
     * alterar la semántica del filtro opcional.
     */
    @Query("""
            SELECT r FROM Reserva r
            LEFT JOIN FETCH r.entrada
            WHERE r.usuarioEmail = :email
              AND (CAST(:estado AS com.valle.boleteria.sales.entidad.EstadoReserva) IS NULL
                   OR r.estado = :estado)
            ORDER BY r.id DESC""")
    List<Reserva> buscarPorUsuario(@Param("email") String email,
                                   @Param("estado") EstadoReserva estado);

    /** Reservas vivas (PENDING/CONFIRMED) de un partido, para la cancelación masiva. */
    List<Reserva> findByPartidoIdAndEstadoIn(Long partidoId, Collection<EstadoReserva> estados);

    /**
     * NATIVE QUERY (requisito de la prueba) — reporte de ventas por partido y
     * localidad, SOLO con reservas CONFIRMED, con filtro opcional por partido.
     *
     * JUSTIFICACIÓN JPQL vs SQL NATIVO (material para el README): esta consulta
     * exige agregaciones (SUM) con GROUP BY sobre dos COLUMNAS DESNORMALIZADAS y
     * una constante de estado; en JPQL la proyección con alias por columna y el
     * agregado condicional expondrían la estructura de clases (habría que joinar
     * contra la entidad Entrada para validar la emisión, lógica que además
     * enredaría el reporte con el estado de la entrada). En SQL puro el reporte
     * es una consulta plano-relacional sobre el esquema sales (sin joins entre
     * esquemas, decisión cerrada), y el agrupado por snapshot desnormalizado
     * (partido_id, rival_snapshot, localidad_id) se expresa directamente.
     * Además, la proyección por interfaz con alias de columna traduce 1:1 los
     * nombres del contrato JSON.
     *
     * CAST(?1 AS BIGINT): mismo motivo que en la JPQL — PostgreSQL necesita el
     * tipo del parámetro anulable para el filtro opcional (?partidoId=).
     */
    @Query(value = """
            SELECT r.partido_id     AS "partidoId",
                   r.rival_snapshot AS "rival",
                   r.localidad_id   AS "localidadId",
                   COALESCE(SUM(r.cantidad), 0) AS "cantidadVendida",
                   COALESCE(SUM(r.total), 0)    AS "totalRecaudado"
              FROM sales.reservas r
             WHERE r.estado = 'CONFIRMED'
               AND (CAST(?1 AS BIGINT) IS NULL OR r.partido_id = ?1)
             GROUP BY r.partido_id, r.rival_snapshot, r.localidad_id
             ORDER BY r.partido_id, r.localidad_id
            """, nativeQuery = true)
    List<VentaReporteFila> reporteVentas(@Param("partidoId") Long partidoId);
}
