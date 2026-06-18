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
        assertThat(planService.limiteGranjas(PlanSuscripcion.DEMO)).isEqualTo(2);
        assertThat(planService.limiteGranjas(PlanSuscripcion.STARTER)).isEqualTo(2);
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

    @Test
    void limiteProveedoresPorPlan() {
        assertThat(planService.limiteProveedores(PlanSuscripcion.DEMO)).isEqualTo(5);
        assertThat(planService.limiteProveedores(PlanSuscripcion.STARTER)).isEqualTo(15);
        assertThat(planService.limiteProveedores(PlanSuscripcion.BUSINESS)).isEqualTo(50);
        assertThat(planService.limiteProveedores(PlanSuscripcion.ENTERPRISE))
                .isEqualTo(Integer.MAX_VALUE);
    }

    @Test
    void limiteAnimalesPorPlan() {
        assertThat(planService.limiteAnimales(PlanSuscripcion.DEMO)).isEqualTo(5);
        assertThat(planService.limiteAnimales(PlanSuscripcion.STARTER)).isEqualTo(20);
        assertThat(planService.limiteAnimales(PlanSuscripcion.BUSINESS)).isEqualTo(100);
        assertThat(planService.limiteAnimales(PlanSuscripcion.ENTERPRISE))
                .isEqualTo(Integer.MAX_VALUE);
    }

    @Test
    void limiteFabricacionesPorPlan() {
        assertThat(planService.limiteFabricaciones(PlanSuscripcion.DEMO)).isEqualTo(5);
        assertThat(planService.limiteFabricaciones(PlanSuscripcion.STARTER)).isEqualTo(50);
        assertThat(planService.limiteFabricaciones(PlanSuscripcion.BUSINESS)).isEqualTo(500);
        assertThat(planService.limiteFabricaciones(PlanSuscripcion.ENTERPRISE))
                .isEqualTo(Integer.MAX_VALUE);
    }

    @Test
    void limiteEmpleadosPorPlan() {
        // DEMO habilita 2 invitaciones para el recorrido de prueba (ver PlanService#limiteEmpleados).
        assertThat(planService.limiteEmpleados(PlanSuscripcion.DEMO)).isEqualTo(2);
        assertThat(planService.limiteEmpleados(PlanSuscripcion.STARTER)).isEqualTo(2);
        assertThat(planService.limiteEmpleados(PlanSuscripcion.BUSINESS)).isEqualTo(10);
        assertThat(planService.limiteEmpleados(PlanSuscripcion.ENTERPRISE))
                .isEqualTo(Integer.MAX_VALUE);
    }
}
