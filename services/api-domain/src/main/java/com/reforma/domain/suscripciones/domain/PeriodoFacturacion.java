package com.reforma.domain.suscripciones.domain;

import java.time.Instant;
import java.time.ZoneOffset;

/** Período de facturación de una suscripción. Anual = mensual × factor de config (RD-P12). */
public enum PeriodoFacturacion {
    MENSUAL,
    ANUAL;

    /** Fin del ciclo que arranca en {@code desde} (mes/año calendario en UTC). */
    public Instant finDeCiclo(Instant desde) {
        var inicio = desde.atZone(ZoneOffset.UTC);
        return (this == MENSUAL ? inicio.plusMonths(1) : inicio.plusYears(1)).toInstant();
    }

    /** Etiqueta humana para descripciones de pago ("Mensual"/"Anual"). */
    public String etiqueta() {
        return this == MENSUAL ? "Mensual" : "Anual";
    }
}
