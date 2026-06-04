package com.reforma.domain.materiasprimas.repository;

import com.reforma.domain.materiasprimas.entity.MateriaPrima;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MateriaPrimaRepository extends JpaRepository<MateriaPrima, Long> {

    List<MateriaPrima> findByGranjaIdAndActivaTrueOrderByNombreMateriaPrimaAsc(String idGranja);

    Optional<MateriaPrima> findByIdAndGranjaId(Long id, String idGranja);

    /**
     * Chequeo de unicidad para alta/actualización: solo cuenta colisión con MPs ACTIVAS.
     * Las inactivas son históricos congelados, sus códigos pueden reutilizarse (V003).
     */
    boolean existsByGranjaIdAndCodigoMateriaPrimaIgnoreCaseAndActivaTrue(
            String idGranja, String codigo);

    Optional<MateriaPrima> findByGranjaIdAndCodigoMateriaPrimaIgnoreCaseAndActivaTrue(
            String idGranja, String codigo);

    long countByGranjaIdAndActivaTrue(String idGranja);
}
