package com.reforma.domain.inventario.dto;

import java.util.List;

public record InventarioListadoResponse(boolean inicializado, List<InventarioResponse> items) {}
