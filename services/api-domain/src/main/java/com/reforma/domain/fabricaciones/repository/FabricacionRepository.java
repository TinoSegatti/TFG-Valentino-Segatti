package com.reforma.domain.fabricaciones.repository;

import com.reforma.domain.fabricaciones.entity.Fabricacion;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FabricacionRepository extends JpaRepository<Fabricacion, String> {

    List<Fabricacion> findByGranjaIdAndActivoTrueOrderByFechaFabricacionDesc(String idGranja);

    Optional<Fabricacion> findByIdAndGranjaIdAndActivoTrue(String id, String idGranja);

    boolean existsByGranjaIdAndCodigoFabricacionIgnoreCaseAndActivoTrue(String idGranja, String codigo);

    boolean existsByGranjaIdAndCodigoFabricacionIgnoreCaseAndActivoTrueAndIdNot(
            String idGranja, String codigo, String id);

    long countByGranjaIdAndActivoTrue(String idGranja);
}
