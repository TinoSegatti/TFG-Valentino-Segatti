package com.reforma.domain.anomalias.repository;

import com.reforma.domain.anomalias.entity.AnomaliaPrecio;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AnomaliaPrecioRepository extends JpaRepository<AnomaliaPrecio, String> {

    @Query("select a from AnomaliaPrecio a "
            + "where a.compra.granja.id = :idGranja "
            + "order by a.detectadoEn desc")
    List<AnomaliaPrecio> listarPorGranja(@Param("idGranja") String idGranja, Pageable pageable);

    @Query("select a from AnomaliaPrecio a "
            + "where a.compra.granja.id = :idGranja and a.compra.proveedor.id = :idProveedor "
            + "order by a.detectadoEn desc")
    List<AnomaliaPrecio> listarPorProveedor(
            @Param("idGranja") String idGranja,
            @Param("idProveedor") Long idProveedor,
            Pageable pageable);

    Optional<AnomaliaPrecio> findByIdAndCompra_Granja_Id(String id, String idGranja);
}
