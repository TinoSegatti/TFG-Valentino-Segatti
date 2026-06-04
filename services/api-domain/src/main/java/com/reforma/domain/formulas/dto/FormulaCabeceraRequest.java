package com.reforma.domain.formulas.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record FormulaCabeceraRequest(
        @NotBlank @Size(max = 50) String codigoFormula,
        @NotBlank @Size(max = 200) String descripcionFormula,
        @NotNull Long idAnimal) {}
