package com.reforma.domain.fabricaciones.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

public record GuardarFabricacionDetalleRequest(
        @NotBlank String idFormula, @Positive Double veces) {}
