-- Sprint 2 — Módulo Proveedores
-- RF-PROV-001 pide campos opcionales: teléfono, email, CUIT/RUT y notas.
-- V001 solo trajo dirección y localidad; acá agregamos los faltantes.

ALTER TABLE t_proveedor
    ADD COLUMN IF NOT EXISTS telefono           VARCHAR(50),
    ADD COLUMN IF NOT EXISTS email              VARCHAR(200),
    ADD COLUMN IF NOT EXISTS cuit               VARCHAR(20),
    ADD COLUMN IF NOT EXISTS notas              TEXT,
    ADD COLUMN IF NOT EXISTS fecha_ultima_actualizacion TIMESTAMPTZ NOT NULL DEFAULT NOW();
