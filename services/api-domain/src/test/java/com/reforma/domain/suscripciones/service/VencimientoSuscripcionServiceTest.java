package com.reforma.domain.suscripciones.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.reforma.domain.suscripciones.domain.EstadoPago;
import com.reforma.domain.suscripciones.domain.EstadoSuscripcion;
import com.reforma.domain.suscripciones.entity.Suscripcion;
import com.reforma.domain.suscripciones.repository.SuscripcionRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** Job diario de vencimientos (RD-P8): armado de candidatas y aislamiento de fallos. */
@ExtendWith(MockitoExtension.class)
class VencimientoSuscripcionServiceTest {

    private static final Instant AHORA = Instant.parse("2026-08-02T04:30:00Z");

    @Mock private SuscripcionRepository suscripcionRepository;
    @Mock private TransicionSuscripcionService transicionService;

    private VencimientoSuscripcionService servicio(boolean habilitado) {
        return new VencimientoSuscripcionService(
                suscripcionRepository, transicionService, habilitado, 7);
    }

    private static Suscripcion conId(long id) {
        return Suscripcion.builder().id(id).build();
    }

    @Test
    @DisplayName("deshabilitado por config → el disparo cron no consulta nada")
    void deshabilitado_noHaceNada() {
        servicio(false).procesarVencimientos();
        verifyNoInteractions(suscripcionRepository, transicionService);
    }

    @Test
    @DisplayName("junta las tres consultas, deduplica ids y pasa el corte de gracia correcto")
    void juntaCandidatasYDeduplica() {
        Instant corteGracia = AHORA.minus(7, ChronoUnit.DAYS);
        when(suscripcionRepository.findByEstadoAndFechaFinPeriodoLessThanEqual(
                        EstadoSuscripcion.CANCELADA, AHORA))
                .thenReturn(List.of(conId(1L)));
        when(suscripcionRepository.findByEstadoAndUltimoCobroEstadoAndUltimoCobroFechaLessThanEqual(
                        EstadoSuscripcion.ACTIVA, EstadoPago.RECHAZADO, corteGracia))
                .thenReturn(List.of(conId(2L), conId(3L)));
        // La 3 también tiene el ciclo vencido: debe procesarse UNA sola vez.
        when(suscripcionRepository.findByEstadoAndFechaFinPeriodoLessThanEqual(
                        EstadoSuscripcion.ACTIVA, AHORA))
                .thenReturn(List.of(conId(3L), conId(4L)));

        servicio(true).procesarVencimientos(AHORA);

        verify(transicionService).procesar(1L, AHORA, corteGracia);
        verify(transicionService).procesar(2L, AHORA, corteGracia);
        verify(transicionService, times(1)).procesar(3L, AHORA, corteGracia);
        verify(transicionService).procesar(4L, AHORA, corteGracia);
    }

    @Test
    @DisplayName("un fallo aislado no detiene al resto de las candidatas")
    void falloAisladoNoDetieneAlResto() {
        when(suscripcionRepository.findByEstadoAndFechaFinPeriodoLessThanEqual(
                        eq(EstadoSuscripcion.CANCELADA), any()))
                .thenReturn(List.of(conId(1L), conId(2L)));
        when(suscripcionRepository.findByEstadoAndUltimoCobroEstadoAndUltimoCobroFechaLessThanEqual(
                        any(), any(), any()))
                .thenReturn(List.of());
        when(suscripcionRepository.findByEstadoAndFechaFinPeriodoLessThanEqual(
                        eq(EstadoSuscripcion.ACTIVA), any()))
                .thenReturn(List.of());
        doThrow(new RuntimeException("boom")).when(transicionService).procesar(eq(1L), any(), any());

        servicio(true).procesarVencimientos(AHORA);

        verify(transicionService).procesar(eq(1L), any(), any());
        verify(transicionService).procesar(eq(2L), any(), any());
    }
}
