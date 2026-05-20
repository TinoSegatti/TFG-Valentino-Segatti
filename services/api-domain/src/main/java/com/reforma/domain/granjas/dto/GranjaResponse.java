package com.reforma.domain.granjas.dto;

import com.reforma.domain.granjas.entity.Granja;
import java.time.Instant;

public record GranjaResponse(
        String id,
        String nombreGranja,
        String descripcion,
        Instant fechaCreacion,
        boolean activa) {

    public static GranjaResponse from(Granja g) {
        return new GranjaResponse(
                g.getId(),
                g.getNombreGranja(),
                g.getDescripcion(),
                g.getFechaCreacion(),
                Boolean.TRUE.equals(g.getActiva()));
    }
}
