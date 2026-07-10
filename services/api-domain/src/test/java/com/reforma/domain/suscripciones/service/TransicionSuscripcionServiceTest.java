package com.reforma.domain.suscripciones.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.reforma.domain.auditoria.domain.AccionAuditoria;
import com.reforma.domain.auditoria.dto.AuditoriaEvento;
import com.reforma.domain.auditoria.service.AuditoriaService;
import com.reforma.domain.common.domain.PlanSuscripcion;
import com.reforma.domain.empleados.service.EmpleadoService;
import com.reforma.domain.suscripciones.domain.EstadoPago;
import com.reforma.domain.suscripciones.domain.EstadoSuscripcion;
import com.reforma.domain.suscripciones.domain.PeriodoFacturacion;
import com.reforma.domain.suscripciones.entity.Pago;
import com.reforma.domain.suscripciones.entity.Suscripcion;
import com.reforma.domain.suscripciones.pasarela.PasarelaPagosService;
import com.reforma.domain.suscripciones.repository.PagoRepository;
import com.reforma.domain.suscripciones.repository.SuscripcionRepository;
import com.reforma.domain.usuarios.email.EmailNotificacionService;
import com.reforma.domain.usuarios.entity.Usuario;
import com.reforma.domain.usuarios.repository.UsuarioRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Transiciones del job de vencimientos (RD-P8) sobre una suscripción:
 * renovación, downgrade aplicado/pospuesto, expiración por cancelación y por gracia agotada.
 */
@ExtendWith(MockitoExtension.class)
class TransicionSuscripcionServiceTest {

    private static final String ID_DUENO = "u_1";
    private static final Long ID_SUSCRIPCION = 10L;
    private static final Instant AHORA = Instant.parse("2026-08-02T04:30:00Z");
    private static final Instant CORTE_GRACIA = AHORA.minus(7, ChronoUnit.DAYS);
    private static final Instant FIN_CICLO_VENCIDO = Instant.parse("2026-08-01T00:00:00Z");

    @Mock private SuscripcionRepository suscripcionRepository;
    @Mock private UsuarioRepository usuarioRepository;
    @Mock private PagoRepository pagoRepository;
    @Mock private PasarelaPagosService pasarela;
    @Mock private EmpleadoService empleadoService;
    @Mock private EmailNotificacionService emailNotificacionService;
    @Mock private AuditoriaService auditoriaService;

    @Captor private ArgumentCaptor<AuditoriaEvento> eventoCaptor;
    @Captor private ArgumentCaptor<Pago> pagoCaptor;

    private TransicionSuscripcionService servicio;

    @BeforeEach
    void configurar() {
        var precios = new PrecioPlanService(
                new BigDecimal("50750"), new BigDecimal("143550"), new BigDecimal("332050"), 10);
        servicio = new TransicionSuscripcionService(
                suscripcionRepository,
                usuarioRepository,
                pagoRepository,
                new PlanService(usuarioRepository),
                precios,
                pasarela,
                empleadoService,
                emailNotificacionService,
                auditoriaService);
    }

    private Usuario dueno(PlanSuscripcion plan) {
        return Usuario.builder()
                .id(ID_DUENO)
                .email("ana@reforma.com")
                .nombreUsuario("Ana")
                .planSuscripcion(plan)
                .esUsuarioEmpleado(false)
                .build();
    }

    private Suscripcion activaVencida(PlanSuscripcion plan, BigDecimal precioSnapshot) {
        return Suscripcion.builder()
                .id(ID_SUSCRIPCION)
                .idUsuario(ID_DUENO)
                .plan(plan)
                .periodo(PeriodoFacturacion.MENSUAL)
                .estado(EstadoSuscripcion.ACTIVA)
                .precioArs(precioSnapshot)
                .fechaInicio(Instant.parse("2026-07-01T00:00:00Z"))
                .fechaFinPeriodo(FIN_CICLO_VENCIDO)
                .ultimoCobroEstado(EstadoPago.APROBADO)
                .ultimoCobroFecha(Instant.parse("2026-07-01T00:05:00Z"))
                .build();
    }

    private void cargar(Suscripcion s, Usuario dueno) {
        when(suscripcionRepository.findById(ID_SUSCRIPCION)).thenReturn(Optional.of(s));
        when(usuarioRepository.findById(ID_DUENO)).thenReturn(Optional.of(dueno));
    }

    private Usuario empleadoVinculado(String id, Instant vinculacion) {
        return Usuario.builder()
                .id(id)
                .email(id + "@reforma.com")
                .esUsuarioEmpleado(true)
                .activoComoEmpleado(true)
                .fechaVinculacion(vinculacion)
                .build();
    }

    // ---- renovación (RD-P4) ----

    @Test
    @DisplayName("renovación: el ciclo nuevo arranca donde terminó el anterior y cobra el snapshot")
    void renovacion_cicloDesdeElFinAnteriorYPrecioSnapshot() {
        // Snapshot distinto del precio de lista (RD-P12: la renovación cobra lo contratado).
        var s = activaVencida(PlanSuscripcion.BUSINESS, new BigDecimal("99999.00"));
        var dueno = dueno(PlanSuscripcion.BUSINESS);
        cargar(s, dueno);
        when(pasarela.cobrarRenovacion(s)).thenReturn(Optional.of(EstadoPago.APROBADO));

        servicio.procesar(ID_SUSCRIPCION, AHORA, CORTE_GRACIA);

        assertThat(s.getFechaInicio()).isEqualTo(FIN_CICLO_VENCIDO);
        assertThat(s.getFechaFinPeriodo())
                .isEqualTo(PeriodoFacturacion.MENSUAL.finDeCiclo(FIN_CICLO_VENCIDO));
        assertThat(s.getUltimoCobroEstado()).isEqualTo(EstadoPago.APROBADO);
        assertThat(s.getUltimoCobroFecha()).isEqualTo(AHORA);
        verify(pagoRepository).save(pagoCaptor.capture());
        assertThat(pagoCaptor.getValue().getMontoArs()).isEqualByComparingTo("99999.00");
        verify(auditoriaService).registrar(eventoCaptor.capture());
        assertThat(eventoCaptor.getValue().accion()).isEqualTo(AccionAuditoria.PAGO_REGISTRADO);
    }

    @Test
    @DisplayName("renovación rechazada: queda ACTIVA en gracia y avisa al dueño por email")
    void renovacion_rechazadaQuedaEnGracia() {
        var s = activaVencida(PlanSuscripcion.STARTER, new BigDecimal("50750.00"));
        var dueno = dueno(PlanSuscripcion.STARTER);
        cargar(s, dueno);
        when(pasarela.cobrarRenovacion(s)).thenReturn(Optional.of(EstadoPago.RECHAZADO));

        servicio.procesar(ID_SUSCRIPCION, AHORA, CORTE_GRACIA);

        assertThat(s.getEstado()).isEqualTo(EstadoSuscripcion.ACTIVA);
        assertThat(s.getUltimoCobroEstado()).isEqualTo(EstadoPago.RECHAZADO);
        assertThat(dueno.getPlanSuscripcion()).isEqualTo(PlanSuscripcion.STARTER);
        verify(emailNotificacionService).enviarAvisoSuscripcion(eq(dueno), anyString(), anyString());
    }

    @Test
    @DisplayName("renovación en modo mp: la pasarela devuelve vacío → no se toca ultimo_cobro ni hay pago")
    void renovacion_modoWebhookNoTocaCobro() {
        var s = activaVencida(PlanSuscripcion.STARTER, new BigDecimal("50750.00"));
        cargar(s, dueno(PlanSuscripcion.STARTER));
        when(pasarela.cobrarRenovacion(s)).thenReturn(Optional.empty());

        servicio.procesar(ID_SUSCRIPCION, AHORA, CORTE_GRACIA);

        // El ciclo se abre igual; el resultado del cobro lo resolverá el webhook.
        assertThat(s.getFechaInicio()).isEqualTo(FIN_CICLO_VENCIDO);
        assertThat(s.getUltimoCobroEstado()).isEqualTo(EstadoPago.APROBADO);
        assertThat(s.getUltimoCobroFecha()).isEqualTo(Instant.parse("2026-07-01T00:05:00Z"));
        verifyNoInteractions(pagoRepository, auditoriaService);
    }

    // ---- downgrade programado (RD-P5 / RD-P6.b.3) ----

    @Test
    @DisplayName("downgrade aplicado: plan pendiente con precio de lista, ciclo desde el fin anterior")
    void downgrade_aplicado() {
        var s = activaVencida(PlanSuscripcion.BUSINESS, new BigDecimal("143550.00"));
        s.setPlanPendiente(PlanSuscripcion.STARTER);
        s.setPeriodoPendiente(PeriodoFacturacion.MENSUAL);
        var dueno = dueno(PlanSuscripcion.BUSINESS);
        cargar(s, dueno);
        when(usuarioRepository.countByUsuarioDuenoIdAndActivoComoEmpleadoTrue(ID_DUENO))
                .thenReturn(2L); // dentro del límite STARTER (2)
        when(pasarela.cobrarRenovacion(s)).thenReturn(Optional.of(EstadoPago.APROBADO));

        servicio.procesar(ID_SUSCRIPCION, AHORA, CORTE_GRACIA);

        assertThat(s.getPlan()).isEqualTo(PlanSuscripcion.STARTER);
        assertThat(s.getPrecioArs()).isEqualByComparingTo("50750"); // re-precia a lista (RD-P12)
        assertThat(s.getPlanPendiente()).isNull();
        assertThat(s.getPeriodoPendiente()).isNull();
        assertThat(s.getFechaInicio()).isEqualTo(FIN_CICLO_VENCIDO);
        assertThat(dueno.getPlanSuscripcion()).isEqualTo(PlanSuscripcion.STARTER);
        verify(pasarela).actualizarMontoRecurrente(eq(s), any());
        verify(auditoriaService, times(2)).registrar(eventoCaptor.capture());
        assertThat(eventoCaptor.getAllValues())
                .extracting(AuditoriaEvento::accion)
                .containsExactly(AccionAuditoria.PAGO_REGISTRADO, AccionAuditoria.DOWNGRADE_APLICADO);
    }

    @Test
    @DisplayName("downgrade pospuesto: empleados sobre el límite destino → renueva el plan actual y avisa")
    void downgrade_pospuestoPorEmpleados() {
        var s = activaVencida(PlanSuscripcion.BUSINESS, new BigDecimal("143550.00"));
        s.setPlanPendiente(PlanSuscripcion.STARTER);
        s.setPeriodoPendiente(PeriodoFacturacion.MENSUAL);
        var dueno = dueno(PlanSuscripcion.BUSINESS);
        cargar(s, dueno);
        when(usuarioRepository.countByUsuarioDuenoIdAndActivoComoEmpleadoTrue(ID_DUENO))
                .thenReturn(5L); // sobre el límite STARTER (2)
        when(pasarela.cobrarRenovacion(s)).thenReturn(Optional.of(EstadoPago.APROBADO));

        servicio.procesar(ID_SUSCRIPCION, AHORA, CORTE_GRACIA);

        // Nadie se desactiva por un downgrade voluntario: se renueva un ciclo más del plan actual.
        assertThat(s.getPlan()).isEqualTo(PlanSuscripcion.BUSINESS);
        assertThat(s.getPlanPendiente()).isEqualTo(PlanSuscripcion.STARTER);
        assertThat(s.getFechaInicio()).isEqualTo(FIN_CICLO_VENCIDO);
        assertThat(dueno.getPlanSuscripcion()).isEqualTo(PlanSuscripcion.BUSINESS);
        verify(empleadoService, never()).cambiarEstado(any(), any(), anyBoolean());
        verify(auditoriaService, times(2)).registrar(eventoCaptor.capture());
        assertThat(eventoCaptor.getAllValues())
                .extracting(AuditoriaEvento::accion)
                .contains(AccionAuditoria.DOWNGRADE_POSPUESTO);
        verify(emailNotificacionService).enviarAvisoSuscripcion(eq(dueno), anyString(), anyString());
    }

    // ---- expiración (RD-P7 / RD-P8) ----

    @Test
    @DisplayName("CANCELADA vencida: expira, cae a DEMO y reinicia la ventana de retención")
    void cancelada_vencidaExpira() {
        var s = activaVencida(PlanSuscripcion.BUSINESS, new BigDecimal("143550.00"));
        s.setEstado(EstadoSuscripcion.CANCELADA);
        s.setPlanPendiente(PlanSuscripcion.DEMO);
        var dueno = dueno(PlanSuscripcion.BUSINESS);
        cargar(s, dueno);

        servicio.procesar(ID_SUSCRIPCION, AHORA, CORTE_GRACIA);

        assertThat(s.getEstado()).isEqualTo(EstadoSuscripcion.EXPIRADA);
        assertThat(s.getPlanPendiente()).isNull();
        assertThat(dueno.getPlanSuscripcion()).isEqualTo(PlanSuscripcion.DEMO);
        // RD-P7: la purga cuenta desde la caída a DEMO, no desde el registro.
        assertThat(dueno.getFechaInicioDemo()).isEqualTo(AHORA);
        verify(auditoriaService).registrar(eventoCaptor.capture());
        assertThat(eventoCaptor.getValue().accion()).isEqualTo(AccionAuditoria.SUSCRIPCION_EXPIRADA);
        verify(emailNotificacionService).enviarAvisoSuscripcion(eq(dueno), anyString(), anyString());
    }

    @Test
    @DisplayName("gracia agotada: cobro RECHAZADO viejo → baja el cobro recurrente y expira")
    void graciaAgotada_expira() {
        var s = activaVencida(PlanSuscripcion.STARTER, new BigDecimal("50750.00"));
        s.setFechaFinPeriodo(AHORA.plus(10, ChronoUnit.DAYS)); // el ciclo NO venció
        s.setUltimoCobroEstado(EstadoPago.RECHAZADO);
        s.setUltimoCobroFecha(CORTE_GRACIA.minus(1, ChronoUnit.DAYS));
        var dueno = dueno(PlanSuscripcion.STARTER);
        cargar(s, dueno);

        servicio.procesar(ID_SUSCRIPCION, AHORA, CORTE_GRACIA);

        assertThat(s.getEstado()).isEqualTo(EstadoSuscripcion.EXPIRADA);
        assertThat(dueno.getPlanSuscripcion()).isEqualTo(PlanSuscripcion.DEMO);
        verify(pasarela).cancelarCobroRecurrente(s);
    }

    @Test
    @DisplayName("gracia vigente: cobro RECHAZADO reciente y ciclo no vencido → no hace nada")
    void graciaVigente_noHaceNada() {
        var s = activaVencida(PlanSuscripcion.STARTER, new BigDecimal("50750.00"));
        s.setFechaFinPeriodo(AHORA.plus(10, ChronoUnit.DAYS));
        s.setUltimoCobroEstado(EstadoPago.RECHAZADO);
        s.setUltimoCobroFecha(CORTE_GRACIA.plus(1, ChronoUnit.DAYS)); // aún dentro de la gracia
        cargar(s, dueno(PlanSuscripcion.STARTER));

        servicio.procesar(ID_SUSCRIPCION, AHORA, CORTE_GRACIA);

        assertThat(s.getEstado()).isEqualTo(EstadoSuscripcion.ACTIVA);
        verifyNoInteractions(pagoRepository, auditoriaService, emailNotificacionService);
    }

    @Test
    @DisplayName("al caer a DEMO se desactivan los empleados excedentes, los más recientes primero")
    void expirar_desactivaExcedentesMasRecientesPrimero() {
        var s = activaVencida(PlanSuscripcion.BUSINESS, new BigDecimal("143550.00"));
        s.setEstado(EstadoSuscripcion.CANCELADA);
        var dueno = dueno(PlanSuscripcion.BUSINESS);
        cargar(s, dueno);
        // 4 activos, límite DEMO = 2 → se desactivan los 2 más recientes (el repo ya ordena desc).
        var e1 = empleadoVinculado("e_mas_nuevo", Instant.parse("2026-06-01T00:00:00Z"));
        var e2 = empleadoVinculado("e_nuevo", Instant.parse("2026-05-01T00:00:00Z"));
        var e3 = empleadoVinculado("e_viejo", Instant.parse("2026-04-01T00:00:00Z"));
        var e4 = empleadoVinculado("e_mas_viejo", Instant.parse("2026-03-01T00:00:00Z"));
        when(usuarioRepository.findByUsuarioDuenoIdOrderByFechaVinculacionDesc(ID_DUENO))
                .thenReturn(List.of(e1, e2, e3, e4));

        servicio.procesar(ID_SUSCRIPCION, AHORA, CORTE_GRACIA);

        verify(empleadoService).cambiarEstado(ID_DUENO, "e_mas_nuevo", false);
        verify(empleadoService).cambiarEstado(ID_DUENO, "e_nuevo", false);
        verify(empleadoService, never()).cambiarEstado(ID_DUENO, "e_viejo", false);
        verify(empleadoService, never()).cambiarEstado(ID_DUENO, "e_mas_viejo", false);
    }

    // ---- robustez / re-evaluación dentro de la transacción ----

    @Test
    @DisplayName("suscripción borrada entre el listado y el procesamiento → no-op")
    void suscripcionBorrada_noOp() {
        when(suscripcionRepository.findById(ID_SUSCRIPCION)).thenReturn(Optional.empty());

        servicio.procesar(ID_SUSCRIPCION, AHORA, CORTE_GRACIA);

        verifyNoInteractions(pagoRepository, pasarela, auditoriaService);
    }

    @Test
    @DisplayName("re-evaluación: el dueño reactivó antes del job (ciclo vigente) → no hace nada")
    void reevaluacion_cicloVigenteNoHaceNada() {
        var s = activaVencida(PlanSuscripcion.BUSINESS, new BigDecimal("143550.00"));
        s.setFechaFinPeriodo(AHORA.plus(20, ChronoUnit.DAYS));
        cargar(s, dueno(PlanSuscripcion.BUSINESS));

        servicio.procesar(ID_SUSCRIPCION, AHORA, CORTE_GRACIA);

        assertThat(s.getEstado()).isEqualTo(EstadoSuscripcion.ACTIVA);
        verifyNoInteractions(pagoRepository, pasarela, auditoriaService);
    }
}
