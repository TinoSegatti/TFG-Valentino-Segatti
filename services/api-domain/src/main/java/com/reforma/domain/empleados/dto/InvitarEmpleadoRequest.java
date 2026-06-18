package com.reforma.domain.empleados.dto;

import com.reforma.domain.common.domain.RolEmpleado;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Alta de un empleado por invitación (Etapa 2). El dueño aporta los datos de identidad;
 * la contraseña la fija el propio empleado al aceptar (ver {@link AceptarInvitacionRequest}).
 */
public record InvitarEmpleadoRequest(
        @NotBlank @Email String email,
        @NotBlank @Size(max = 100) String nombreUsuario,
        @NotBlank @Size(max = 100) String apellidoUsuario,
        @NotNull RolEmpleado rol) {}
