package com.reforma.domain.animales.dto;

import com.reforma.domain.animales.entity.Animal;
import java.time.Instant;

public record AnimalResponse(
        Long id,
        String idGranja,
        String codigoAnimal,
        String descripcionAnimal,
        String categoriaAnimal,
        String observaciones,
        boolean activo,
        Instant fechaCreacion,
        Instant fechaUltimaActualizacion) {

    public static AnimalResponse from(Animal a) {
        return new AnimalResponse(
                a.getId(),
                a.getGranja().getId(),
                a.getCodigoAnimal(),
                a.getDescripcionAnimal(),
                a.getCategoriaAnimal(),
                a.getObservaciones(),
                Boolean.TRUE.equals(a.getActivo()),
                a.getFechaCreacion(),
                a.getFechaUltimaActualizacion());
    }
}
