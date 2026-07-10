package com.reforma.domain.suscripciones.dto;

/**
 * Resultado de iniciar un checkout. {@code requierePago=true} → el frontend envía al usuario
 * a {@code urlPago} (checkout de MP o pantalla simulada, según {@code modo}).
 * {@code requierePago=false} → era un downgrade: quedó programado a fin de ciclo (RD-P5)
 * sin cobro alguno; {@code suscripcion} refleja el estado resultante.
 */
public record CheckoutResponse(
        String modo,
        boolean requierePago,
        String urlPago,
        SuscripcionResponse suscripcion) {}
