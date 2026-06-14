package com.reforma.domain.auditoria.dto;

import com.reforma.domain.auditoria.domain.AccionAuditoria;
import lombok.Builder;

/**
 * Datos de un evento a auditar. {@code datosAnteriores}/{@code datosNuevos} son objetos
 * arbitrarios que {@code AuditoriaService} serializa a JSON (jamás incluir secretos:
 * password hash, tokens en claro, etc.).
 */
@Builder
public record AuditoriaEvento(
        String idUsuario,
        String idGranja,
        String tablaOrigen,
        String idRegistro,
        AccionAuditoria accion,
        String descripcion,
        Object datosAnteriores,
        Object datosNuevos) {}
