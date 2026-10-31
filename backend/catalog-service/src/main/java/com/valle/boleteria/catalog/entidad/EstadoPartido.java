package com.valle.boleteria.catalog.entidad;

/**
 * Estado lógico del partido. La cancelación es LÓGICA (nunca se borra la fila):
 * así el sistema puede seguir reportando ventas históricas de partidos cancelados.
 */
public enum EstadoPartido {
    PROGRAMADO, JUGADO, CANCELADO
}
