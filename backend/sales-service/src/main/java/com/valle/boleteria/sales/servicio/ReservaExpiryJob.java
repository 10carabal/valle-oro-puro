package com.valle.boleteria.sales.servicio;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * Job de expiración de reservas PENDING: si un usuario reserva y no paga, el cupo
 * no puede quedar retenido para siempre. Cada pasada cancela las PENDING con más
 * de {@code sales.reservas.expiracion.minutos} y libera su cupo en catalog.
 *
 * <p>Si catalog falla, el barrido hace rollback y se reintenta en la siguiente
 * pasada; el error se loguea (no se traga).</p>
 */
@Component
public class ReservaExpiryJob {

    private static final Logger log = LoggerFactory.getLogger(ReservaExpiryJob.class);

    private final VentaService ventas;
    private final long minutos;

    public ReservaExpiryJob(VentaService ventas,
                            @Value("${sales.reservas.expiracion.minutos:10}") long minutos) {
        this.ventas = ventas;
        this.minutos = minutos;
    }

    @Scheduled(fixedDelayString = "${sales.reservas.expiracion.intervalo-ms:60000}",
            initialDelayString = "${sales.reservas.expiracion.intervalo-ms:60000}")
    public void expirarReservasPendientes() {
        try {
            int expiradas = ventas.expirarPendientes(LocalDateTime.now().minusMinutes(minutos));
            if (expiradas > 0) {
                log.info("Expiradas {} reservas PENDING con más de {} minutos", expiradas, minutos);
            }
        } catch (Exception e) {
            log.error("No se pudieron expirar las reservas PENDING; se reintenta en la próxima pasada", e);
        }
    }
}
