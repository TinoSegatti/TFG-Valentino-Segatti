-- V006: snapshots inmutables (ADR 0005) y estado de borrador para compras.

ALTER TABLE t_compra_cabecera
    ADD COLUMN codigo_proveedor_snapshot VARCHAR(50),
    ADD COLUMN nombre_proveedor_snapshot VARCHAR(200),
    ADD COLUMN estado VARCHAR(20) NOT NULL DEFAULT 'BORRADOR';

ALTER TABLE t_compra_detalle
    ADD COLUMN codigo_mp_snapshot VARCHAR(50),
    ADD COLUMN nombre_mp_snapshot VARCHAR(200);
