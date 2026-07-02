package com.reforma.domain.prediccion.domain;

/**
 * Nivel de riesgo de agotamiento de una materia prima (RF-IA-PRED). Los valores coinciden, por
 * nombre, con los que devuelve api-ml. {@link #SIN_DATOS} cubre además el caso fail-open (api-ml no
 * respondió o historial insuficiente).
 */
public enum NivelAlertaStock {
    SIN_DATOS,
    SIN_RIESGO,
    NORMAL,
    ATENCION,
    ALERTA,
    CRITICO;

    public static NivelAlertaStock desde(String valor) {
        if (valor == null) {
            return SIN_DATOS;
        }
        try {
            return NivelAlertaStock.valueOf(valor);
        } catch (IllegalArgumentException e) {
            return SIN_DATOS;
        }
    }
}
