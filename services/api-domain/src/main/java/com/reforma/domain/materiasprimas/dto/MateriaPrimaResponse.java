package com.reforma.domain.materiasprimas.dto;

import com.reforma.domain.materiasprimas.entity.MateriaPrima;
import java.time.Instant;

public record MateriaPrimaResponse(
        Long id,
        String idGranja,
        String codigoMateriaPrima,
        String nombreMateriaPrima,
        Double precioPorKilo,
        boolean activa,
        Instant fechaCreacion,
        Instant fechaUltimaActualizacion) {

    public static MateriaPrimaResponse from(MateriaPrima mp) {
        return new MateriaPrimaResponse(
                mp.getId(),
                mp.getGranja().getId(),
                mp.getCodigoMateriaPrima(),
                mp.getNombreMateriaPrima(),
                mp.getPrecioPorKilo(),
                Boolean.TRUE.equals(mp.getActiva()),
                mp.getFechaCreacion(),
                mp.getFechaUltimaActualizacion());
    }
}
