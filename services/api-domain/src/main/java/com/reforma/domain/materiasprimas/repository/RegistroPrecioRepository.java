package com.reforma.domain.materiasprimas.repository;

import com.reforma.domain.materiasprimas.entity.RegistroPrecio;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;

public interface RegistroPrecioRepository extends JpaRepository<RegistroPrecio, String> {

    @Modifying
    void deleteByCompra_Id(String idCompra);

    List<RegistroPrecio> findByCompra_Id(String idCompra);

    List<RegistroPrecio> findByMateriaPrimaIdOrderByFechaReferenciaDescIdDesc(Long idMateriaPrima);
}
