package com.valle.boleteria.catalog.entidad;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;

/**
 * Localidad (sección/zona) de un partido, con su cupo de entradas
 * (tabla {@code catalog.localidades}). El inventario de este servicio.
 *
 * {@code vendidos} NUNCA se modifica con read-modify-write en Java: el único
 * camino de escritura es el UPDATE condicional atómico de
 * {@code LocalidadRepository} (anti-sobreventa). El CHECK de la migración
 * {@code vendidos <= cupo} es la red de seguridad final.
 */
@Entity
@Table(name = "localidades", schema = "catalog")
public class Localidad {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "partido_id", nullable = false)
    private Partido partido;

    @Column(nullable = false, length = 100)
    private String nombre;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal precio;

    @Column(nullable = false)
    private int cupo;

    /** Entradas ya reservadas/vendidas. Siempre vía UPDATE atómico. */
    @Column(nullable = false)
    private int vendidos;

    protected Localidad() {
        // JPA
    }

    public Localidad(String nombre, BigDecimal precio, int cupo) {
        this.nombre = nombre;
        this.precio = precio;
        this.cupo = cupo;
        this.vendidos = 0; // toda localidad nace sin vender
    }

    public void actualizar(String nombre, BigDecimal precio, int cupo) {
        this.nombre = nombre;
        this.precio = precio;
        this.cupo = cupo;
    }

    /** Disponibilidad derivada (cupo - vendidos). */
    public int getDisponibles() {
        return cupo - vendidos;
    }

    public Long getId() {
        return id;
    }

    public Partido getPartido() {
        return partido;
    }

    void setPartido(Partido partido) {
        this.partido = partido;
    }

    public String getNombre() {
        return nombre;
    }

    public BigDecimal getPrecio() {
        return precio;
    }

    public int getCupo() {
        return cupo;
    }

    public int getVendidos() {
        return vendidos;
    }
}
