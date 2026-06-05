package com.reforma.domain.compras.support;

/** Totales de compras REGISTRADAS para una materia prima en una granja. */
public record CompraTotalesMateriaPrima(double totalKilos, double totalDinero) {

    public static CompraTotalesMateriaPrima vacio() {
        return new CompraTotalesMateriaPrima(0.0, 0.0);
    }
}
