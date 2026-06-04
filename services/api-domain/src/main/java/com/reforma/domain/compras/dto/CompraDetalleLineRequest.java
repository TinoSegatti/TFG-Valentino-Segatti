package com.reforma.domain.compras.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

public record CompraDetalleLineRequest(
        @NotNull @Positive Long idMateriaPrima,
        @NotNull @PositiveOrZero Double cantidadKg,
        @NotNull @PositiveOrZero Double precioPorKilo,
        @NotNull @PositiveOrZero Double subtotal) {}
