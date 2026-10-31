package com.valle.boleteria.catalog.entidad;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

/** Partido del catálogo (tabla {@code catalog.partidos}). */
@Entity
@Table(name = "partidos", schema = "catalog")
public class Partido {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 200)
    private String rival;

    private LocalDate fecha;

    private LocalTime hora;

    @Column(length = 200)
    private String escenario;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EstadoPartido estado;

    /**
     * Inventario que cuelga del partido: se persiste en cascada. Un partido
     * cancelado CONSERVA sus localidades y su historial de vendidos (reportes).
     */
    @OneToMany(mappedBy = "partido", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Localidad> localidades = new ArrayList<>();

    protected Partido() {
        // JPA
    }

    public Partido(String rival, LocalDate fecha, LocalTime hora, String escenario) {
        this.rival = rival;
        this.fecha = fecha;
        this.hora = hora;
        this.escenario = escenario;
        this.estado = EstadoPartido.PROGRAMADO; // todo partido nace PROGRAMADO
    }

    public void actualizar(String rival, LocalDate fecha, LocalTime hora, String escenario) {
        this.rival = rival;
        this.fecha = fecha;
        this.hora = hora;
        this.escenario = escenario;
    }

    public void cambiarEstado(EstadoPartido estado) {
        this.estado = estado;
    }

    public void agregarLocalidad(Localidad localidad) {
        localidades.add(localidad);
        localidad.setPartido(this);
    }

    public Long getId() {
        return id;
    }

    public String getRival() {
        return rival;
    }

    public LocalDate getFecha() {
        return fecha;
    }

    public LocalTime getHora() {
        return hora;
    }

    public String getEscenario() {
        return escenario;
    }

    public EstadoPartido getEstado() {
        return estado;
    }

    public List<Localidad> getLocalidades() {
        return localidades;
    }
}
