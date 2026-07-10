package com.reforma.domain.suscripciones.dto;

import com.reforma.domain.common.domain.PlanSuscripcion;
import com.reforma.domain.suscripciones.domain.EstadoPago;
import com.reforma.domain.suscripciones.domain.PeriodoFacturacion;
import jakarta.validation.constraints.NotNull;

/**
 * Body de {@code POST /api/suscripcion/confirmar-simulado} (solo modo {@code simulado}):
 * la pantalla de pago simulada devuelve qué se estaba comprando y el resultado elegido
 * ({@code APROBADO} o {@code RECHAZADO}). El backend re-valida todo igual que en el
 * checkout — el body no otorga nada que el dueño no pudiera pedir por su cuenta.
 */
public record ConfirmacionSimuladaRequest(
        @NotNull PlanSuscripcion plan,
        @NotNull PeriodoFacturacion periodo,
        @NotNull EstadoPago resultado) {}
