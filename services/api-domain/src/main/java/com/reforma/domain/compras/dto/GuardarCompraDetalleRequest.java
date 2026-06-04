package com.reforma.domain.compras.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

public record GuardarCompraDetalleRequest(
        @NotNull @Size(min = 1) @Valid List<CompraDetalleLineRequest> lineas) {}
