package com.reforma.domain.fabricaciones.repository;

import com.reforma.domain.fabricaciones.entity.FabricacionDetalle;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FabricacionDetalleRepository extends JpaRepository<FabricacionDetalle, String> {

    @Query(
            """
            SELECT COALESCE(SUM(df.cantidadUsada), 0.0)
            FROM FabricacionDetalle df
            JOIN df.fabricacion f
            WHERE f.granja.id = :idGranja
              AND f.activo = true
              AND f.estado = com.reforma.domain.fabricaciones.domain.EstadoFabricacion.REGISTRADA
              AND df.materiaPrima.id = :idMateriaPrima
            """)
    Double sumCantidadUsadaPorMateriaPrima(
            @Param("idGranja") String idGranja, @Param("idMateriaPrima") Long idMateriaPrima);
}
