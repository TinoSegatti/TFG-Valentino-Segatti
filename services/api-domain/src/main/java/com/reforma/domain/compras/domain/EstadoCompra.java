package com.reforma.domain.compras.domain;

/** Ciclo de vida de una compra/factura registrada en el sistema. */
public enum EstadoCompra {
    /** Cabecera creada; detalle editable hasta que la suma coincida con el total. */
    BORRADOR,
    /** Detalle guardado y validado; documento inmutable operativamente. */
    REGISTRADA
}
