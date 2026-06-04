-- V007: historial de precios enlazado a compras (series temporales para api-ml).

ALTER TABLE t_registro_precio
    ADD COLUMN IF NOT EXISTS id_compra VARCHAR(32) REFERENCES t_compra_cabecera(id) ON DELETE SET NULL,
    ADD COLUMN IF NOT EXISTS fecha_referencia TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS origen VARCHAR(30) NOT NULL DEFAULT 'MANUAL';

UPDATE t_registro_precio
SET fecha_referencia = fecha_cambio
WHERE fecha_referencia IS NULL;

ALTER TABLE t_registro_precio
    ALTER COLUMN fecha_referencia SET NOT NULL;

CREATE INDEX IF NOT EXISTS idx_registro_precio_mp_fecha
    ON t_registro_precio (id_materia_prima, fecha_referencia DESC);

CREATE INDEX IF NOT EXISTS idx_registro_precio_compra
    ON t_registro_precio (id_compra);
