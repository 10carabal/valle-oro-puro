package com.valle.boleteria.sales.entidad;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/**
 * Entrada emitida (tabla {@code sales.entradas}): una por reserva confirmada.
 *
 * El código es un UUID v4 generado en Java ({@code UUID.randomUUID()}); la columna
 * {@code codigo_unico} tiene restricción UNIQUE en BD, que es la garantía FÍSICA
 * de que dos pagos nunca producen códigos repetidos (a nivel de aplicación dos
 * UUID v4 repetidos son prácticamente imposibles, pero la BD no confía en eso).
 */
@Entity
@Table(name = "entradas", schema = "sales")
public class Entrada {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reserva_id", nullable = false, unique = true)
    private Reserva reserva;

    @Column(name = "codigo_unico", nullable = false, unique = true, length = 40)
    private String codigoUnico;

    @Column(name = "emitida_en", nullable = false)
    private LocalDateTime emitidaEn;

    protected Entrada() {
        // JPA
    }

    public Entrada(Reserva reserva, String codigoUnico) {
        this.reserva = reserva;
        this.codigoUnico = codigoUnico;
        this.emitidaEn = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public Reserva getReserva() {
        return reserva;
    }

    public String getCodigoUnico() {
        return codigoUnico;
    }

    public LocalDateTime getEmitidaEn() {
        return emitidaEn;
    }
}
