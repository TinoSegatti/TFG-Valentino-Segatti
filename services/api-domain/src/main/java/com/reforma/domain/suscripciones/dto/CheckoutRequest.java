package com.reforma.domain.suscripciones.dto;

import com.reforma.domain.common.domain.PlanSuscripcion;
import com.reforma.domain.suscripciones.domain.PeriodoFacturacion;
import jakarta.validation.constraints.NotNull;

/** Body de {@code POST /api/suscripcion/checkout}: qué plan y período quiere contratar el dueño. */
public record CheckoutRequest(
        @NotNull PlanSuscripcion plan,
        @NotNull PeriodoFacturacion periodo) {}
