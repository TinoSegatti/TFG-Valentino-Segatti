package com.reforma.domain.fabricaciones.support;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class FabricacionCalculoTest {

    @Test
    void vecesToKg_y_kgToVeces() {
        assertThat(FabricacionCalculo.vecesToKg(3.5)).isEqualTo(3500.0);
        assertThat(FabricacionCalculo.kgToVeces(3500.0)).isEqualTo(3.5);
    }

    @Test
    void cantidadUsada_formula500kg_tresYMedioVeces() {
        assertThat(FabricacionCalculo.cantidadUsada(500.0, 3.5)).isEqualTo(1750.0);
    }

    @Test
    void costoTotal_congelaCostoUnitario() {
        assertThat(FabricacionCalculo.costoTotal(12000.0, 3.5)).isEqualTo(42000.0);
    }
}
