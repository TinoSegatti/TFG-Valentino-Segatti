package com.reforma.domain.animales.repository;

import com.reforma.domain.animales.entity.Animal;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AnimalRepository extends JpaRepository<Animal, Long> {

    /** Lista alfabéticamente los animales activos de una granja. */
    List<Animal> findByGranjaIdAndActivoTrueOrderByDescripcionAnimalAsc(String idGranja);

    /** Búsqueda por subcadena de descripción (case-insensitive). */
    List<Animal>
            findByGranjaIdAndActivoTrueAndDescripcionAnimalContainingIgnoreCaseOrderByDescripcionAnimalAsc(
                    String idGranja, String fragmento);

    Optional<Animal> findByIdAndGranjaId(Long id, String idGranja);

    /**
     * Chequeo de unicidad para alta/actualización: solo cuenta colisión con animales
     * ACTIVOS. Los inactivos son histórico congelado y sus códigos pueden reutilizarse
     * (ADR 0005 + V004).
     */
    boolean existsByGranjaIdAndCodigoAnimalIgnoreCaseAndActivoTrue(String idGranja, String codigo);

    /**
     * Busca un animal activo por su código de negocio dentro de la granja.
     * Útil para flujos donde el CSV de import referencia animales por código (no por id).
     */
    Optional<Animal> findByGranjaIdAndCodigoAnimalIgnoreCaseAndActivoTrue(
            String idGranja, String codigo);

    long countByGranjaIdAndActivoTrue(String idGranja);
}
