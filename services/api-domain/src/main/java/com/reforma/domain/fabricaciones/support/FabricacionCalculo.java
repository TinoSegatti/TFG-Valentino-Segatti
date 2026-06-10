package com.reforma.domain.fabricaciones.support;

import com.reforma.domain.formulas.support.FormulaCalculo;
import java.math.BigDecimal;
import java.math.RoundingMode;

public final class FabricacionCalculo {

    public static final int DECIMALES = 3;

    private FabricacionCalculo() {}

    public static double redondear(double valor) {
        return BigDecimal.valueOf(valor).setScale(DECIMALES, RoundingMode.HALF_UP).doubleValue();
    }

    /** 1 vez = lote de 1000 kg de producto terminado. */
    public static double vecesToKg(double veces) {
        return redondear(veces * FormulaCalculo.PESO_LOTE_KG);
    }

    public static double kgToVeces(double kilos) {
        if (kilos <= 0.0) {
            return 0.0;
        }
        return redondear(kilos / FormulaCalculo.PESO_LOTE_KG);
    }

    /** Kg de MP consumidos = kg de la linea de formula (por 1 vez) x veces fabricadas. */
    public static double cantidadUsada(double cantidadKgFormula, double veces) {
        return redondear(cantidadKgFormula * veces);
    }

    public static double costoTotal(double costoUnitarioFormula, double veces) {
        return redondear(costoUnitarioFormula * veces);
    }

    public static double costoPorKilo(double costoTotal, double kilosProducidos) {
        if (kilosProducidos <= 0.0) {
            return 0.0;
        }
        return redondear(costoTotal / kilosProducidos);
    }
}
