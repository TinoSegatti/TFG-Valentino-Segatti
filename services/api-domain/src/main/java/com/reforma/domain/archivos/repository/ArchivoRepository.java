package com.reforma.domain.archivos.repository;

import com.reforma.domain.archivos.domain.TipoModuloArchivo;
import com.reforma.domain.archivos.entity.Archivo;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ArchivoRepository extends JpaRepository<Archivo, Long> {

    List<Archivo> findByIdGranjaOrderByFechaCreacionDesc(String idGranja);

    List<Archivo> findByIdGranjaAndTipoModuloOrderByFechaCreacionDesc(
            String idGranja, TipoModuloArchivo tipoModulo);

    Optional<Archivo> findByIdAndIdGranja(Long id, String idGranja);

    long countByIdGranja(String idGranja);

    boolean existsByIdGranjaAndTipoModuloAndCodigoArchivoIgnoreCase(
            String idGranja, TipoModuloArchivo tipoModulo, String codigoArchivo);
}
