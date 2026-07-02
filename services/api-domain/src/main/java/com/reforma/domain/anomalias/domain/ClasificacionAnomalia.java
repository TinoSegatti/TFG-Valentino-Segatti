package com.reforma.domain.anomalias.domain;

/**
 * Clasificación de un precio de compra según su desviación respecto al historial (RF-IA-ANOM-002).
 *
 * <p>Los cuatro primeros valores coinciden, por nombre, con los que devuelve api-ml.
 * {@link #SIN_EVALUAR} es exclusivo del dominio: se usa cuando api-ml no respondió (fail-open).
 */
public enum ClasificacionAnomalia {
    SIN_HISTORIAL,
    NORMAL,
    ATENCION,
    ANOMALIA_ALTA,
    SIN_EVALUAR;

    public boolean esPersistible() {
        return this == ATENCION || this == ANOMALIA_ALTA;
    }

    public boolean requiereConfirmacion() {
        return this == ANOMALIA_ALTA;
    }

    public static ClasificacionAnomalia desde(String valor) {
        if (valor == null) {
            return SIN_EVALUAR;
        }
        try {
            return ClasificacionAnomalia.valueOf(valor);
        } catch (IllegalArgumentException e) {
            return SIN_EVALUAR;
        }
    }
}
