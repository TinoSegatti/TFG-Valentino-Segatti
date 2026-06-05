package com.reforma.domain.inventario.support;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class InventarioCalculoTest {

    @Test
    void precioAlmacenPonderado_ejemplo() {
        // 100 kg a $10 + 50 kg a $12 = 1600 / 150 = 10.667
        double precio = InventarioCalculo.precioAlmacenPonderado(100.0 * 10.0 + 50.0 * 12.0, 150.0);
        assertThat(precio).isEqualTo(10.667);
    }

    @Test
    void precioAlmacenPonderado_sinKilosDevuelveCero() {
        assertThat(InventarioCalculo.precioAlmacenPonderado(0.0, 0.0)).isZero();
    }

    @Test
    void mermaYValorStock() {
        assertThat(InventarioCalculo.merma(100.0, 95.0)).isEqualTo(5.0);
        assertThat(InventarioCalculo.valorStock(50.0, 12.0)).isEqualTo(600.0);
        assertThat(InventarioCalculo.valorStock(-5.0, 12.0)).isZero();
    }
}
