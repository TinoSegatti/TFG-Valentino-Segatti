package com.reforma.domain.formulas.repository;

import com.reforma.domain.formulas.entity.FormulaCabecera;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FormulaCabeceraRepository extends JpaRepository<FormulaCabecera, String> {

    List<FormulaCabecera> findByGranjaIdAndActivaTrueOrderByCodigoFormulaAsc(String idGranja);

    Optional<FormulaCabecera> findByIdAndGranjaIdAndActivaTrue(String id, String idGranja);

    boolean existsByGranjaIdAndCodigoFormulaIgnoreCaseAndActivaTrue(String idGranja, String codigoFormula);

    boolean existsByGranjaIdAndCodigoFormulaIgnoreCaseAndActivaTrueAndIdNot(
            String idGranja, String codigoFormula, String id);

    long countByGranjaIdAndActivaTrue(String idGranja);

    @Query(
            """
            SELECT DISTINCT f FROM FormulaCabecera f
            JOIN f.detalles d
            WHERE f.granja.id = :idGranja
              AND f.activa = true
              AND d.materiaPrima.id = :idMateriaPrima
            """)
    List<FormulaCabecera> findActivasConMateriaPrima(
            @Param("idGranja") String idGranja, @Param("idMateriaPrima") Long idMateriaPrima);
}
