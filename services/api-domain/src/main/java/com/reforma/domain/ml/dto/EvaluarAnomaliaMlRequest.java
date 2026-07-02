package com.reforma.domain.ml.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/** Request hacia api-ml {@code POST /api/ml/anomalias/evaluar} (campos snake_case del schema Pydantic). */
public record EvaluarAnomaliaMlRequest(
        @JsonProperty("precio_ingresado") double precioIngresado,
        List<PuntoHistorialMl> historial,
        @JsonProperty("mes_referencia") Integer mesReferencia) {}
