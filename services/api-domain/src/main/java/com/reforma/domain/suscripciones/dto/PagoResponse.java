package com.reforma.domain.suscripciones.dto;

import com.reforma.domain.suscripciones.domain.EstadoPago;
import java.math.BigDecimal;
import java.time.Instant;

/** Un cobro del historial ({@code GET /api/suscripcion/pagos}). Sin datos de tarjeta ni ids MP. */
public record PagoResponse(
        Long id,
        BigDecimal montoArs,
        EstadoPago estado,
        String descripcion,
        Instant fechaPago) {}
