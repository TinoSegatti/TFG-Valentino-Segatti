package com.reforma.domain.fabricaciones.repository;

import com.reforma.domain.fabricaciones.dto.MateriaPrimaConsumoResponse;
import com.reforma.domain.fabricaciones.entity.FabricacionDetalle;
import com.reforma.domain.prediccion.support.AgregadoMensualMateria;
import java.time.Instant;
import java.util.List;
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

    /**
     * Suma los kilos consumidos de cada materia prima en TODAS las fabricaciones REGISTRADAS y
     * activas de la granja (agregación para el gráfico de consumo). Ordena de mayor a menor.
     */
    @Query(
            """
            SELECT new com.reforma.domain.fabricaciones.dto.MateriaPrimaConsumoResponse(
                df.materiaPrima.codigoMateriaPrima,
                df.materiaPrima.nombreMateriaPrima,
                SUM(df.cantidadUsada))
            FROM FabricacionDetalle df
            JOIN df.fabricacion f
            WHERE f.granja.id = :idGranja
              AND f.activo = true
              AND f.estado = com.reforma.domain.fabricaciones.domain.EstadoFabricacion.REGISTRADA
            GROUP BY df.materiaPrima.id, df.materiaPrima.codigoMateriaPrima, df.materiaPrima.nombreMateriaPrima
            ORDER BY SUM(df.cantidadUsada) DESC
            """)
    List<MateriaPrimaConsumoResponse> agregarConsumoMaterias(@Param("idGranja") String idGranja);

    /**
     * Consumo (kilos usados) por materia prima y mes ({@code "YYYY-MM"}, UTC) desde {@code desde},
     * sobre fabricaciones REGISTRADAS y activas de la granja. Base de la serie mensual de la
     * predicción de agotamiento (RF-IA-PRED).
     */
    @Query(
            """
            SELECT new com.reforma.domain.prediccion.support.AgregadoMensualMateria(
                df.materiaPrima.id,
                CAST(FUNCTION('to_char', f.fechaFabricacion, 'YYYY-MM') AS string),
                SUM(df.cantidadUsada))
            FROM FabricacionDetalle df
            JOIN df.fabricacion f
            WHERE f.granja.id = :idGranja
              AND f.activo = true
              AND f.estado = com.reforma.domain.fabricaciones.domain.EstadoFabricacion.REGISTRADA
              AND f.fechaFabricacion >= :desde
            GROUP BY df.materiaPrima.id, CAST(FUNCTION('to_char', f.fechaFabricacion, 'YYYY-MM') AS string)
            """)
    List<AgregadoMensualMateria> consumosMensualesPorMateria(
            @Param("idGranja") String idGranja, @Param("desde") Instant desde);
}
