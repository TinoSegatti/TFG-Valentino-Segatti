package com.reforma.domain.formulas.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record FormulaDetalleLineRequest(
        @NotNull Long idMateriaPrima, @NotNull @Positive Double cantidadKg) {}
