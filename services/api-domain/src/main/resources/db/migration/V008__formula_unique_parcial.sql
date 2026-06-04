-- V008: codigo de formula unico solo entre activas (ADR 0005).

ALTER TABLE t_formula_cabecera DROP CONSTRAINT IF EXISTS t_formula_cabecera_id_granja_codigo_formula_key;

CREATE UNIQUE INDEX IF NOT EXISTS uq_formula_granja_codigo_activa
    ON t_formula_cabecera (id_granja, codigo_formula)
    WHERE activa = TRUE;
