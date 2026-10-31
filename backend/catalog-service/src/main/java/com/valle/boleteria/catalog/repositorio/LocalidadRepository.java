package com.valle.boleteria.catalog.repositorio;

import com.valle.boleteria.catalog.entidad.Localidad;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface LocalidadRepository extends JpaRepository<Localidad, Long> {

    /** Localidad con su partido ya cargado (para LocalidadInfo: precio + snapshot). */
    @Query("SELECT l FROM Localidad l JOIN FETCH l.partido WHERE l.id = :id")
    Optional<Localidad> buscarConPartido(@Param("id") Long id);

    /**
     * NÚCLEO DEL CASO CRÍTICO — UPDATE condicional atómico (anti-sobreventa).
     *
     * Postgres toma un row lock sobre la fila y, en READ COMMITTED, al liberarse el
     * lock REEVALÚA el WHERE contra el valor ya commitado. Por eso, de N transacciones
     * simultáneas peleando por el último cupo, SOLO UNA afecta filas; las demás ven
     * 0 filas y el servicio responde 409. No hay lectura previa en Java ni bloqueo
     * pesimista: la condición viaja dentro del propio UPDATE.
     *
     * @return nº de filas afectadas (0 = no había cupo suficiente)
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE Localidad l SET l.vendidos = l.vendidos + :cantidad "
            + "WHERE l.id = :id AND l.vendidos + :cantidad <= l.cupo")
    int sumarVendidos(@Param("id") Long id, @Param("cantidad") int cantidad);

    /**
     * Liberación con piso en 0: solo descuenta si quedan al menos {@code :cantidad}
     * vendidos; nunca deja vendidos negativo (misma técnica de UPDATE condicional).
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE Localidad l SET l.vendidos = l.vendidos - :cantidad "
            + "WHERE l.id = :id AND l.vendidos >= :cantidad")
    int restarVendidos(@Param("id") Long id, @Param("cantidad") int cantidad);
}
