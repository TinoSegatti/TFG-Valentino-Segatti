package com.reforma.domain.suscripciones.service;

import com.reforma.domain.suscripciones.domain.EstadoPago;
import com.reforma.domain.suscripciones.domain.EstadoSuscripcion;
import com.reforma.domain.suscripciones.entity.Suscripcion;
import com.reforma.domain.suscripciones.repository.SuscripcionRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashSet;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Job diario de vencimientos de suscripción (RD-P8): junta las candidatas (ciclo vencido en
 * ACTIVA o CANCELADA, o gracia por cobro rechazado agotada) y delega cada una en
 * {@link TransicionSuscripcionService}, que la procesa en su propia transacción re-evaluando
 * el estado — un fallo aislado no detiene al resto (patrón de {@code LimpiezaCuentasDemoService}).
 *
 * <p>Config: {@code reforma.pagos.vencimientos-cron} (default 04:30, después de la purga DEMO
 * de las 03:00 para no purgar una cuenta el mismo día que cae a DEMO),
 * {@code reforma.pagos.gracia-dias} (default 7) y {@code reforma.pagos.vencimientos-habilitado}.
 */
@Service
public class VencimientoSuscripcionService {

    private static final Logger log = LoggerFactory.getLogger(VencimientoSuscripcionService.class);

    private final SuscripcionRepository suscripcionRepository;
    private final TransicionSuscripcionService transicionService;
    private final boolean habilitado;
    private final long graciaDias;

    public VencimientoSuscripcionService(
            SuscripcionRepository suscripcionRepository,
            TransicionSuscripcionService transicionService,
            @Value("${reforma.pagos.vencimientos-habilitado:true}") boolean habilitado,
            @Value("${reforma.pagos.gracia-dias:7}") long graciaDias) {
        this.suscripcionRepository = suscripcionRepository;
        this.transicionService = transicionService;
        this.habilitado = habilitado;
        this.graciaDias = graciaDias;
    }

    @Scheduled(cron = "${reforma.pagos.vencimientos-cron:0 30 4 * * *}")
    public void procesarVencimientos() {
        if (!habilitado) {
            return;
        }
        procesarVencimientos(Instant.now());
    }

    /** Separado del disparo cron para poder invocarlo con un "ahora" fijo en tests. */
    public void procesarVencimientos(Instant ahora) {
        Instant corteGracia = ahora.minus(graciaDias, ChronoUnit.DAYS);
        Set<Long> candidatas = new LinkedHashSet<>();
        suscripcionRepository
                .findByEstadoAndFechaFinPeriodoLessThanEqual(EstadoSuscripcion.CANCELADA, ahora)
                .stream().map(Suscripcion::getId).forEach(candidatas::add);
        suscripcionRepository
                .findByEstadoAndUltimoCobroEstadoAndUltimoCobroFechaLessThanEqual(
                        EstadoSuscripcion.ACTIVA, EstadoPago.RECHAZADO, corteGracia)
                .stream().map(Suscripcion::getId).forEach(candidatas::add);
        suscripcionRepository
                .findByEstadoAndFechaFinPeriodoLessThanEqual(EstadoSuscripcion.ACTIVA, ahora)
                .stream().map(Suscripcion::getId).forEach(candidatas::add);
        if (candidatas.isEmpty()) {
            return;
        }
        int procesadas = 0;
        for (Long id : candidatas) {
            try {
                transicionService.procesar(id, ahora, corteGracia);
                procesadas++;
            } catch (Exception e) {
                log.error("No se pudo procesar el vencimiento de la suscripción {}: {}",
                        id, e.getMessage(), e);
            }
        }
        log.info("Vencimientos de suscripción: {}/{} candidatas procesadas (gracia {} días).",
                procesadas, candidatas.size(), graciaDias);
    }
}
