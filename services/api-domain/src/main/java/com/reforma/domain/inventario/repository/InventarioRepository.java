package com.reforma.domain.inventario.repository;

import com.reforma.domain.inventario.entity.Inventario;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InventarioRepository extends JpaRepository<Inventario, String> {

    List<Inventario> findByGranjaIdOrderByMateriaPrimaCodigoMateriaPrimaAsc(String idGranja);

    Optional<Inventario> findByGranjaIdAndMateriaPrimaId(String idGranja, Long idMateriaPrima);

    Optional<Inventario> findByIdAndGranjaId(String id, String idGranja);

    long countByGranjaId(String idGranja);

    void deleteByGranjaId(String idGranja);
}
