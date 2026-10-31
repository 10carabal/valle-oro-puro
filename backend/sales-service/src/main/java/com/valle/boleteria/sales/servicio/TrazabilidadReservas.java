package com.valle.boleteria.sales.servicio;

import com.valle.boleteria.sales.entidad.Reserva;
import com.valle.boleteria.sales.repositorio.ReservaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Escrituras de trazabilidad que DEBEN sobrevivir al rollback de la transacción
 * principal: cuando catalog responde 409 (sin cupo), la petición termina en 409
 * y la transacción de {@code VentaService#reservar} hace rollback; si la fila
 * REJECTED se guardara ahí, se perdería. Por eso se registra en una
 * transacción INDEPENDIENTE (REQUIRES_NEW) antes de relanzar el 409.
 *
 * Diseño elegido sobre la alternativa de "no persistir nada": la prueba pide
 * trazabilidad de los intentos fallidos (¿quién intentó comprar la última
 * entrada y no llegó?), y un histórico de REJECTED permite auditar la saga
 * completa sin afectar al reporte (que solo cuenta CONFIRMED).
 */
@Service
public class TrazabilidadReservas {

    private final ReservaRepository reservaRepository;

    public TrazabilidadReservas(ReservaRepository reservaRepository) {
        this.reservaRepository = reservaRepository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void registrarRechazo(Reserva reservaRechazada) {
        reservaRepository.save(reservaRechazada);
    }
}
