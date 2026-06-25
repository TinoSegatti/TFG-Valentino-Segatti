package com.reforma.domain.inventario.support;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Calculos de cantidades, merma, valor de stock y precio almacen. */
public final class InventarioCalculo {

    public static final int DECIMALES = 3;

    private InventarioCalculo() {}

    public static double redondear(double valor) {
        return BigDecimal.valueOf(valor).setScale(DECIMALES, RoundingMode.HALF_UP).doubleValue();
    }

    /** merma = cantidad_sistema - cantidad_real. */
    public static double merma(double cantidadSistema, double cantidadReal) {
        return redondear(cantidadSistema - cantidadReal);
    }

    /** valor_stock = max(0, cantidad_real) * precio_por_kilo (ultimo precio registrado). */
    public static double valorStock(double cantidadReal, double precioPorKilo) {
        double real = Math.max(0.0, cantidadReal);
        return redondear(real * precioPorKilo);
    }

    /**
     * precio_almacen = gasto acumulado / kilos comprados acumulados (costo promedio ponderado).
     *
     * <p>El gasto acumulado es el valor del stock inicial (cantidad_inicial * precio_inicial) más la
     * suma de subtotales de las compras REGISTRADAS; los kilos acumulados son la cantidad inicial más
     * los kilos comprados. No interviene el consumo por fabricaciones. Devuelve 0 si no hay kilos.
     */
    public static double precioAlmacenPonderado(double gastoAcumulado, double kilosAcumulados) {
        if (kilosAcumulados <= 0.0) {
            return 0.0;
        }
        return redondear(gastoAcumulado / kilosAcumulados);
    }
}
