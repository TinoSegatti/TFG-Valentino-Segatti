package com.reforma.domain.compras.repository;

import com.reforma.domain.compras.domain.EstadoCompra;
import com.reforma.domain.compras.entity.CompraDetalle;
import com.reforma.domain.compras.support.CompraTotalesMateriaPrima;
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
}
