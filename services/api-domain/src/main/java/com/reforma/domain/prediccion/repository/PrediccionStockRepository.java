package com.reforma.domain.prediccion.repository;

import com.reforma.domain.prediccion.entity.PrediccionStock;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PrediccionStockRepository extends JpaRepository<PrediccionStock, String> {

    /** Fila vigente de una MP en la granja (para el upsert find-then-save). */
    Optional<PrediccionStock> findByGranjaIdAndMateriaPrimaId(String idGranja, Long idMateriaPrima);
}
