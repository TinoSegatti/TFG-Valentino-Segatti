package com.reforma.domain.granjas.repository;

import com.reforma.domain.granjas.entity.Granja;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GranjaRepository extends JpaRepository<Granja, String> {

    List<Granja> findByUsuarioIdAndActivaTrueOrderByNombreGranjaAsc(String idUsuario);

    long countByUsuarioIdAndActivaTrue(String idUsuario);

    Optional<Granja> findByIdAndUsuarioId(String id, String idUsuario);
}
