package com.reforma.domain.empleados.dto;

import com.reforma.domain.common.domain.RolEmpleado;
import com.reforma.domain.usuarios.entity.Usuario;
import java.time.Instant;

/**
 * Vista de un empleado para el dueño/jefe. {@code activoComoEmpleado=false} +
 * {@code fechaVinculacion=null} indica una invitación pendiente de aceptación.
 */
public record EmpleadoResponse(
        String id,
        String email,
        String nombreUsuario,
        String apellidoUsuario,
        RolEmpleado rolEmpleado,
        boolean emailVerificado,
        boolean activoComoEmpleado,
        Instant fechaVinculacion) {

    public static EmpleadoResponse from(Usuario u) {
        return new EmpleadoResponse(
                u.getId(),
                u.getEmail(),
                u.getNombreUsuario(),
                u.getApellidoUsuario(),
                u.getRolEmpleado(),
                Boolean.TRUE.equals(u.getEmailVerificado()),
                Boolean.TRUE.equals(u.getActivoComoEmpleado()),
                u.getFechaVinculacion());
    }
}
