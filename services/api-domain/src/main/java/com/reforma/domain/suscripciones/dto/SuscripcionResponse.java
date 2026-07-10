package com.reforma.domain.suscripciones.dto;

import com.reforma.domain.common.domain.PlanSuscripcion;
import com.reforma.domain.suscripciones.domain.EstadoPago;
import com.reforma.domain.suscripciones.domain.EstadoSuscripcion;
import com.reforma.domain.suscripciones.domain.PeriodoFacturacion;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * Estado de la suscripción del dueño autenticado ({@code GET /api/suscripcion}).
 *
 * <p>{@code planEfectivo} siempre viene de {@code t_usuarios.plan_suscripcion} (lo que
 * gatea de verdad). {@code gestionada=false} = no hay fila en {@code t_suscripcion}
 * (DEMO implícito o plan asignado a mano): el resto de los campos van en {@code null}.
 */
public record SuscripcionResponse(
        PlanSuscripcion planEfectivo,
        boolean gestionada,
        PlanSuscripcion plan,
        PeriodoFacturacion periodo,
        EstadoSuscripcion estado,
        BigDecimal precioArs,
        Instant fechaInicio,
        Instant fechaFinPeriodo,
        PlanSuscripcion planPendiente,
        PeriodoFacturacion periodoPendiente,
        EstadoPago ultimoCobroEstado,
        Instant ultimoCobroFecha) {

    /** Cuenta sin suscripción gestionada: solo se informa el plan efectivo. */
    public static SuscripcionResponse implicita(PlanSuscripcion planEfectivo) {
        return new SuscripcionResponse(
                planEfectivo, false, null, null, null, null, null, null, null, null, null, null);
    }
}
