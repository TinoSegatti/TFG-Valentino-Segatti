-- V012: precio base manual de la materia prima.
-- Es el precio que carga el usuario al crear/editar la MP. Las compras NUNCA lo tocan; solo
-- modifican precio_por_kilo (precio vigente de catálogo). Sirve como valor de reversión cuando
-- se elimina la última compra de una MP y ya no quedan líneas de compra que respalden el precio.

ALTER TABLE t_materia_prima
    ADD COLUMN precio_base_manual DOUBLE PRECISION NOT NULL DEFAULT 0;

-- Backfill: para las MPs existentes, el mejor valor disponible como base manual es su precio actual.
UPDATE t_materia_prima SET precio_base_manual = precio_por_kilo;

-- Índice para acelerar la búsqueda del precio vigente por materia en t_compra_detalle
-- (consulta findMasRecientePorMateriaPrima). PostgreSQL no indexa columnas FK automáticamente.
CREATE INDEX IF NOT EXISTS idx_compra_detalle_materia
    ON t_compra_detalle (id_materia_prima);
