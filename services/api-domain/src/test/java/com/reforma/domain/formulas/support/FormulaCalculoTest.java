package com.reforma.domain.formulas.support;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class FormulaCalculoTest {

    @Test
    void sumaKgCompleta_y_kilosFaltantes() {
        assertThat(FormulaCalculo.sumaKgCompleta(1000.0)).isTrue();
        assertThat(FormulaCalculo.sumaKgCompleta(999.9995)).isTrue();
        assertThat(FormulaCalculo.sumaKgCompleta(500.0)).isFalse();
        assertThat(FormulaCalculo.kilosFaltantes(750.0)).isEqualTo(250.0);
    }

    @Test
    void costoParcial() {
        assertThat(FormulaCalculo.calcularCostoParcial(100.0, 105.0)).isEqualTo(10500.0);
    }

    @Test
    void redondeaCantidadesYCostosA2Decimales() {
        assertThat(FormulaCalculo.redondear(12.345)).isEqualTo(12.35);
        assertThat(FormulaCalculo.redondear(12.344)).isEqualTo(12.34);
        assertThat(FormulaCalculo.calcularCostoParcial(0.333, 3.0)).isEqualTo(1.0);
    }
}
