package com.valle.boleteria.catalog.repositorio;

import com.valle.boleteria.catalog.entidad.EstadoPartido;
import com.valle.boleteria.catalog.entidad.Partido;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface PartidoRepository extends JpaRepository<Partido, Long> {

    /**
     * JPQL parametrizada (requisito de la prueba): los DOS filtros son opcionales,
     * se puede consultar por fecha, por estado o por ambos a la vez. El
     * LEFT JOIN FETCH trae las localidades (precios y disponibilidad) en la MISMA
     * consulta, evitando el N+1 del listado público.
     *
     * El CAST sobre :fecha es necesario en PostgreSQL: sin él, Hibernate 6 envía
     * el NULL del parámetro anulable sin tipo declarado y PG responde 42P18
     * ("could not determine data type"). El CAST le fija el tipo DATE sin alterar
     * la semántica del filtro opcional (el enum no lo necesita).
     */
    @Query("""
            SELECT DISTINCT p FROM Partido p
            LEFT JOIN FETCH p.localidades l
            WHERE (:estado IS NULL OR p.estado = :estado)
              AND (CAST(:fecha AS java.time.LocalDate) IS NULL OR p.fecha = :fecha)
            ORDER BY p.fecha ASC""")
    List<Partido> buscar(@Param("estado") EstadoPartido estado, @Param("fecha") LocalDate fecha);
}
