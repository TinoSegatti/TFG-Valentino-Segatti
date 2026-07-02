package com.reforma.domain.compras.repository;

import com.reforma.domain.compras.domain.EstadoCompra;
import com.reforma.domain.compras.dto.MateriaPrimaCompradaResponse;
import com.reforma.domain.compras.entity.CompraDetalle;
import com.reforma.domain.compras.support.CompraTotalesMateriaPrima;
import com.reforma.domain.prediccion.support.AgregadoMensualMateria;
import java.time.Instant;
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

    /**
     * Totales por materia prima en compras REGISTRADAS y activas de la granja.
     * Devuelve {@code [idMateriaPrima, totalKilos, totalDinero]} ordenado por MP.
     *
     * <p>El total de dinero acumula el {@code subtotal} guardado de cada línea (el gasto real de la
     * factura), no {@code cantidad * precioUnitario}, para respetar la tolerancia de redondeo con la
     * que el usuario carga el detalle de compra.
     */
    @Query(
            """
            SELECT cd.materiaPrima.id, SUM(cd.cantidadComprada), SUM(cd.subtotal)
            FROM CompraDetalle cd
            JOIN cd.compra c
            WHERE c.granja.id = :idGranja
              AND c.activo = true
              AND c.estado = :estado
            GROUP BY cd.materiaPrima.id
            """)
    List<Object[]> sumarTotalesPorMateriaPrima(
            @Param("idGranja") String idGranja, @Param("estado") EstadoCompra estado);

    /**
     * Total {kilos, dinero} de compras REGISTRADAS para una MP.
     *
     * <p>{@code totalDinero} suma el {@code subtotal} de cada línea (gasto real de la factura), base
     * del acumulador de gasto del precio almacén; {@code totalKilos} suma {@code cantidadComprada}.
     */
    @Query(
            """
            SELECT new com.reforma.domain.compras.support.CompraTotalesMateriaPrima(
                COALESCE(SUM(cd.cantidadComprada), 0.0),
                COALESCE(SUM(cd.subtotal), 0.0)
            )
            FROM CompraDetalle cd
            JOIN cd.compra c
            WHERE c.granja.id = :idGranja
              AND c.activo = true
              AND c.estado = :estado
              AND cd.materiaPrima.id = :idMateriaPrima
            """)
    CompraTotalesMateriaPrima totalPorMateriaPrima(
            @Param("idGranja") String idGranja,
            @Param("idMateriaPrima") Long idMateriaPrima,
            @Param("estado") EstadoCompra estado);

    /**
     * Suma los kilos comprados de cada materia prima en TODAS las compras REGISTRADAS y activas de la
     * granja (agregación para el gráfico "materias primas más compradas"). Ordena de mayor a menor.
     */
    @Query(
            """
            SELECT new com.reforma.domain.compras.dto.MateriaPrimaCompradaResponse(
                cd.materiaPrima.codigoMateriaPrima,
                cd.materiaPrima.nombreMateriaPrima,
                SUM(cd.cantidadComprada))
            FROM CompraDetalle cd
            JOIN cd.compra c
            WHERE c.granja.id = :idGranja
              AND c.activo = true
              AND c.estado = :estado
            GROUP BY cd.materiaPrima.id, cd.materiaPrima.codigoMateriaPrima, cd.materiaPrima.nombreMateriaPrima
            ORDER BY SUM(cd.cantidadComprada) DESC
            """)
    List<MateriaPrimaCompradaResponse> agregarComprasMaterias(
            @Param("idGranja") String idGranja, @Param("estado") EstadoCompra estado);

    /**
     * Ingresos (kilos comprados) por materia prima y mes ({@code "YYYY-MM"}, UTC) desde {@code desde},
     * sobre compras REGISTRADAS y activas de la granja. Base de la serie mensual de la predicción de
     * agotamiento (RF-IA-PRED).
     */
    @Query(
            """
            SELECT new com.reforma.domain.prediccion.support.AgregadoMensualMateria(
                cd.materiaPrima.id,
                CAST(FUNCTION('to_char', c.fechaCompra, 'YYYY-MM') AS string),
                SUM(cd.cantidadComprada))
            FROM CompraDetalle cd
            JOIN cd.compra c
            WHERE c.granja.id = :idGranja
              AND c.activo = true
              AND c.estado = :estado
              AND c.fechaCompra >= :desde
            GROUP BY cd.materiaPrima.id, CAST(FUNCTION('to_char', c.fechaCompra, 'YYYY-MM') AS string)
            """)
    List<AgregadoMensualMateria> ingresosMensualesPorMateria(
            @Param("idGranja") String idGranja,
            @Param("estado") EstadoCompra estado,
            @Param("desde") Instant desde);
}
