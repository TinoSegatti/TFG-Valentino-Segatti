package com.reforma.domain.materiasprimas.repository;

import com.reforma.domain.materiasprimas.entity.MateriaPrima;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MateriaPrimaRepository extends JpaRepository<MateriaPrima, String> {

    List<MateriaPrima> findByGranjaIdAndActivaTrueOrderByNombreMateriaPrimaAsc(String idGranja);

    Optional<MateriaPrima> findByIdAndGranjaId(String id, String idGranja);

    boolean existsByGranjaIdAndCodigoMateriaPrimaIgnoreCase(String idGranja, String codigo);

    long countByGranjaIdAndActivaTrue(String idGranja);
}
