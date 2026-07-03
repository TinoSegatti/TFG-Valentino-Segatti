package com.reforma.domain.archivos.domain;

/**
 * Módulos que admiten crear un archivo (snapshot inmutable) de sus registros.
 * Persistido en {@code t_archivo.tipo_modulo} (VARCHAR(20)).
 */
public enum TipoModuloArchivo {
    INVENTARIO,
    COMPRAS,
    FORMULAS
}
