package com.reforma.domain.formulas.support;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Calculos de kg, porcentaje y costo para formulas (lote 1000 kg). */
public final class FormulaCalculo {

    public static final double PESO_LOTE_KG = 1000.0;
    /** Cantidades, porcentajes y costos del detalle se redondean a 2 decimales. */
    public static final int DECIMALES = 2;
    /** Tolerancia de cierre del lote acorde a la precision de 2 decimales. */
    public static final double TOLERANCIA_KG = 0.01;

    private FormulaCalculo() {}

    public static double redondear(double valor) {
        return BigDecimal.valueOf(valor).setScale(DECIMALES, RoundingMode.HALF_UP).doubleValue();
    }

    public static double calcularPorcentaje(double cantidadKg, double pesoTotal) {
        if (pesoTotal == 0.0) {
            return 0.0;
        }
        return redondear((cantidadKg / pesoTotal) * 100.0);
    }

    public static double calcularCostoParcial(double cantidadKg, double precioPorKilo) {
        return redondear(cantidadKg * precioPorKilo);
    }

    public static boolean sumaKgCompleta(double sumaKg) {
        return Math.abs(sumaKg - PESO_LOTE_KG) <= TOLERANCIA_KG + 1e-9;
    }

    public static double kilosFaltantes(double sumaKg) {
        return redondear(Math.max(0.0, PESO_LOTE_KG - sumaKg));
    }
}
