-- ============================================================
-- V1: esquema del catálogo (partidos y localidades).
-- Este servicio es el DUEÑO del inventario de entradas: los CHECK
-- de la tabla localidades son la red de seguridad final (capa 2)
-- del mecanismo anti-sobreventa del caso crítico; la capa 1 es el
-- UPDATE condicional atómico del repositorio.
-- ============================================================

CREATE SCHEMA IF NOT EXISTS catalog;

CREATE TABLE catalog.partidos (
    id        BIGSERIAL PRIMARY KEY,
    rival     VARCHAR(200) NOT NULL,
    fecha     DATE,
    hora      TIME(6),
    escenario VARCHAR(200),
    estado    VARCHAR(20) NOT NULL,
    CONSTRAINT chk_partidos_estado CHECK (estado IN ('PROGRAMADO', 'JUGADO', 'CANCELADO'))
);

-- Filtro fecha+estado del listado público (JPQL parametrizada)
CREATE INDEX idx_partidos_fecha_estado ON catalog.partidos (fecha, estado);

CREATE TABLE catalog.localidades (
    id         BIGSERIAL PRIMARY KEY,
    partido_id BIGINT NOT NULL REFERENCES catalog.partidos (id),
    nombre     VARCHAR(100) NOT NULL,
    precio     NUMERIC(12, 2) NOT NULL,
    cupo       INT NOT NULL,
    vendidos   INT NOT NULL DEFAULT 0,
    CONSTRAINT chk_localidades_precio   CHECK (precio > 0),
    CONSTRAINT chk_localidades_cupo     CHECK (cupo >= 1),
    CONSTRAINT chk_localidades_vendidos CHECK (vendidos >= 0 AND vendidos <= cupo)
);

-- FK que se une en cada listado de partidos con precios/disponibilidad
CREATE INDEX idx_localidades_partido ON catalog.localidades (partido_id);
