-- ============================================================
-- V2: sin datos semilla a propósito.
-- A diferencia de auth/catalog, las reservas son datos TRANSACCIONALES
-- generados por el flujo de compra (reservar → pagar). Sembrar reservas
-- ficticias obligaría a sembrar también el inventario correspondiente en
-- catalog (otro esquema/servicio) y rompería la frontera de propiedad.
-- El seed de demostración vive en las pruebas del servicio
-- (VentaFlujoTest / ConcurrenciaCompraTest) y en el flujo real de la app.
-- ============================================================

-- Sentencia mínima para dejar la migración registrada y verificable.
SELECT 1;
