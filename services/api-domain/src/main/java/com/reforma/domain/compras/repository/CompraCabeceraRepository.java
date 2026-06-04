package com.reforma.domain.compras.repository;

import com.reforma.domain.compras.entity.CompraCabecera;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CompraCabeceraRepository extends JpaRepository<CompraCabecera, String> {

    @EntityGraph(attributePaths = {"proveedor", "detalles"})
    List<CompraCabecera> findByGranjaIdAndActivoTrueOrderByFechaCompraDesc(String idGranja);

    @EntityGraph(attributePaths = {"proveedor", "detalles", "detalles.materiaPrima"})
    Optional<CompraCabecera> findByIdAndGranjaIdAndActivoTrue(String id, String idGranja);
}
