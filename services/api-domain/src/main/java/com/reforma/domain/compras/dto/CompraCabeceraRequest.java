package com.reforma.domain.compras.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public record CompraCabeceraRequest(
        @NotNull @Positive Long idProveedor,
        @NotBlank @Size(max = 100) String numeroFactura,
        @NotNull LocalDate fechaCompra,
        @NotNull @Positive Double totalFactura,
        @Size(max = 2000) String observaciones) {}
