-- ============================================================
-- V2: catálogo semilla, en SQL dinámico (DO $$ ... $$) por dos
-- motivos: (1) las fechas se calculan desde CURRENT_DATE, así el
-- seed sigue siendo útil sin importar la fecha de ejecución;
-- (2) es idempotente: si el catálogo ya tiene datos no duplica.
--
-- La localidad VIP del PRIMER partido nace con cupo = 1: es el
-- escenario de la "última entrada" del caso crítico (sobreventa).
-- ============================================================

DO $$
DECLARE
    v_partido BIGINT;
BEGIN
    IF EXISTS (SELECT 1 FROM catalog.partidos) THEN
        RAISE NOTICE 'Seed omitido: el catálogo ya contiene datos';
        RETURN;
    END IF;

    -- 1) Caimanes de Barranquilla (+7 días) — VIP con UNA sola entrada (caso crítico)
    INSERT INTO catalog.partidos (rival, fecha, hora, escenario, estado)
    VALUES ('Caimanes de Barranquilla',
            (CURRENT_DATE + INTERVAL '7 day')::date,
            TIME '19:00:00',
            'Coliseo El Pueblo, Cali',
            'PROGRAMADO')
    RETURNING id INTO v_partido;

    INSERT INTO catalog.localidades (partido_id, nombre, precio, cupo, vendidos) VALUES
        (v_partido, 'General', 20000, 100, 0),
        (v_partido, 'Prensa',  35000,  40, 0),
        (v_partido, 'VIP',     60000,   1, 0);   -- ¡la última entrada!

    -- 2) Soacha Búfalos (+14 días)
    INSERT INTO catalog.partidos (rival, fecha, hora, escenario, estado)
    VALUES ('Soacha Búfalos',
            (CURRENT_DATE + INTERVAL '14 day')::date,
            TIME '18:00:00',
            'Coliseo Ciudad de Deportes, Soacha',
            'PROGRAMADO')
    RETURNING id INTO v_partido;

    INSERT INTO catalog.localidades (partido_id, nombre, precio, cupo, vendidos) VALUES
        (v_partido, 'General', 20000, 100, 0),
        (v_partido, 'Prensa',  35000,  40, 0),
        (v_partido, 'VIP',     60000,  20, 0);

    -- 3) Pilares de Yumbo (+3 días)
    INSERT INTO catalog.partidos (rival, fecha, hora, escenario, estado)
    VALUES ('Pilares de Yumbo',
            (CURRENT_DATE + INTERVAL '3 day')::date,
            TIME '20:00:00',
            'Coliseo de Yumbo',
            'PROGRAMADO')
    RETURNING id INTO v_partido;

    INSERT INTO catalog.localidades (partido_id, nombre, precio, cupo, vendidos) VALUES
        (v_partido, 'General', 20000, 100, 0),
        (v_partido, 'Prensa',  35000,  40, 0),
        (v_partido, 'VIP',     60000,  20, 0);
END $$;
