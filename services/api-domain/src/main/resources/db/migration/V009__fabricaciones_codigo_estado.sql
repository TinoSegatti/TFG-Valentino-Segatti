-- V009: codigo de negocio, estado BORRADOR/REGISTRADA, formula opcional en borrador, snapshots.

ALTER TABLE t_fabricacion
    ADD COLUMN IF NOT EXISTS codigo_fabricacion VARCHAR(50);

ALTER TABLE t_fabricacion
    ADD COLUMN IF NOT EXISTS estado VARCHAR(20) NOT NULL DEFAULT 'REGISTRADA';

ALTER TABLE t_fabricacion
    ADD COLUMN IF NOT EXISTS codigo_formula_snapshot VARCHAR(50);

ALTER TABLE t_fabricacion
    ADD COLUMN IF NOT EXISTS descripcion_formula_snapshot VARCHAR(200);

ALTER TABLE t_fabricacion
    ADD COLUMN IF NOT EXISTS costo_unitario_formula_snapshot DOUBLE PRECISION;

ALTER TABLE t_fabricacion
    ALTER COLUMN id_formula DROP NOT NULL;

ALTER TABLE t_fabricacion
    ALTER COLUMN cantidad_fabricacion SET DEFAULT 0;

ALTER TABLE t_detalle_fabricacion
    ADD COLUMN IF NOT EXISTS codigo_mp_snapshot VARCHAR(50);

ALTER TABLE t_detalle_fabricacion
    ADD COLUMN IF NOT EXISTS nombre_mp_snapshot VARCHAR(200);

CREATE UNIQUE INDEX IF NOT EXISTS uq_fabricacion_granja_codigo_activo
    ON t_fabricacion (id_granja, LOWER(codigo_fabricacion))
    WHERE activo = TRUE AND codigo_fabricacion IS NOT NULL;
