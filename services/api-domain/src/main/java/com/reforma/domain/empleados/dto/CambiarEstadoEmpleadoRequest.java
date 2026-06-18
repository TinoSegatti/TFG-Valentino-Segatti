package com.reforma.domain.empleados.dto;

import jakarta.validation.constraints.NotNull;

/**
 * Activación/desactivación (baja lógica) de un empleado (Etapa 4, ADR-0005).
 * {@code activo=false} desvincula sin borrar la cuenta; {@code true} lo reactiva.
 */
public record CambiarEstadoEmpleadoRequest(@NotNull Boolean activo) {}
