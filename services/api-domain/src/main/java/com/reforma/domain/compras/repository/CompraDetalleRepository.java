package com.reforma.domain.compras.repository;

import com.reforma.domain.compras.domain.EstadoCompra;
import com.reforma.domain.compras.entity.CompraDetalle;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CompraDetalleRepository extends JpaRepository<CompraDetalle, String> {

    /**
     * Línea de compra más reciente por fecha de factura para una MP (catálogo vigente).
     * Desempate estable por id de compra.
     */
    @Query(
            """
            SELECT cd FROM CompraDetalle cd
            JOIN cd.compra c
            WHERE c.granja.id = :idGranja
              AND c.activo = true
              AND c.estado = :estado
              AND cd.materiaPrima.id = :idMateriaPrima
            ORDER BY c.fechaCompra DESC, c.id DESC
            """)
    List<CompraDetalle> findMasRecientePorMateriaPrima(
            @Param("idGranja") String idGranja,
            @Param("idMateriaPrima") Long idMateriaPrima,
            @Param("estado") EstadoCompra estado,
            Pageable pageable);
}
