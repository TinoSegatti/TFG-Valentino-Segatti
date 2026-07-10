package com.reforma.domain.suscripciones.repository;

import com.reforma.domain.suscripciones.domain.EstadoPago;
import com.reforma.domain.suscripciones.domain.EstadoSuscripcion;
import com.reforma.domain.suscripciones.entity.Suscripcion;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SuscripcionRepository extends JpaRepository<Suscripcion, Long> {

    /** La suscripción es 1:1 con el dueño (UNIQUE en BD); vacío = DEMO implícito. */
    Optional<Suscripcion> findByIdUsuario(String idUsuario);

    /** Vínculo pago→suscripción del webhook MP (metadata {@code preapproval_id}, RD-P9). */
    Optional<Suscripcion> findByMpPreapprovalId(String mpPreapprovalId);

    /** Candidatas del job de vencimientos (RD-P8): ciclo pagado vencido en el estado dado. */
    List<Suscripcion> findByEstadoAndFechaFinPeriodoLessThanEqual(
            EstadoSuscripcion estado, Instant corte);

    /** Candidatas a expirar por gracia (RD-P8): último cobro rechazado hace más de la gracia. */
    List<Suscripcion> findByEstadoAndUltimoCobroEstadoAndUltimoCobroFechaLessThanEqual(
            EstadoSuscripcion estado, EstadoPago ultimoCobroEstado, Instant corteGracia);
}
