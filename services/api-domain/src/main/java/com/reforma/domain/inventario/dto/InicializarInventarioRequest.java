package com.reforma.domain.inventario.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

public record InicializarInventarioRequest(
        @NotEmpty List<@Valid InventarioInicialLineRequest> lineas) {}
