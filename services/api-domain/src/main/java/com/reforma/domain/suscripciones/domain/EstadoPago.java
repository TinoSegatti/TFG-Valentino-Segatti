package com.reforma.domain.suscripciones.domain;

/**
 * Estado de un cobro ({@code t_pago.estado} y {@code t_suscripcion.ultimo_cobro_estado}).
 * Mapea los estados de pago de Mercado Pago a los cuatro que le importan al negocio.
 */
public enum EstadoPago {
    APROBADO,
    RECHAZADO,
    PENDIENTE,
    DEVUELTO
}
