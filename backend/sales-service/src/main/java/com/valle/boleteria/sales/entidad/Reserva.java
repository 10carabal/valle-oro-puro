package com.valle.boleteria.sales.entidad;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Reserva de entradas (tabla {@code sales.reservas}).
 *
 * Snapshot DESNORMALIZADO: {@code partidoId} y {@code rivalSnapshot} se copian de
 * catalog en el momento de reservar para que el reporte de ventas (Native Query)
 * se resuelva sin joins entre esquemas y para que el histórico no cambie si el
 * catálogo modifica el rival después.
 *
 * El cupo NO se toca aquí: sales registra la saga (PENDING → CONFIRMED) y delega
 * la anti-sobreventa en el UPDATE atómico de catalog.
 */
@Entity
@Table(name = "reservas", schema = "sales")
public class Reserva {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Desnormalizado del catálogo (evita joins entre esquemas en el reporte). */
    @Column(name = "partido_id", nullable = false)
    private Long partidoId;

    /** Rival "congelado" al momento de la reserva (trazabilidad histórica). */
    @Column(name = "rival_snapshot", nullable = false)
    private String rivalSnapshot;

    @Column(name = "localidad_id", nullable = false)
    private Long localidadId;

    /** Dueño de la reserva (subject del JWT; el email define el permiso). */
    @Column(name = "usuario_email", nullable = false)
    private String usuarioEmail;

    @Column(nullable = false)
    private int cantidad;

    /** Precio de catalog al momento de reservar (puede cambiar después). */
    @Column(name = "precio_unitario", nullable = false, precision = 12, scale = 2)
    private BigDecimal precioUnitario;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal total;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EstadoReserva estado;

    /**
     * Bloqueo optimista: dos pagos (o un pago y una cancelación) simultáneos sobre
     * la MISMA reserva no pueden ganar los dos. Hibernate compara la versión en el
     * UPDATE; el perdedor recibe OptimisticLockingFailureException → 409.
     */
    @Version
    @Column(nullable = false)
    private long version;

    @Column(name = "creado_en", nullable = false)
    private LocalDateTime creadoEn;

    @Column(name = "confirmado_en")
    private LocalDateTime confirmadoEn;

    /** Lado inverso de la relación 1:1 (el FK vive en entradas.reserva_id). */
    @OneToOne(mappedBy = "reserva", fetch = FetchType.LAZY)
    private Entrada entrada;

    protected Reserva() {
        // JPA
    }

    public Reserva(Long partidoId, String rivalSnapshot, Long localidadId, String usuarioEmail,
                   int cantidad, BigDecimal precioUnitario, EstadoReserva estado) {
        this.partidoId = partidoId;
        this.rivalSnapshot = rivalSnapshot;
        this.localidadId = localidadId;
        this.usuarioEmail = usuarioEmail;
        this.cantidad = cantidad;
        this.precioUnitario = precioUnitario;
        this.total = precioUnitario.multiply(BigDecimal.valueOf(cantidad));
        this.estado = estado;
        this.creadoEn = LocalDateTime.now();
    }

    /** Pago simulado aceptado: la reserva queda confirmada con su hora. */
    public void confirmar() {
        this.estado = EstadoReserva.CONFIRMED;
        this.confirmadoEn = LocalDateTime.now();
    }

    /** Cancelación: el cupo se libera en catalog (compensación de la saga). */
    public void cancelar() {
        this.estado = EstadoReserva.CANCELLED;
    }

    public Long getId() {
        return id;
    }

    public Long getPartidoId() {
        return partidoId;
    }

    public String getRivalSnapshot() {
        return rivalSnapshot;
    }

    public Long getLocalidadId() {
        return localidadId;
    }

    public String getUsuarioEmail() {
        return usuarioEmail;
    }

    public int getCantidad() {
        return cantidad;
    }

    public BigDecimal getPrecioUnitario() {
        return precioUnitario;
    }

    public BigDecimal getTotal() {
        return total;
    }

    public EstadoReserva getEstado() {
        return estado;
    }

    public LocalDateTime getCreadoEn() {
        return creadoEn;
    }

    public LocalDateTime getConfirmadoEn() {
        return confirmadoEn;
    }

    public Entrada getEntrada() {
        return entrada;
    }
}
