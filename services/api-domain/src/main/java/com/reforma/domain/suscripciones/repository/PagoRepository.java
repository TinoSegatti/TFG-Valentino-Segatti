package com.reforma.domain.suscripciones.repository;

import com.reforma.domain.suscripciones.entity.Pago;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PagoRepository extends JpaRepository<Pago, Long> {

    /** Historial de cobros de una suscripción, el más reciente primero (usa idx_pago_suscripcion). */
    Page<Pago> findByIdSuscripcionOrderByFechaPagoDesc(Long idSuscripcion, Pageable pageable);

    /** Clave de idempotencia del webhook MP (RD-P9): un pago notificado dos veces no se duplica. */
    Optional<Pago> findByMpPaymentId(String mpPaymentId);
}
