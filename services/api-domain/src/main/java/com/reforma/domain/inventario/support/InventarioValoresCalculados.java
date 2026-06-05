package com.reforma.domain.inventario.support;

/** Valores derivados de compras, inventario inicial y (futuro) fabricaciones. */
public record InventarioValoresCalculados(
        double cantidadAcumulada,
        double cantidadSistema,
        double cantidadReal,
        double merma,
        double precioAlmacen,
        double valorStock) {}
