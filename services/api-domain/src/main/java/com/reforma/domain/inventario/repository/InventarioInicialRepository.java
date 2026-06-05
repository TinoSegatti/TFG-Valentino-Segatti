package com.reforma.domain.inventario.repository;

import com.reforma.domain.inventario.entity.InventarioInicial;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InventarioInicialRepository extends JpaRepository<InventarioInicial, String> {

    List<InventarioInicial> findByGranjaId(String idGranja);

    Optional<InventarioInicial> findByGranjaIdAndMateriaPrimaId(String idGranja, Long idMateriaPrima);

    void deleteByGranjaId(String idGranja);

    long countByGranjaId(String idGranja);
}
