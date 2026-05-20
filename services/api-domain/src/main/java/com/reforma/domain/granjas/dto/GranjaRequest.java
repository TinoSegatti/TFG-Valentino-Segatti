package com.reforma.domain.granjas.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record GranjaRequest(
        @NotBlank @Size(max = 200) String nombreGranja,
        @Size(max = 2000) String descripcion) {}
