package com.reforma.domain.auditoria.dto;

import com.reforma.domain.auditoria.domain.AccionAuditoria;
import com.reforma.domain.auditoria.entity.Auditoria;
import com.reforma.domain.usuarios.entity.Usuario;
import java.time.Instant;

/**
 * Vista de solo lectura de un registro de auditoría para la consola (RF-AUD, Etapa 5).
 * {@code actorEmail}/{@code actorNombre} se resuelven a partir de {@code idUsuario} para no
 * obligar al frontend a cruzar ids. Los datos antes/después se exponen como JSON crudo.
 */
public record AuditoriaResponse(
        String id,
        String idUsuario,
        String actorEmail,
        String actorNombre,
        String idGranja,
        String tablaOrigen,
        String idRegistro,
        AccionAuditoria accion,
        String descripcion,
        String datosAnteriores,
        String datosNuevos,
        Instant fechaOperacion,
        String ipAddress,
        String userAgent) {

    /** {@code actor} puede ser null si el usuario fue borrado: se degrada a solo el id. */
    public static AuditoriaResponse from(Auditoria a, Usuario actor) {
        var nombre = actor == null
                ? null
                : (actor.getNombreUsuario() + " " + actor.getApellidoUsuario()).trim();
        return new AuditoriaResponse(
                a.getId(),
                a.getIdUsuario(),
                actor == null ? null : actor.getEmail(),
                nombre,
                a.getIdGranja(),
                a.getTablaOrigen(),
                a.getIdRegistro(),
                a.getAccion(),
                a.getDescripcion(),
                a.getDatosAnterioresJson(),
                a.getDatosNuevosJson(),
                a.getFechaOperacion(),
                a.getIpAddress(),
                a.getUserAgent());
    }
}
