package com.reforma.domain.anomalias.dto;

import com.reforma.domain.anomalias.domain.ClasificacionAnomalia;
import com.reforma.domain.anomalias.support.AnomaliaMensaje;
import com.reforma.domain.ml.dto.AnomaliaMlResponse;

/** Resultado de evaluar un precio (preview en el formulario de compra). */
public record AnomaliaEvaluacionResponse(
        String clasificacion,
        String mensaje,
        boolean requiereConfirmacion,
        Double zScore,
        Double promedioHistorico,
        Double minHistorico,
        Double maxHistorico,
        Double desviacionPct,
        int nMuestras) {

    public static AnomaliaEvaluacionResponse desde(String nombreMp, double precio, AnomaliaMlResponse ml) {
        ClasificacionAnomalia clasif = ClasificacionAnomalia.desde(ml.clasificacion());
        return new AnomaliaEvaluacionResponse(
                clasif.name(),
                AnomaliaMensaje.construir(clasif, nombreMp, precio, ml),
                clasif.requiereConfirmacion(),
                ml.zScore(),
                ml.promedioHistorico(),
                ml.minHistorico(),
                ml.maxHistorico(),
                ml.desviacionPct(),
                ml.nMuestras());
    }

    /** Fail-open: api-ml no respondió. No bloquea ni pide confirmación. */
    public static AnomaliaEvaluacionResponse sinEvaluar(String nombreMp) {
        return new AnomaliaEvaluacionResponse(
                ClasificacionAnomalia.SIN_EVALUAR.name(),
                "No se pudo evaluar el precio en este momento.",
                false,
                null, null, null, null, null, 0);
    }
}
