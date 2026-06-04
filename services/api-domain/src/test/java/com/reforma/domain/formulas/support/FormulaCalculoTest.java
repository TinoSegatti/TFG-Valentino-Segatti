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
}
