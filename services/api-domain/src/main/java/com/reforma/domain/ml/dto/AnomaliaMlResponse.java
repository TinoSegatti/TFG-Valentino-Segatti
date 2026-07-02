package com.reforma.domain.ml.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/** Response de api-ml con la clasificación de la anomalía (campos snake_case del schema Pydantic). */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AnomaliaMlResponse(
        String clasificacion,
        @JsonProperty("z_score") Double zScore,
        @JsonProperty("promedio_historico") Double promedioHistorico,
        @JsonProperty("min_historico") Double minHistorico,
        @JsonProperty("max_historico") Double maxHistorico,
        @JsonProperty("desviacion_pct") Double desviacionPct,
        @JsonProperty("n_muestras") int nMuestras,
        String ventana) {}
