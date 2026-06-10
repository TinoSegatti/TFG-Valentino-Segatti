package com.reforma.domain.fabricaciones.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public record FabricacionCabeceraRequest(
        @NotBlank @Size(max = 50) String codigoFabricacion,
        @NotNull LocalDate fechaFabricacion,
        @NotBlank @Size(max = 200) String descripcionFabricacion,
        @Size(max = 2000) String observaciones) {}
