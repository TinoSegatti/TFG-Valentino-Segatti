package com.reforma.domain.suscripciones.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.reforma.domain.common.domain.PlanSuscripcion;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class PrecioPlanServiceTest {

    private final PrecioPlanService servicio = new PrecioPlanService(
            new BigDecimal("50750"), new BigDecimal("143550"), new BigDecimal("332050"), 10);

    @Test
    void demo_esGratisEnAmbosPeriodos() {
        assertThat(servicio.precioMensual(PlanSuscripcion.DEMO)).isEqualByComparingTo("0");
        assertThat(servicio.precioAnual(PlanSuscripcion.DEMO)).isEqualByComparingTo("0");
    }

    @Test
    void mensuales_tomanElValorDeConfig() {
        assertThat(servicio.precioMensual(PlanSuscripcion.STARTER)).isEqualByComparingTo("50750");
        assertThat(servicio.precioMensual(PlanSuscripcion.BUSINESS)).isEqualByComparingTo("143550");
        assertThat(servicio.precioMensual(PlanSuscripcion.ENTERPRISE)).isEqualByComparingTo("332050");
    }

    @Test
    void anual_esMensualPorFactor() {
        assertThat(servicio.precioAnual(PlanSuscripcion.STARTER)).isEqualByComparingTo("507500");
        assertThat(servicio.precioAnual(PlanSuscripcion.BUSINESS)).isEqualByComparingTo("1435500");
        assertThat(servicio.precioAnual(PlanSuscripcion.ENTERPRISE)).isEqualByComparingTo("3320500");
    }
}
