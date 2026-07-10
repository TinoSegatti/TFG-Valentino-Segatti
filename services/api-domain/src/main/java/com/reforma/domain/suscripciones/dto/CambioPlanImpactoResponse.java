package com.reforma.domain.suscripciones.dto;

import com.reforma.domain.common.domain.PlanSuscripcion;
import java.time.Instant;
import java.util.List;

/**
 * Impacto de cambiar al plan {@code planDestino} ({@code GET /api/suscripcion/cambio-impacto}),
 * para el modal de confirmación (RD-P6.c):
 * <ul>
 *   <li>{@code bloqueantes} — recursos que impiden confirmar (hoy solo empleados, RD-P6.b);
 *       el frontend deshabilita el CTA y ofrece "Gestionar equipo".</li>
 *   <li>{@code advertencias} — datos que quedarían en sobre-límite (RD-P6.a: nada se borra
 *       ni bloquea; solo no se podrá crear más hasta bajar del límite). Con destino DEMO los
 *       empleados excedentes van acá (la cancelación nunca se bloquea; se desactivan solos
 *       al aplicarse — RD-P6.b.4).</li>
 * </ul>
 * {@code aplicaDesde}: ahora para upgrades/contrataciones; fin del ciclo pagado para downgrades.
 */
public record CambioPlanImpactoResponse(
        PlanSuscripcion planActual,
        PlanSuscripcion planDestino,
        TipoCambio tipoCambio,
        Instant aplicaDesde,
        List<ImpactoRecurso> bloqueantes,
        List<ImpactoRecurso> advertencias) {

    public enum TipoCambio { UPGRADE, DOWNGRADE, SIN_CAMBIO }

    /** {@code granja} null para recursos de cuenta (empleados, granjas). */
    public record ImpactoRecurso(
            String recurso, String granja, long cantidadActual, int limiteDestino, long excedente) {}
}
