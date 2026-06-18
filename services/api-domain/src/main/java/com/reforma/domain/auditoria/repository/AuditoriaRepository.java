package com.reforma.domain.auditoria.repository;

import com.reforma.domain.auditoria.domain.AccionAuditoria;
import com.reforma.domain.auditoria.entity.Auditoria;
import java.time.Instant;
import java.util.Collection;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AuditoriaRepository extends JpaRepository<Auditoria, String> {

    /**
     * Auditoría del tenant (actores en {@code idsUsuario} = dueño + sus empleados) con filtros
     * opcionales. Cada filtro se activa con su flag {@code filtraX}; el valor solo aparece en la
     * comparación tipada (nunca en un {@code :param is null} suelto) para que PostgreSQL pueda
     * inferir el tipo del parámetro. El orden y la paginación los aporta el {@link Pageable}.
     */
    @Query(
            """
            select a from Auditoria a
            where a.idUsuario in :idsUsuario
              and (:filtraGranja = false or a.idGranja = :idGranja)
              and (:filtraUsuario = false or a.idUsuario = :idUsuario)
              and (:filtraAccion = false or a.accion = :accion)
              and (:filtraDesde = false or a.fechaOperacion >= :desde)
              and (:filtraHasta = false or a.fechaOperacion <= :hasta)
            """)
    Page<Auditoria> buscar(
            @Param("idsUsuario") Collection<String> idsUsuario,
            @Param("filtraGranja") boolean filtraGranja,
            @Param("idGranja") String idGranja,
            @Param("filtraUsuario") boolean filtraUsuario,
            @Param("idUsuario") String idUsuario,
            @Param("filtraAccion") boolean filtraAccion,
            @Param("accion") AccionAuditoria accion,
            @Param("filtraDesde") boolean filtraDesde,
            @Param("desde") Instant desde,
            @Param("filtraHasta") boolean filtraHasta,
            @Param("hasta") Instant hasta,
            Pageable pageable);
}
