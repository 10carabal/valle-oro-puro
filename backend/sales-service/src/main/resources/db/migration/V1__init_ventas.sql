-- ============================================================
-- V1: esquema de ventas (reservas y entradas).
--
-- sales NO es dueño del inventario: el cupo vive en catalog y aquí
-- solo se registra el RESULTADO de la saga de compra. Los campos
-- partido_id y rival_snapshot están DESNORMALIZADOS a propósito:
-- el reporte de ventas (Native Query) se resuelve 100% dentro del
-- esquema sales, sin joins entre esquemas (decisión cerrada del
-- PLAN: "PostgreSQL con 3 esquemas, sin joins entre esquemas").
-- El rival se "congela" en el momento de la reserva, de modo que un
-- cambio posterior en catalog no altera el histórico de ventas.
-- ============================================================

CREATE SCHEMA IF NOT EXISTS sales;

CREATE TABLE sales.reservas (
    id               BIGSERIAL PRIMARY KEY,
    partido_id       BIGINT NOT NULL,
    rival_snapshot   VARCHAR NOT NULL,
    localidad_id     BIGINT NOT NULL,
    usuario_email    VARCHAR NOT NULL,
    cantidad         INT NOT NULL CHECK (cantidad >= 1),
    precio_unitario  NUMERIC(12, 2) NOT NULL,
    total            NUMERIC(12, 2) NOT NULL,
    estado           VARCHAR NOT NULL CHECK (estado IN ('PENDING', 'CONFIRMED', 'REJECTED', 'CANCELLED')),
    creado_en        TIMESTAMP NOT NULL,
    confirmado_en    TIMESTAMP
);

-- Mis reservas (GET /api/sales/reservas filtra por el usuario autenticado)
CREATE INDEX idx_reservas_usuario ON sales.reservas (usuario_email);
-- Cancelación por partido y agregados por localidad (reporte nativo)
CREATE INDEX idx_reservas_localidad_estado ON sales.reservas (localidad_id, estado);
-- Reporte de ventas por partido y cancelación masiva tras cancelar un partido
CREATE INDEX idx_reservas_partido ON sales.reservas (partido_id);

CREATE TABLE sales.entradas (
    id          BIGSERIAL PRIMARY KEY,
    reserva_id  BIGINT NOT NULL UNIQUE REFERENCES sales.reservas (id),
    -- UNIQUE es la garantía física de códigos no repetidos ( UUID v4 en Java )
    codigo_unico VARCHAR NOT NULL UNIQUE,
    emitida_en  TIMESTAMP NOT NULL
);
