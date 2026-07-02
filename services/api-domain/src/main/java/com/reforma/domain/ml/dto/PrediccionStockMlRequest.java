package com.reforma.domain.ml.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/** Request hacia api-ml {@code POST /api/ml/prediccion/stock} (campos snake_case del schema Pydantic). */
public record PrediccionStockMlRequest(
        List<ItemPrediccionMl> items,
        @JsonProperty("incluir_series") boolean incluirSeries,
        @JsonProperty("meses_proyeccion") int mesesProyeccion) {}
