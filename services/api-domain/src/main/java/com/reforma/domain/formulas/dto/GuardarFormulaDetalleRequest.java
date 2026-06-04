package com.reforma.domain.formulas.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

public record GuardarFormulaDetalleRequest(@NotEmpty List<@Valid FormulaDetalleLineRequest> lineas) {}
