package com.reforma.domain.usuarios.dto;

import com.reforma.domain.common.domain.PlanSuscripcion;
import java.util.List;

/**
 * Perfil del usuario autenticado para mostrar en la UI: identidad + rol efectivo + permisos.
 * El rol es {@code OWNER} para un dueño o el {@code rolEmpleado} (ADMIN/EDITOR/LECTOR) para un empleado.
 * {@code permisos} son descripciones legibles derivadas del rol (ver {@code PerfilService}).
 */
public record PerfilResponse(
        String id,
        String email,
        String nombre,
        String apellido,
        String rol,
        boolean esEmpleado,
        PlanSuscripcion plan,
        String idDueno,
        List<String> permisos) {}
