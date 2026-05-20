package com.reforma.domain.usuarios.dto;

import com.reforma.domain.common.domain.PlanSuscripcion;
import com.reforma.domain.common.domain.TipoUsuario;
import com.reforma.domain.usuarios.entity.Usuario;

public record UsuarioResponse(
        String id,
        String email,
        String nombreUsuario,
        String apellidoUsuario,
        TipoUsuario tipoUsuario,
        PlanSuscripcion planSuscripcion,
        int maxGranjas,
        boolean emailVerificado) {

    public static UsuarioResponse from(Usuario u) {
        return new UsuarioResponse(
                u.getId(),
                u.getEmail(),
                u.getNombreUsuario(),
                u.getApellidoUsuario(),
                u.getTipoUsuario(),
                u.getPlanSuscripcion(),
                u.getMaxGranjas(),
                Boolean.TRUE.equals(u.getEmailVerificado()));
    }
}
