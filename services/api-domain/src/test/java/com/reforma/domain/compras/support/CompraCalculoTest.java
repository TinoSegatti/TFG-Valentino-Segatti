package com.reforma.domain.compras.support;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class CompraCalculoTest {

    @Test
    @DisplayName("redondear: conserva 3 decimales con HALF_UP")
    void redondear_tresDecimales() {
        assertThat(CompraCalculo.redondear(10.1254)).isEqualTo(10.125);
        assertThat(CompraCalculo.redondear(10.1255)).isEqualTo(10.126);
        assertThat(CompraCalculo.redondear(35.5)).isEqualTo(35.5);
    }

    @Test
    @DisplayName("redondear: montos grandes (30 millones) sin perder escala")
    void redondear_montosGrandes() {
        assertThat(CompraCalculo.redondear(30_000_000.0)).isEqualTo(30_000_000.0);
        assertThat(CompraCalculo.redondear(15_000_000.499)).isEqualTo(15_000_000.499);
        assertThat(CompraCalculo.redondear(15_000_000.4995)).isEqualTo(15_000_000.5);
    }

    @Test
    @DisplayName("calcularSubtotal: cantidad × precio redondeado")
    void calcularSubtotal_decimales() {
        assertThat(CompraCalculo.calcularSubtotal(100.125, 300.333)).isEqualTo(30_070.842);
        assertThat(CompraCalculo.calcularSubtotal(10_000.0, 3_000.0)).isEqualTo(30_000_000.0);
    }

    @ParameterizedTest
    @CsvSource({
        "1000.0, 1000.0, true",
        "1000.0, 1000.49, true",
        "1000.0, 1000.5, true",
        "1000.0, 999.5, true",
        "1000.0, 999.49, false",
        "1000.0, 1000.51, false",
        "30000000.0, 30000000.0, true",
        "30000000.0, 29999999.6, true",
        "30000000.0, 29999999.4, false"
    })
    @DisplayName("dentroDeTolerancia: ±0,50 monetario")
    void dentroDeTolerancia_casos(double esperado, double actual, boolean valido) {
        assertThat(CompraCalculo.dentroDeTolerancia(esperado, actual)).isEqualTo(valido);
    }

    @Test
    @DisplayName("calcularCantidad: subtotal / precio con redondeo")
    void calcularCantidad_desdeSubtotal() {
        assertThat(CompraCalculo.calcularCantidad(30_000_000.0, 3_000.0)).isEqualTo(10_000.0);
        assertThat(CompraCalculo.calcularCantidad(1_000.0, 3.0)).isEqualTo(333.333);
    }
}
