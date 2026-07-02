package com.reforma.domain.ml.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

/** Response de api-ml con la predicción por materia prima. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PrediccionStockMlResponse(List<PrediccionItemMl> predicciones) {}
