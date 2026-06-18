package com.reforma.domain.empleados.dto;

import com.reforma.domain.common.domain.RolEmpleado;
import jakarta.validation.constraints.NotNull;

/** Cambio de rol de un empleado (Etapa 4). Las reglas de quién puede asignar qué viven en el servicio. */
public record CambiarRolEmpleadoRequest(@NotNull RolEmpleado rol) {}
