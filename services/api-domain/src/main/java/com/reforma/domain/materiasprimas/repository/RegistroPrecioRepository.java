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

    /** Serie de precios de compras de una MP (para el cálculo de anomalías), orden cronológico. */
    List<RegistroPrecio> findByMateriaPrimaIdAndOrigenOrderByFechaReferenciaAsc(
            Long idMateriaPrima, String origen);

    /** Igual que el anterior pero excluyendo una compra (la que se está registrando ahora). */
    List<RegistroPrecio> findByMateriaPrimaIdAndOrigenAndCompraIdNotOrderByFechaReferenciaAsc(
            Long idMateriaPrima, String origen, String idCompraExcluida);
}
