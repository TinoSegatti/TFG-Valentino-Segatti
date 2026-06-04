package com.reforma.domain.proveedores.repository;

import com.reforma.domain.proveedores.entity.Proveedor;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProveedorRepository extends JpaRepository<Proveedor, Long> {

    /** Lista alfabéticamente los proveedores activos de una granja. */
    List<Proveedor> findByGranjaIdAndActivoTrueOrderByNombreProveedorAsc(String idGranja);

    /**
     * Búsqueda case-insensitive por subcadena del nombre (RF-PROV-002 — filtrable por nombre).
     */
    List<Proveedor> findByGranjaIdAndActivoTrueAndNombreProveedorContainingIgnoreCaseOrderByNombreProveedorAsc(
            String idGranja, String fragmentoNombre);

    Optional<Proveedor> findByIdAndGranjaId(Long id, String idGranja);

    /**
     * Chequeo de unicidad para alta/actualización: solo cuenta colisión con proveedores
     * ACTIVOS. Los inactivos son histórico congelado y sus códigos pueden reutilizarse (V003).
     */
    boolean existsByGranjaIdAndCodigoProveedorIgnoreCaseAndActivoTrue(
            String idGranja, String codigo);

    Optional<Proveedor> findByGranjaIdAndCodigoProveedorIgnoreCaseAndActivoTrue(
            String idGranja, String codigo);

    long countByGranjaIdAndActivoTrue(String idGranja);
}
