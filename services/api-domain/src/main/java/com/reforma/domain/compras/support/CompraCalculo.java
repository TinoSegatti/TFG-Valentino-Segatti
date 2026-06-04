package com.reforma.domain.compras.support;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Utilidades de redondeo y tolerancia para líneas y totales de compra (3 decimales, ±0,50). */
public final class CompraCalculo {

    public static final int DECIMALES = 3;
    public static final double TOLERANCIA_MONETARIA = 0.50;

    private CompraCalculo() {}

    public static double redondear(double valor) {
        return BigDecimal.valueOf(valor).setScale(DECIMALES, RoundingMode.HALF_UP).doubleValue();
    }

    public static boolean dentroDeTolerancia(double esperado, double actual) {
        return Math.abs(esperado - actual) <= TOLERANCIA_MONETARIA + 1e-9;
    }

    public static double calcularSubtotal(double cantidadKg, double precioPorKilo) {
        return redondear(cantidadKg * precioPorKilo);
    }

    public static double calcularCantidad(double subtotal, double precioPorKilo) {
        if (precioPorKilo == 0) {
            return 0;
        }
        return redondear(subtotal / precioPorKilo);
    }
}
