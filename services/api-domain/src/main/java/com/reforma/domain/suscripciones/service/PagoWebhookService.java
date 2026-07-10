package com.reforma.domain.suscripciones.service;

import com.mercadopago.resources.payment.Payment;
import com.reforma.domain.auditoria.domain.AccionAuditoria;
import com.reforma.domain.auditoria.dto.AuditoriaEvento;
import com.reforma.domain.auditoria.service.AuditoriaService;
import com.reforma.domain.common.domain.PlanSuscripcion;
import com.reforma.domain.suscripciones.domain.EmpleadosSobreLimiteException;
import com.reforma.domain.suscripciones.domain.EstadoPago;
import com.reforma.domain.suscripciones.domain.EstadoSuscripcion;
import com.reforma.domain.suscripciones.entity.Pago;
import com.reforma.domain.suscripciones.entity.Suscripcion;
import com.reforma.domain.suscripciones.pasarela.PasarelaMercadoPagoService;
import com.reforma.domain.suscripciones.repository.PagoRepository;
import com.reforma.domain.suscripciones.repository.SuscripcionRepository;
import com.reforma.domain.usuarios.email.EmailNotificacionService;
import com.reforma.domain.usuarios.repository.UsuarioRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;

/**
 * Procesa las notificaciones del webhook de Mercado Pago (RD-P9), ya autenticadas por
 * {@code MpWebhookFirmaValidator}. Reglas:
 *
 * <ul>
 *   <li><b>No se confía en el payload:</b> del evento solo se toman {@code type} y
 *       {@code data.id}; el recurso se re-consulta a la API de MP.</li>
 *   <li><b>Idempotente:</b> pagos con upsert por {@code mp_payment_id} (UNIQUE); transiciones
 *       que re-aplican el mismo estado son no-op. MP reintenta ante no-200, así que un error
 *       transitorio (MP caído, BD caída) se propaga para que la notificación se re-entregue.</li>
 *   <li><b>Eventos ignorados</b> (id desconocido, referencia ajena, estado sin acción) se
 *       loguean y devuelven 200: reintentarlos no los volvería procesables.</li>
 * </ul>
 *
 * <p>Eventos manejados: {@code subscription_preapproval} (alta/upgrade → activa la máquina de
 * estados; cancelación/pausa desde MP → replica local), {@code payment} (registra el cobro,
 * mantiene {@code ultimo_cobro} para la gracia RD-P8 y extiende el ciclo en las renovaciones) y
 * {@code subscription_authorized_payment} (cuota de la suscripción → se resuelve al pago real).
 */
@Service
@Slf4j
@ConditionalOnProperty(prefix = "reforma.pagos", name = "mode", havingValue = "mp")
public class PagoWebhookService {

    public static final String TIPO_PREAPPROVAL = "subscription_preapproval";
    public static final String TIPO_PAGO = "payment";
    public static final String TIPO_CUOTA = "subscription_authorized_payment";

    private static final String TABLA_SUSCRIPCION = "t_suscripcion";

    private final PasarelaMercadoPagoService pasarela;
    private final SuscripcionService suscripcionService;
    private final SuscripcionRepository suscripcionRepository;
    private final PagoRepository pagoRepository;
    private final UsuarioRepository usuarioRepository;
    private final EmailNotificacionService emailNotificacionService;
    private final AuditoriaService auditoriaService;
    private final RestClient mpRestClient;

    public PagoWebhookService(
            PasarelaMercadoPagoService pasarela,
            SuscripcionService suscripcionService,
            SuscripcionRepository suscripcionRepository,
            PagoRepository pagoRepository,
            UsuarioRepository usuarioRepository,
            EmailNotificacionService emailNotificacionService,
            AuditoriaService auditoriaService,
            RestClient.Builder restClientBuilder,
            @Value("${reforma.pagos.mp.access-token:}") String accessToken) {
        this.pasarela = pasarela;
        this.suscripcionService = suscripcionService;
        this.suscripcionRepository = suscripcionRepository;
        this.pagoRepository = pagoRepository;
        this.usuarioRepository = usuarioRepository;
        this.emailNotificacionService = emailNotificacionService;
        this.auditoriaService = auditoriaService;
        // El SDK no trae cliente de /authorized_payments; se consulta directo (patrón MlClient).
        this.mpRestClient = restClientBuilder
                .baseUrl("https://api.mercadopago.com")
                .defaultHeader("Authorization", "Bearer " + accessToken)
                .build();
    }

    /** Punto de entrada del controller: enruta por {@code type} (ya validada la firma). */
    @Transactional
    public void procesar(String type, String dataId) {
        if (dataId == null || dataId.isBlank()) {
            log.warn("Webhook MP sin data.id (type {}): ignorado", type);
            return;
        }
        switch (type == null ? "" : type) {
            case TIPO_PREAPPROVAL -> procesarPreapproval(dataId);
            case TIPO_PAGO -> procesarPago(dataId);
            case TIPO_CUOTA -> procesarCuota(dataId);
            default -> log.info("Webhook MP type {} (id {}): sin acción", type, dataId);
        }
    }

    // ------------------------------------------------- subscription_preapproval

    private void procesarPreapproval(String preapprovalId) {
        var preapproval = pasarela.consultarPreapproval(preapprovalId);
        if (preapproval == null) {
            log.info("Webhook MP: preapproval {} desconocido para MP (prueba del panel o id "
                    + "ajeno): ignorado", preapprovalId);
            return;
        }
        var referencia = PasarelaMercadoPagoService
                .parsearReferencia(preapproval.getExternalReference())
                .orElse(null);
        if (referencia == null) {
            log.warn("Webhook MP: preapproval {} con external_reference ajena: ignorado",
                    preapprovalId);
            return;
        }
        var estado = preapproval.getStatus();
        log.info("Webhook MP: preapproval {} -> {} ({} {})",
                preapprovalId, estado, referencia.plan(), referencia.periodo());
        switch (estado == null ? "" : estado) {
            case "authorized" -> activar(preapprovalId, preapproval.getAutoRecurring() != null
                    ? preapproval.getAutoRecurring().getTransactionAmount()
                    : null, referencia);
            case "cancelled", "paused" -> replicarCancelacion(preapprovalId, referencia.idUsuario());
            default -> { /* pending: el comprador todavía no autorizó — nada que hacer */ }
        }
    }

    /** Preapproval autorizado: activa (RD-P4) y cierra en MP el preapproval reemplazado. */
    private void activar(String preapprovalId, @Nullable BigDecimal monto,
            PasarelaMercadoPagoService.ReferenciaCheckout ref) {
        var existente = suscripcionRepository.findByIdUsuario(ref.idUsuario()).orElse(null);
        if (existente != null && preapprovalId.equals(existente.getMpPreapprovalId())
                && existente.getEstado() == EstadoSuscripcion.ACTIVA
                && existente.getPlan() == ref.plan()
                && existente.getPeriodo() == ref.periodo()) {
            return; // notificación repetida: no-op (RD-P9)
        }
        String preapprovalAnterior = existente != null ? existente.getMpPreapprovalId() : null;
        try {
            suscripcionService.activarDesdePasarela(
                    ref.idUsuario(), ref.plan(), ref.periodo(), monto, preapprovalId);
        } catch (EmpleadosSobreLimiteException e) {
            // Prácticamente inalcanzable (un upgrade nunca baja el límite); si pasa, no se
            // activa y queda registro para resolverlo a mano. 200 a MP: reintentar no ayuda.
            log.error("Webhook MP: preapproval {} autorizado pero el tenant {} excede el límite "
                    + "de empleados del plan {}: activación omitida",
                    preapprovalId, ref.idUsuario(), ref.plan());
            return;
        }
        if (preapprovalAnterior != null && !preapprovalAnterior.equals(preapprovalId)) {
            pasarela.cancelarDefinitivo(preapprovalAnterior);
        }
    }

    /** Cancelación/pausa hecha en MP (app de MP, soporte): se replica la RD-P7 local. */
    private void replicarCancelacion(String preapprovalId, String idUsuario) {
        var s = suscripcionRepository.findByIdUsuario(idUsuario)
                .filter(x -> preapprovalId.equals(x.getMpPreapprovalId()))
                .orElse(null);
        if (s == null || s.getEstado() != EstadoSuscripcion.ACTIVA) {
            return; // ya cancelada localmente (nuestro propio "paused") o sin correlato: no-op
        }
        s.setEstado(EstadoSuscripcion.CANCELADA);
        s.setPlanPendiente(PlanSuscripcion.DEMO);
        s.setPeriodoPendiente(null);
        s.setFechaActualizacion(Instant.now());
        auditar(idUsuario, s.getId(), AccionAuditoria.SUSCRIPCION_CANCELADA,
                "Suscripción cancelada desde Mercado Pago",
                Map.of("plan", s.getPlan().name(),
                        "vigenteHasta", String.valueOf(s.getFechaFinPeriodo())));
        log.info("Webhook MP: cancelación replicada para la suscripción {}", s.getId());
    }

    // ------------------------------------------------------------------- payment

    private void procesarPago(String paymentId) {
        Payment pago;
        try {
            pago = pasarela.consultarPago(Long.valueOf(paymentId));
        } catch (NumberFormatException e) {
            log.warn("Webhook MP: payment id no numérico ({}): ignorado", paymentId);
            return;
        }
        if (pago == null) {
            log.info("Webhook MP: payment {} desconocido para MP: ignorado", paymentId);
            return;
        }
        var s = suscripcionDelPago(pago);
        if (s == null) {
            log.info("Webhook MP: payment {} sin suscripción asociada: ignorado", paymentId);
            return;
        }
        var estado = mapearEstado(pago.getStatus());
        var monto = pago.getTransactionAmount() != null
                ? pago.getTransactionAmount() : s.getPrecioArs();
        var fecha = pago.getDateCreated() != null
                ? pago.getDateCreated().toInstant() : Instant.now();

        var existentePago = pagoRepository.findByMpPaymentId(paymentId).orElse(null);
        if (existentePago != null) {
            if (existentePago.getEstado() == estado) {
                return; // notificación repetida: no-op (RD-P9)
            }
            existentePago.setEstado(estado); // p. ej. PENDIENTE→APROBADO o APROBADO→DEVUELTO
        } else {
            // La descripción sale del external_reference del PAGO (plan/período del checkout
            // que lo generó): en un upgrade, el webhook payment puede llegar antes que el
            // preapproval "authorized" y el plan local todavía es el viejo.
            var descripcion = PasarelaMercadoPagoService.parsearReferencia(pago.getExternalReference())
                    .map(ref -> SuscripcionService.descripcionPago(ref.plan(), ref.periodo()))
                    .orElseGet(() -> SuscripcionService.descripcionPago(s.getPlan(), s.getPeriodo()));
            pagoRepository.save(Pago.builder()
                    .idSuscripcion(s.getId())
                    .mpPaymentId(paymentId)
                    .montoArs(monto)
                    .estado(estado)
                    .descripcion(descripcion)
                    .fechaPago(fecha)
                    .build());
        }
        auditar(s.getIdUsuario(), s.getId(), AccionAuditoria.PAGO_REGISTRADO,
                "Cobro notificado por Mercado Pago: " + estado.name().toLowerCase(),
                Map.of("plan", s.getPlan().name(),
                        "montoArs", monto.toPlainString(),
                        "mpPaymentId", paymentId));
        actualizarSuscripcionPorCobro(s, estado, fecha);
    }

    /**
     * Mantiene la máquina local al día con el cobro: {@code ultimo_cobro} alimenta la gracia
     * (RD-P8) y un aprobado con ciclo vencido lo extiende (renovación cobrada por MP antes o
     * después de que el job del 04:30 la asiente — la que llegue segunda ve el ciclo vigente).
     */
    private void actualizarSuscripcionPorCobro(Suscripcion s, EstadoPago estado, Instant fecha) {
        if (s.getEstado() != EstadoSuscripcion.ACTIVA && s.getEstado() != EstadoSuscripcion.CANCELADA) {
            return; // PENDIENTE_PAGO la activa el evento de preapproval; EXPIRADA es historia
        }
        s.setUltimoCobroEstado(estado);
        s.setUltimoCobroFecha(fecha);
        s.setFechaActualizacion(Instant.now());
        if (estado == EstadoPago.APROBADO && s.getFechaFinPeriodo() != null
                && !s.getFechaFinPeriodo().isAfter(fecha)) {
            var inicioNuevoCiclo = s.getFechaFinPeriodo();
            s.setFechaInicio(inicioNuevoCiclo);
            s.setFechaFinPeriodo(s.getPeriodo().finDeCiclo(inicioNuevoCiclo));
        }
        if (estado == EstadoPago.RECHAZADO) {
            usuarioRepository.findById(s.getIdUsuario()).ifPresent(dueno ->
                    emailNotificacionService.enviarAvisoSuscripcion(dueno,
                            "No pudimos cobrar tu suscripción de REFORMA",
                            "El cobro de tu plan " + s.getPlan().name() + " fue rechazado."
                                    + " Mercado Pago va a reintentar; si el pago no se acredita,"
                                    + " tu cuenta pasará a DEMO al agotarse la gracia."));
        }
    }

    /** Vincula el pago: metadata {@code preapproval_id} primero, external_reference después. */
    @Nullable
    private Suscripcion suscripcionDelPago(Payment pago) {
        if (pago.getMetadata() != null) {
            Object preapprovalId = pago.getMetadata().get("preapproval_id");
            if (preapprovalId instanceof String id && !id.isBlank()) {
                var porPreapproval = suscripcionRepository.findByMpPreapprovalId(id).orElse(null);
                if (porPreapproval != null) {
                    return porPreapproval;
                }
            }
        }
        return PasarelaMercadoPagoService.parsearReferencia(pago.getExternalReference())
                .flatMap(ref -> suscripcionRepository.findByIdUsuario(ref.idUsuario()))
                .orElse(null);
    }

    // ------------------------------------------- subscription_authorized_payment

    /** Cuota de suscripción: se resuelve el pago real que la ejecutó y se delega en él. */
    private void procesarCuota(String authorizedPaymentId) {
        CuotaMp cuota;
        try {
            cuota = mpRestClient.get()
                    .uri("/authorized_payments/{id}", authorizedPaymentId)
                    .retrieve()
                    .body(CuotaMp.class);
        } catch (org.springframework.web.client.HttpClientErrorException.NotFound e) {
            log.info("Webhook MP: cuota {} desconocida para MP: ignorada", authorizedPaymentId);
            return;
        }
        if (cuota == null || cuota.payment() == null || cuota.payment().id() == null) {
            log.info("Webhook MP: cuota {} aún sin pago ejecutado: ignorada", authorizedPaymentId);
            return;
        }
        procesarPago(String.valueOf(cuota.payment().id()));
    }

    /** Subconjunto relevante de GET /authorized_payments/{id}. */
    record CuotaMp(PagoDeCuota payment) {
        record PagoDeCuota(Long id) {}
    }

    // ------------------------------------------------------------------ helpers

    static EstadoPago mapearEstado(String estadoMp) {
        return switch (estadoMp == null ? "" : estadoMp) {
            case "approved" -> EstadoPago.APROBADO;
            case "rejected", "cancelled" -> EstadoPago.RECHAZADO;
            case "refunded", "charged_back" -> EstadoPago.DEVUELTO;
            default -> EstadoPago.PENDIENTE; // pending / in_process / authorized
        };
    }

    private void auditar(String idUsuario, Long idSuscripcion, AccionAuditoria accion,
            String descripcion, Object datosNuevos) {
        auditoriaService.registrar(AuditoriaEvento.builder()
                .idUsuario(idUsuario)
                .tablaOrigen(TABLA_SUSCRIPCION)
                .idRegistro(String.valueOf(idSuscripcion))
                .accion(accion)
                .descripcion(descripcion)
                .datosNuevos(datosNuevos)
                .build());
    }
}
