-- V004: módulo Animales — campos extras y unicidad parcial (Sprint 2 #5).
-- Mantiene la política definida en ADR 0005 (soft-delete + entidad nueva al reusar código).

ALTER TABLE t_animal
    ADD COLUMN IF NOT EXISTS observaciones TEXT,
    ADD COLUMN IF NOT EXISTS fecha_ultima_actualizacion TIMESTAMPTZ NOT NULL DEFAULT NOW();

-- Reemplazo del UNIQUE total por uno parcial: solo bloquea código duplicado entre activos.
ALTER TABLE t_animal
    DROP CONSTRAINT IF EXISTS t_animal_id_granja_codigo_animal_key;

CREATE UNIQUE INDEX IF NOT EXISTS uq_animal_granja_codigo_activo
    ON t_animal (id_granja, codigo_animal)
    WHERE activo = TRUE;
