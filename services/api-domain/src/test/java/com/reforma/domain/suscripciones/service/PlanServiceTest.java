package com.reforma.domain.suscripciones.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.reforma.domain.common.domain.PlanSuscripcion;
import org.junit.jupiter.api.Test;

/**
 * Tests puros del cálculo de límites por plan (sin Spring context).
 * RF-GRN-002 (granjas) y RF-MP-003 (materias primas).
 */
class PlanServiceTest {

    private final PlanService planService = new PlanService(null);

    @Test
    void limiteGranjasPorPlan() {
        assertThat(planService.limiteGranjas(PlanSuscripcion.DEMO)).isEqualTo(1);
        assertThat(planService.limiteGranjas(PlanSuscripcion.STARTER)).isEqualTo(1);
        assertThat(planService.limiteGranjas(PlanSuscripcion.BUSINESS)).isEqualTo(3);
        assertThat(planService.limiteGranjas(PlanSuscripcion.ENTERPRISE))
                .isEqualTo(Integer.MAX_VALUE);
    }

    @Test
    void limiteMateriasPrimasPorPlan() {
        assertThat(planService.limiteMateriasPrimas(PlanSuscripcion.DEMO)).isEqualTo(10);
        assertThat(planService.limiteMateriasPrimas(PlanSuscripcion.STARTER)).isEqualTo(30);
        assertThat(planService.limiteMateriasPrimas(PlanSuscripcion.BUSINESS)).isEqualTo(100);
        assertThat(planService.limiteMateriasPrimas(PlanSuscripcion.ENTERPRISE))
                .isEqualTo(Integer.MAX_VALUE);
    }
}
