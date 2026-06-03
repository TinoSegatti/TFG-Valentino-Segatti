-- Reemplazamos las constraints UNIQUE totales por índices UNIQUE PARCIALES.
-- Motivo (ver ADR 0005): preservamos integridad histórica al usar baja lógica.
-- Una entidad inactiva queda "congelada" con sus datos originales y referencias
-- históricas intactas; el código puede ser reutilizado por una entidad NUEVA
-- (id distinto) solo si no hay otra activa con el mismo código en la granja.

ALTER TABLE t_materia_prima
    DROP CONSTRAINT IF EXISTS t_materia_prima_id_granja_codigo_materia_prima_key;

CREATE UNIQUE INDEX IF NOT EXISTS uq_mp_granja_codigo_activa
    ON t_materia_prima (id_granja, codigo_materia_prima)
    WHERE activa = TRUE;

ALTER TABLE t_proveedor
    DROP CONSTRAINT IF EXISTS t_proveedor_id_granja_codigo_proveedor_key;

CREATE UNIQUE INDEX IF NOT EXISTS uq_prov_granja_codigo_activo
    ON t_proveedor (id_granja, codigo_proveedor)
    WHERE activo = TRUE;
