-- Bloqueo optimista de reservas (@Version en Reserva).
-- Dos pagos simultáneos, o un pago y una cancelación, ya no pueden ganar los dos:
-- Hibernate compara la versión en el UPDATE y el perdedor recibe
-- OptimisticLockingFailureException, que el GlobalExceptionHandler mapea a 409.
ALTER TABLE sales.reservas
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0;

COMMENT ON COLUMN sales.reservas.version IS
    'Bloqueo optimista (@Version): 409 si dos transacciones compiten por la misma reserva';
