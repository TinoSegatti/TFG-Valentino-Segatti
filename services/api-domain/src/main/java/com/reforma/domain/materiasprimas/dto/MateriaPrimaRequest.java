package com.reforma.domain.materiasprimas.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record MateriaPrimaRequest(
        @NotBlank @Size(max = 50) String codigoMateriaPrima,
        @NotBlank @Size(max = 200) String nombreMateriaPrima,
        @PositiveOrZero Double precioPorKilo) {}
