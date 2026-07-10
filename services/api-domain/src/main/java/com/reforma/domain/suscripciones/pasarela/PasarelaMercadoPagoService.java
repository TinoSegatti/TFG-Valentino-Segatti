package com.reforma.domain.suscripciones.pasarela;

import com.mercadopago.MercadoPagoConfig;
import com.mercadopago.client.payment.PaymentClient;
import com.mercadopago.client.preapproval.PreApprovalAutoRecurringCreateRequest;
import com.mercadopago.client.preapproval.PreApprovalAutoRecurringUpdateRequest;
import com.mercadopago.client.preapproval.PreapprovalClient;
import com.mercadopago.client.preapproval.PreapprovalCreateRequest;
import com.mercadopago.client.preapproval.PreapprovalUpdateRequest;
import com.mercadopago.exceptions.MPApiException;
import com.mercadopago.exceptions.MPException;
import com.mercadopago.resources.payment.Payment;
import com.mercadopago.resources.preapproval.Preapproval;
import com.reforma.domain.common.domain.PlanSuscripcion;
import com.reforma.domain.suscripciones.domain.EstadoPago;
import com.reforma.domain.suscripciones.domain.PeriodoFacturacion;
import com.reforma.domain.suscripciones.entity.Suscripcion;
import com.reforma.domain.usuarios.entity.Usuario;
import jakarta.annotation.PostConstruct;
import java.math.BigDecimal;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/**
 * Pasarela Mercado Pago real ({@code reforma.pagos.mode=mp}, RD-P1/RD-P13): suscripciones
 * (preapproval) SIN plan asociado, creadas {@code pending} — el dueño completa la tarjeta en el
 * checkout hospedado de MP ({@code init_point}); acá nunca se activa nada: la confirmación llega
 * por webhook (RD-P9). La intención de compra viaja en {@code external_reference}
 * ({@code idUsuario|PLAN|PERIODO}), igual que en el modo simulado viaja por query params.
 *
 * <p>Mapeo de estados con la máquina local:
 * <ul>
 *   <li>{@code cancelarCobroRecurrente} → preapproval {@code paused} (reversible: nuestra
 *       cancelación admite "Mantener mi plan" hasta fin de ciclo, RD-P7).</li>
 *   <li>{@code reanudarCobroRecurrente} → {@code authorized} (deshace la pausa).</li>
 *   <li>{@link #cancelarDefinitivo} → {@code cancelled} (terminal en MP): preapproval viejo
 *       tras un upgrade (RD-P4).</li>
 *   <li>{@code cobrarRenovacion} → {@code empty}: MP cobra solo y notifica por webhook.</li>
 * </ul>
 *
 * <p>Los errores de la API de MP se loguean SIN token ni headers y se devuelven como 502 con
 * mensaje amigable (RD-P13) en los flujos de usuario; en los del job solo se loguean.
 */
@Service
@Slf4j
@ConditionalOnProperty(prefix = "reforma.pagos", name = "mode", havingValue = "mp")
public class PasarelaMercadoPagoService implements PasarelaPagosService {

    static final String MODO_MP = "mp";
    private static final String SEPARADOR_REFERENCIA = "|";

    private final String accessToken;
    private final String backUrlBase;
    private final String payerEmailOverride;
    private final PreapprovalClient preapprovalClient;
    private final PaymentClient paymentClient;

    public PasarelaMercadoPagoService(
            @Value("${reforma.pagos.mp.access-token:}") String accessToken,
            @Value("${reforma.pagos.mp.back-url-base:}") String backUrlBase,
            @Value("${reforma.pagos.mp.payer-email-override:}") String payerEmailOverride) {
        this.accessToken = accessToken;
        this.backUrlBase = backUrlBase;
        this.payerEmailOverride = payerEmailOverride;
        this.preapprovalClient = new PreapprovalClient();
        this.paymentClient = new PaymentClient();
    }

    /** Fail-fast al bootear con mode=mp y credenciales incompletas (mejor que fallar al cobrar). */
    @PostConstruct
    void validarConfiguracion() {
        if (accessToken == null || accessToken.isBlank()) {
            throw new IllegalStateException(
                    "reforma.pagos.mode=mp exige MP_ACCESS_TOKEN (vacío a propósito en el repo; "
                            + "cargarlo en el .env raíz)");
        }
        if (backUrlBase == null || !backUrlBase.startsWith("http")) {
            throw new IllegalStateException(
                    "reforma.pagos.mode=mp exige MP_BACK_URL_BASE con la URL del frontend "
                            + "(adónde vuelve el usuario desde el checkout de MP)");
        }
        MercadoPagoConfig.setAccessToken(accessToken);
        log.info("Pasarela Mercado Pago habilitada (token {}, back_url_base {})",
                accessToken.startsWith("TEST-") ? "de PRUEBA" : "productivo", backUrlBase);
    }

    @Override
    public String modo() {
        return MODO_MP;
    }

    @Override
    public String iniciarCheckout(
            Usuario dueno, PlanSuscripcion plan, PeriodoFacturacion periodo, BigDecimal montoArs) {
        var request = PreapprovalCreateRequest.builder()
                .reason("REFORMA - Plan " + plan.name() + " (" + periodo.etiqueta() + ")")
                .externalReference(referenciaExterna(dueno.getId(), plan, periodo))
                .payerEmail(payerEmailOverride.isBlank() ? dueno.getEmail() : payerEmailOverride)
                .backUrl(backUrlBase + "/planes/retorno")
                .status("pending")
                .autoRecurring(PreApprovalAutoRecurringCreateRequest.builder()
                        .frequency(periodo == PeriodoFacturacion.MENSUAL ? 1 : 12)
                        .frequencyType("months")
                        .transactionAmount(montoArs)
                        .currencyId("ARS")
                        .build())
                .build();
        try {
            Preapproval preapproval = preapprovalClient.create(request);
            log.info("[PAGOS:MP] preapproval {} creado: {} {} (${} ARS) para {}",
                    preapproval.getId(), plan, periodo, montoArs, dueno.getEmail());
            return preapproval.getInitPoint();
        } catch (MPException | MPApiException e) {
            throw errorPasarela("crear el checkout", e);
        }
    }

    @Override
    public void cancelarCobroRecurrente(Suscripcion suscripcion) {
        cambiarEstadoPreapproval(suscripcion.getMpPreapprovalId(), "paused", "pausar el cobro", true);
    }

    @Override
    public void reanudarCobroRecurrente(Suscripcion suscripcion) {
        cambiarEstadoPreapproval(
                suscripcion.getMpPreapprovalId(), "authorized", "reanudar el cobro", true);
    }

    /** Cierre terminal en MP del preapproval reemplazado por un upgrade (RD-P4) o expirado. */
    public void cancelarDefinitivo(String mpPreapprovalId) {
        cambiarEstadoPreapproval(mpPreapprovalId, "cancelled", "cancelar el preapproval", false);
    }

    @Override
    public void actualizarMontoRecurrente(Suscripcion suscripcion, BigDecimal nuevoMontoArs) {
        if (suscripcion.getMpPreapprovalId() == null) {
            return;
        }
        // Lo invoca el job de downgrades (RD-P5 plan B): un fallo no debe frenar la corrida —
        // el plan local ya bajó (el usuario no paga de más); se loguea para intervenir a mano.
        try {
            preapprovalClient.update(suscripcion.getMpPreapprovalId(),
                    PreapprovalUpdateRequest.builder()
                            .autoRecurring(PreApprovalAutoRecurringUpdateRequest.builder()
                                    .transactionAmount(nuevoMontoArs)
                                    .build())
                            .build());
            log.info("[PAGOS:MP] preapproval {} actualizado a ${} ARS",
                    suscripcion.getMpPreapprovalId(), nuevoMontoArs);
        } catch (MPException | MPApiException e) {
            log.error("[PAGOS:MP] no se pudo actualizar el monto del preapproval {}: {}",
                    suscripcion.getMpPreapprovalId(), detalle(e));
        }
    }

    @Override
    public Optional<EstadoPago> cobrarRenovacion(Suscripcion suscripcion) {
        // MP cobra la recurrencia por su cuenta; el resultado llega por webhook (RD-P8).
        return Optional.empty();
    }

    // ------------------------------------------------- consultas para el webhook (RD-P9)

    /**
     * Re-consulta el preapproval a MP: el webhook nunca confía en el payload recibido.
     * {@code null} si MP no lo reconoce — 404 (id ajeno) o 400 (id con formato inválido, p. ej.
     * la prueba del panel manda {@code data.id=123456}) — el webhook lo ignora con 200;
     * reintentar no lo volvería procesable.
     */
    public Preapproval consultarPreapproval(String mpPreapprovalId) {
        try {
            return preapprovalClient.get(mpPreapprovalId);
        } catch (MPApiException e) {
            if (idDesconocido(e)) {
                return null;
            }
            throw errorPasarela("consultar la suscripción " + mpPreapprovalId, e);
        } catch (MPException e) {
            throw errorPasarela("consultar la suscripción " + mpPreapprovalId, e);
        }
    }

    /** Re-consulta un pago a MP (evento {@code payment}); {@code null} si MP no lo reconoce. */
    public Payment consultarPago(Long mpPaymentId) {
        try {
            return paymentClient.get(mpPaymentId);
        } catch (MPApiException e) {
            if (idDesconocido(e)) {
                return null;
            }
            throw errorPasarela("consultar el pago " + mpPaymentId, e);
        } catch (MPException e) {
            throw errorPasarela("consultar el pago " + mpPaymentId, e);
        }
    }

    private static boolean idDesconocido(MPApiException e) {
        return e.getStatusCode() == 404 || e.getStatusCode() == 400;
    }

    // ------------------------------------------------------------------ referencia externa

    /** {@code external_reference = idUsuario|PLAN|PERIODO}: la intención de compra (RD-P4). */
    static String referenciaExterna(String idUsuario, PlanSuscripcion plan, PeriodoFacturacion periodo) {
        return idUsuario + SEPARADOR_REFERENCIA + plan.name() + SEPARADOR_REFERENCIA + periodo.name();
    }

    /** Parte inversa de {@link #referenciaExterna}; vacío si la referencia no es nuestra. */
    public static Optional<ReferenciaCheckout> parsearReferencia(String externalReference) {
        if (externalReference == null) {
            return Optional.empty();
        }
        String[] partes = externalReference.split("\\" + SEPARADOR_REFERENCIA);
        if (partes.length != 3) {
            return Optional.empty();
        }
        try {
            return Optional.of(new ReferenciaCheckout(
                    partes[0], PlanSuscripcion.valueOf(partes[1]), PeriodoFacturacion.valueOf(partes[2])));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    /** Intención de compra reconstruida desde {@code external_reference}. */
    public record ReferenciaCheckout(
            String idUsuario, PlanSuscripcion plan, PeriodoFacturacion periodo) {}

    // ------------------------------------------------------------------ helpers

    private void cambiarEstadoPreapproval(
            String mpPreapprovalId, String estado, String operacion, boolean propagarError) {
        if (mpPreapprovalId == null) {
            return;
        }
        try {
            preapprovalClient.update(mpPreapprovalId,
                    PreapprovalUpdateRequest.builder().status(estado).build());
            log.info("[PAGOS:MP] preapproval {} -> {}", mpPreapprovalId, estado);
        } catch (MPException | MPApiException e) {
            if (propagarError) {
                throw errorPasarela(operacion, e);
            }
            log.error("[PAGOS:MP] no se pudo {} ({}): {}", operacion, mpPreapprovalId, detalle(e));
        }
    }

    /** Log del error real (sin token) + 502 amigable al frontend (RD-P13). */
    private ResponseStatusException errorPasarela(String operacion, Exception e) {
        log.error("[PAGOS:MP] error al {}: {}", operacion, detalle(e));
        return new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                "No pudimos " + operacion + " con Mercado Pago; probá de nuevo en unos minutos");
    }

    private static String detalle(Exception e) {
        if (e instanceof MPApiException api && api.getApiResponse() != null) {
            return "HTTP " + api.getStatusCode() + " " + api.getApiResponse().getContent();
        }
        return e.getMessage();
    }
}
