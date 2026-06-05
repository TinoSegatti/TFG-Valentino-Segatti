package com.reforma.domain.inventario.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

public record ActualizarCantidadRealRequest(
        @NotNull @PositiveOrZero Double cantidadReal, String observaciones) {}
