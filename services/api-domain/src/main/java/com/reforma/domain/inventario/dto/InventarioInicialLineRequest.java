package com.reforma.domain.inventario.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

public record InventarioInicialLineRequest(
        @NotNull Long idMateriaPrima,
        @NotNull @PositiveOrZero Double cantidadInicial,
        @NotNull @PositiveOrZero Double precioInicial) {}
