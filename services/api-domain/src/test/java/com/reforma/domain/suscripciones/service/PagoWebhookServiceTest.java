package com.reforma.domain.suscripciones.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.mercadopago.resources.payment.Payment;
import com.mercadopago.resources.preapproval.Preapproval;
import com.mercadopago.resources.preapproval.PreapprovalAutoRecurring;
import com.reforma.domain.auditoria.domain.AccionAuditoria;
import com.reforma.domain.auditoria.dto.AuditoriaEvento;
import com.reforma.domain.auditoria.service.AuditoriaService;
import com.reforma.domain.common.domain.PlanSuscripcion;
import com.reforma.domain.suscripciones.domain.EmpleadosSobreLimiteException;
import com.reforma.domain.suscripciones.domain.EstadoPago;
import com.reforma.domain.suscripciones.domain.EstadoSuscripcion;
import com.reforma.domain.suscripciones.domain.PeriodoFacturacion;
import com.reforma.domain.suscripciones.entity.Pago;
import com.reforma.domain.suscripciones.entity.Suscripcion;
import com.reforma.domain.suscripciones.pasarela.PasarelaMercadoPagoService;
import com.reforma.domain.suscripciones.repository.PagoRepository;
import com.reforma.domain.suscripciones.repository.SuscripcionRepository;
import com.reforma.domain.usuarios.email.EmailNotificacionService;
import com.reforma.domain.usuarios.entity.Usuario;
import com.reforma.domain.usuarios.repository.UsuarioRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestClient;

/**
 * RD-P9: el webhook re-consulta a MP (nunca confía en el payload), aplica transiciones
 * idempotentes y los eventos no procesables se ignoran sin romper (MP recibe 200).
 * El evento {@code subscription_authorized_payment} (HTTP crudo) se valida en sandbox.
 */
@ExtendWith(MockitoExtension.class)
class PagoWebhookServiceTest {

    private static final String ID_USUARIO = "u_1";
    private static final String PREAPPROVAL_ID = "pre_nuevo";
    private static final String REFERENCIA = ID_USUARIO + "|ENTERPRISE|MENSUAL";

    @Mock private PasarelaMercadoPagoService pasarela;
    @Mock private SuscripcionService suscripcionService;
    @Mock private SuscripcionRepository suscripcionRepository;
    @Mock private PagoRepository pagoRepository;
    @Mock private UsuarioRepository usuarioRepository;
    @Mock private EmailNotificacionService emailNotificacionService;
    @Mock private AuditoriaService auditoriaService;

    @Captor private ArgumentCaptor<Pago> pagoCaptor;
    @Captor private ArgumentCaptor<AuditoriaEvento> eventoCaptor;

    private PagoWebhookService servicio;

    @BeforeEach
    void configurar() {
        servicio = new PagoWebhookService(
                pasarela, suscripcionService, suscripcionRepository, pagoRepository,
                usuarioRepository, emailNotificacionService, auditoriaService,
                RestClient.builder(), "TEST-token");
    }

    /**
     * Recursos del SDK como objetos reales (campos por reflexión, como los hidrata Gson):
     * solo tienen @Getter y mockearlos dentro de un thenReturn anida stubbings (prohibido).
     */
    private Preapproval preapproval(String estado, String referencia, String monto) {
        var p = new Preapproval();
        setCampo(p, "status", estado);
        setCampo(p, "externalReference", referencia);
        if (monto != null) {
            var recurrencia = new PreapprovalAutoRecurring();
            setCampo(recurrencia, "transactionAmount", new BigDecimal(monto));
            setCampo(p, "autoRecurring", recurrencia);
        }
        return p;
    }

    private Suscripcion suscripcion(EstadoSuscripcion estado, String mpPreapprovalId) {
        return Suscripcion.builder()
                .id(10L)
                .idUsuario(ID_USUARIO)
                .plan(PlanSuscripcion.BUSINESS)
                .periodo(PeriodoFacturacion.MENSUAL)
                .estado(estado)
                .precioArs(new BigDecimal("143550.00"))
                .fechaInicio(Instant.parse("2026-07-01T00:00:00Z"))
                .fechaFinPeriodo(Instant.parse("2026-08-01T00:00:00Z"))
                .mpPreapprovalId(mpPreapprovalId)
                .fechaCreacion(Instant.parse("2026-07-01T00:00:00Z"))
                .fechaActualizacion(Instant.parse("2026-07-01T00:00:00Z"))
                .build();
    }

    private Payment pago(String estadoMp, String monto, Instant fecha, String preapprovalId) {
        var p = new Payment();
        setCampo(p, "status", estadoMp);
        setCampo(p, "transactionAmount", monto != null ? new BigDecimal(monto) : null);
        setCampo(p, "dateCreated",
                fecha != null ? OffsetDateTime.ofInstant(fecha, ZoneOffset.UTC) : null);
        setCampo(p, "metadata", preapprovalId != null
                ? Map.of("preapproval_id", preapprovalId) : Map.of());
        return p;
    }

    private static void setCampo(Object objeto, String campo, Object valor) {
        try {
            var f = objeto.getClass().getDeclaredField(campo);
            f.setAccessible(true);
            f.set(objeto, valor);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(
                    "El SDK de MP renombró el campo '" + campo + "'", e);
        }
    }

    // ---- subscription_preapproval ----

    @Test
    @DisplayName("preapproval authorized: activa con la intención del external_reference y el monto de MP")
    void preapprovalAutorizado_activa() {
        when(pasarela.consultarPreapproval(PREAPPROVAL_ID))
                .thenReturn(preapproval("authorized", REFERENCIA, "332050"));
        when(suscripcionRepository.findByIdUsuario(ID_USUARIO)).thenReturn(Optional.empty());

        servicio.procesar(PagoWebhookService.TIPO_PREAPPROVAL, PREAPPROVAL_ID);

        verify(suscripcionService).activarDesdePasarela(ID_USUARIO, PlanSuscripcion.ENTERPRISE,
                PeriodoFacturacion.MENSUAL, new BigDecimal("332050"), PREAPPROVAL_ID);
        verify(pasarela, never()).cancelarDefinitivo(any());
    }

    @Test
    @DisplayName("preapproval authorized sobre un upgrade: cierra en MP el preapproval reemplazado (RD-P4)")
    void preapprovalAutorizado_upgradeCancelaElAnterior() {
        when(pasarela.consultarPreapproval(PREAPPROVAL_ID))
                .thenReturn(preapproval("authorized", REFERENCIA, "332050"));
        when(suscripcionRepository.findByIdUsuario(ID_USUARIO))
                .thenReturn(Optional.of(suscripcion(EstadoSuscripcion.ACTIVA, "pre_viejo")));

        servicio.procesar(PagoWebhookService.TIPO_PREAPPROVAL, PREAPPROVAL_ID);

        verify(suscripcionService).activarDesdePasarela(ID_USUARIO, PlanSuscripcion.ENTERPRISE,
                PeriodoFacturacion.MENSUAL, new BigDecimal("332050"), PREAPPROVAL_ID);
        verify(pasarela).cancelarDefinitivo("pre_viejo");
    }

    @Test
    @DisplayName("preapproval authorized repetido (misma suscripción ya activa): no-op idempotente")
    void preapprovalAutorizado_repetidoEsNoOp() {
        var s = suscripcion(EstadoSuscripcion.ACTIVA, PREAPPROVAL_ID);
        s.setPlan(PlanSuscripcion.ENTERPRISE);
        when(pasarela.consultarPreapproval(PREAPPROVAL_ID))
                .thenReturn(preapproval("authorized", REFERENCIA, "332050"));
        when(suscripcionRepository.findByIdUsuario(ID_USUARIO)).thenReturn(Optional.of(s));

        servicio.procesar(PagoWebhookService.TIPO_PREAPPROVAL, PREAPPROVAL_ID);

        verifyNoInteractions(suscripcionService);
        verify(pasarela, never()).cancelarDefinitivo(any());
    }

    @Test
    @DisplayName("preapproval authorized con el tenant sobre el límite de empleados: se omite sin romper")
    void preapprovalAutorizado_empleadosSobreLimiteNoRompe() {
        when(pasarela.consultarPreapproval(PREAPPROVAL_ID))
                .thenReturn(preapproval("authorized", REFERENCIA, "332050"));
        when(suscripcionRepository.findByIdUsuario(ID_USUARIO)).thenReturn(Optional.empty());
        when(suscripcionService.activarDesdePasarela(any(), any(), any(), any(), any()))
                .thenThrow(new EmpleadosSobreLimiteException(PlanSuscripcion.ENTERPRISE, 5, 2));

        servicio.procesar(PagoWebhookService.TIPO_PREAPPROVAL, PREAPPROVAL_ID);

        verify(pasarela, never()).cancelarDefinitivo(any());
    }

    @Test
    @DisplayName("preapproval cancelado desde MP: replica la cancelación local (RD-P7)")
    void preapprovalCancelado_replicaLocal() {
        var s = suscripcion(EstadoSuscripcion.ACTIVA, PREAPPROVAL_ID);
        when(pasarela.consultarPreapproval(PREAPPROVAL_ID))
                .thenReturn(preapproval("cancelled", REFERENCIA, null));
        when(suscripcionRepository.findByIdUsuario(ID_USUARIO)).thenReturn(Optional.of(s));

        servicio.procesar(PagoWebhookService.TIPO_PREAPPROVAL, PREAPPROVAL_ID);

        assertThat(s.getEstado()).isEqualTo(EstadoSuscripcion.CANCELADA);
        assertThat(s.getPlanPendiente()).isEqualTo(PlanSuscripcion.DEMO);
        verify(auditoriaService).registrar(eventoCaptor.capture());
        assertThat(eventoCaptor.getValue().accion())
                .isEqualTo(AccionAuditoria.SUSCRIPCION_CANCELADA);
    }

    @Test
    @DisplayName("preapproval paused tras nuestra propia cancelación (ya CANCELADA): no-op")
    void preapprovalPausado_yaCanceladaEsNoOp() {
        when(pasarela.consultarPreapproval(PREAPPROVAL_ID))
                .thenReturn(preapproval("paused", REFERENCIA, null));
        when(suscripcionRepository.findByIdUsuario(ID_USUARIO))
                .thenReturn(Optional.of(suscripcion(EstadoSuscripcion.CANCELADA, PREAPPROVAL_ID)));

        servicio.procesar(PagoWebhookService.TIPO_PREAPPROVAL, PREAPPROVAL_ID);

        verifyNoInteractions(auditoriaService);
    }

    @Test
    @DisplayName("preapproval desconocido para MP (prueba del panel) o referencia ajena: ignorado")
    void preapprovalNoProcesable_seIgnora() {
        when(pasarela.consultarPreapproval("pre_fantasma")).thenReturn(null);
        servicio.procesar(PagoWebhookService.TIPO_PREAPPROVAL, "pre_fantasma");

        when(pasarela.consultarPreapproval("pre_ajeno"))
                .thenReturn(preapproval("authorized", "otra-cosa", null));
        servicio.procesar(PagoWebhookService.TIPO_PREAPPROVAL, "pre_ajeno");

        verifyNoInteractions(suscripcionService, auditoriaService);
    }

    // ---- payment ----

    @Test
    @DisplayName("payment approved nuevo: inserta el pago idempotente y actualiza ultimo_cobro")
    void pagoAprobado_nuevo() {
        var s = suscripcion(EstadoSuscripcion.ACTIVA, PREAPPROVAL_ID);
        var fecha = Instant.parse("2026-07-15T10:00:00Z"); // ciclo vigente: no extiende
        when(pasarela.consultarPago(555L))
                .thenReturn(pago("approved", "143550", fecha, PREAPPROVAL_ID));
        when(suscripcionRepository.findByMpPreapprovalId(PREAPPROVAL_ID))
                .thenReturn(Optional.of(s));
        when(pagoRepository.findByMpPaymentId("555")).thenReturn(Optional.empty());

        servicio.procesar(PagoWebhookService.TIPO_PAGO, "555");

        verify(pagoRepository).save(pagoCaptor.capture());
        var pago = pagoCaptor.getValue();
        assertThat(pago.getMpPaymentId()).isEqualTo("555");
        assertThat(pago.getEstado()).isEqualTo(EstadoPago.APROBADO);
        assertThat(pago.getMontoArs()).isEqualByComparingTo("143550");
        assertThat(s.getUltimoCobroEstado()).isEqualTo(EstadoPago.APROBADO);
        assertThat(s.getFechaFinPeriodo()).isEqualTo(Instant.parse("2026-08-01T00:00:00Z"));
        verify(auditoriaService).registrar(eventoCaptor.capture());
        assertThat(eventoCaptor.getValue().accion()).isEqualTo(AccionAuditoria.PAGO_REGISTRADO);
    }

    @Test
    @DisplayName("payment de un upgrade que llega antes del authorized: descripcion del external_reference, no del plan local")
    void pagoDeUpgrade_descripcionDelExternalReference() {
        // Suscripción local todavía en BUSINESS; el pago pertenece al checkout ENTERPRISE.
        var s = suscripcion(EstadoSuscripcion.ACTIVA, "pre_viejo");
        var pagoMp = pago("approved", "332050", Instant.parse("2026-07-15T10:00:00Z"),
                PREAPPROVAL_ID);
        setCampo(pagoMp, "externalReference", REFERENCIA);
        when(pasarela.consultarPago(558L)).thenReturn(pagoMp);
        when(suscripcionRepository.findByMpPreapprovalId(PREAPPROVAL_ID))
                .thenReturn(Optional.empty()); // el preapproval nuevo aún no se asentó local
        when(suscripcionRepository.findByIdUsuario(ID_USUARIO)).thenReturn(Optional.of(s));
        when(pagoRepository.findByMpPaymentId("558")).thenReturn(Optional.empty());

        servicio.procesar(PagoWebhookService.TIPO_PAGO, "558");

        verify(pagoRepository).save(pagoCaptor.capture());
        assertThat(pagoCaptor.getValue().getDescripcion()).contains("ENTERPRISE");
    }

    @Test
    @DisplayName("payment approved con ciclo vencido: extiende el período (renovación por webhook)")
    void pagoAprobado_renovacionExtiendeCiclo() {
        var s = suscripcion(EstadoSuscripcion.ACTIVA, PREAPPROVAL_ID);
        var fecha = Instant.parse("2026-08-01T04:00:00Z"); // después del fin de ciclo
        when(pasarela.consultarPago(556L))
                .thenReturn(pago("approved", "143550", fecha, PREAPPROVAL_ID));
        when(suscripcionRepository.findByMpPreapprovalId(PREAPPROVAL_ID))
                .thenReturn(Optional.of(s));
        when(pagoRepository.findByMpPaymentId("556")).thenReturn(Optional.empty());

        servicio.procesar(PagoWebhookService.TIPO_PAGO, "556");

        // El ciclo nuevo arranca donde terminó el anterior (cobrar tarde no regala días).
        assertThat(s.getFechaInicio()).isEqualTo(Instant.parse("2026-08-01T00:00:00Z"));
        assertThat(s.getFechaFinPeriodo()).isEqualTo(Instant.parse("2026-09-01T00:00:00Z"));
    }

    @Test
    @DisplayName("payment repetido con el mismo estado: no-op (idempotencia por mp_payment_id)")
    void pagoRepetido_mismoEstadoEsNoOp() {
        var s = suscripcion(EstadoSuscripcion.ACTIVA, PREAPPROVAL_ID);
        when(pasarela.consultarPago(555L))
                .thenReturn(pago("approved", "143550", Instant.parse("2026-07-15T10:00:00Z"),
                        PREAPPROVAL_ID));
        when(suscripcionRepository.findByMpPreapprovalId(PREAPPROVAL_ID))
                .thenReturn(Optional.of(s));
        when(pagoRepository.findByMpPaymentId("555")).thenReturn(Optional.of(Pago.builder()
                .id(1L).idSuscripcion(10L).mpPaymentId("555")
                .montoArs(new BigDecimal("143550")).estado(EstadoPago.APROBADO)
                .fechaPago(Instant.parse("2026-07-15T10:00:00Z")).build()));

        servicio.procesar(PagoWebhookService.TIPO_PAGO, "555");

        verify(pagoRepository, never()).save(any());
        verifyNoInteractions(auditoriaService);
    }

    @Test
    @DisplayName("payment que pasa de pendiente a aprobado: actualiza la fila existente")
    void pagoExistente_cambioDeEstadoSeActualiza() {
        var s = suscripcion(EstadoSuscripcion.ACTIVA, PREAPPROVAL_ID);
        var existente = Pago.builder()
                .id(1L).idSuscripcion(10L).mpPaymentId("555")
                .montoArs(new BigDecimal("143550")).estado(EstadoPago.PENDIENTE)
                .fechaPago(Instant.parse("2026-07-15T10:00:00Z")).build();
        when(pasarela.consultarPago(555L))
                .thenReturn(pago("approved", "143550", Instant.parse("2026-07-15T10:00:00Z"),
                        PREAPPROVAL_ID));
        when(suscripcionRepository.findByMpPreapprovalId(PREAPPROVAL_ID))
                .thenReturn(Optional.of(s));
        when(pagoRepository.findByMpPaymentId("555")).thenReturn(Optional.of(existente));

        servicio.procesar(PagoWebhookService.TIPO_PAGO, "555");

        assertThat(existente.getEstado()).isEqualTo(EstadoPago.APROBADO);
        verify(pagoRepository, never()).save(any());
        verify(auditoriaService).registrar(any(AuditoriaEvento.class));
    }

    @Test
    @DisplayName("payment rechazado: marca ultimo_cobro (gracia RD-P8) y avisa por email")
    void pagoRechazado_avisaAlDueno() {
        var s = suscripcion(EstadoSuscripcion.ACTIVA, PREAPPROVAL_ID);
        var dueno = Usuario.builder().id(ID_USUARIO).email("ana@reforma.com").build();
        when(pasarela.consultarPago(557L))
                .thenReturn(pago("rejected", "143550", Instant.parse("2026-07-15T10:00:00Z"),
                        PREAPPROVAL_ID));
        when(suscripcionRepository.findByMpPreapprovalId(PREAPPROVAL_ID))
                .thenReturn(Optional.of(s));
        when(pagoRepository.findByMpPaymentId("557")).thenReturn(Optional.empty());
        when(usuarioRepository.findById(ID_USUARIO)).thenReturn(Optional.of(dueno));

        servicio.procesar(PagoWebhookService.TIPO_PAGO, "557");

        assertThat(s.getUltimoCobroEstado()).isEqualTo(EstadoPago.RECHAZADO);
        assertThat(s.getFechaFinPeriodo()).isEqualTo(Instant.parse("2026-08-01T00:00:00Z"));
        verify(emailNotificacionService).enviarAvisoSuscripcion(eq(dueno), any(), any());
    }

    @Test
    @DisplayName("payment sin suscripción asociada (metadata y referencia ajenas): ignorado")
    void pagoSinSuscripcion_seIgnora() {
        when(pasarela.consultarPago(558L))
                .thenReturn(pago("approved", "100", Instant.parse("2026-07-15T10:00:00Z"), null));

        servicio.procesar(PagoWebhookService.TIPO_PAGO, "558");

        verify(pagoRepository, never()).save(any());
        verifyNoInteractions(auditoriaService);
    }

    // ---- enrutamiento ----

    @Test
    @DisplayName("type desconocido o data.id ausente: sin acción")
    void eventosNoManejados() {
        servicio.procesar("subscription_preapproval_plan", "123");
        servicio.procesar(null, "123");
        servicio.procesar(PagoWebhookService.TIPO_PAGO, null);
        servicio.procesar(PagoWebhookService.TIPO_PAGO, "no-numerico");

        verifyNoInteractions(suscripcionService, pagoRepository, auditoriaService);
    }

    @Test
    @DisplayName("mapeo de estados de MP al enum local")
    void mapeoDeEstados() {
        assertThat(PagoWebhookService.mapearEstado("approved")).isEqualTo(EstadoPago.APROBADO);
        assertThat(PagoWebhookService.mapearEstado("rejected")).isEqualTo(EstadoPago.RECHAZADO);
        assertThat(PagoWebhookService.mapearEstado("cancelled")).isEqualTo(EstadoPago.RECHAZADO);
        assertThat(PagoWebhookService.mapearEstado("refunded")).isEqualTo(EstadoPago.DEVUELTO);
        assertThat(PagoWebhookService.mapearEstado("charged_back")).isEqualTo(EstadoPago.DEVUELTO);
        assertThat(PagoWebhookService.mapearEstado("pending")).isEqualTo(EstadoPago.PENDIENTE);
        assertThat(PagoWebhookService.mapearEstado(null)).isEqualTo(EstadoPago.PENDIENTE);
    }
}
