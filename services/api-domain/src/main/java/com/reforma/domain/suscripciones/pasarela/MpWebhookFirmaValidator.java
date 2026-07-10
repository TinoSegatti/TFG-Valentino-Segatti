package com.reforma.domain.suscripciones.pasarela;

import jakarta.annotation.PostConstruct;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Validación de la firma {@code x-signature} de los webhooks de Mercado Pago (RD-P9).
 *
 * <p>MP firma cada notificación con HMAC-SHA256 sobre el manifest
 * {@code id:{data.id};request-id:{x-request-id};ts:{ts};} usando la clave secreta de la sección
 * Webhooks de la aplicación ({@code MP_WEBHOOK_SECRET}). Reglas de MP: los términos sin valor se
 * omiten del manifest y un {@code data.id} alfanumérico va en minúsculas. Además del hash se
 * exige que {@code ts} (epoch en segundos; verificado contra el panel real) esté dentro de una
 * tolerancia de ±5 minutos, para neutralizar replays de notificaciones capturadas. La comparación
 * es en tiempo constante.
 *
 * <p>Nunca se loguean ni la firma recibida ni el secret; ante firma inválida el controller
 * responde 401 sin procesar nada.
 */
@Component
@Slf4j
@ConditionalOnProperty(prefix = "reforma.pagos", name = "mode", havingValue = "mp")
public class MpWebhookFirmaValidator {

    static final Duration TOLERANCIA_TS = Duration.ofMinutes(5);
    private static final String ALGORITMO = "HmacSHA256";

    private final String secret;

    public MpWebhookFirmaValidator(@Value("${reforma.pagos.mp.webhook-secret:}") String secret) {
        this.secret = secret;
    }

    @PostConstruct
    void validarConfiguracion() {
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException(
                    "reforma.pagos.mode=mp exige MP_WEBHOOK_SECRET (clave secreta de la sección "
                            + "Webhooks del panel de MP) para validar x-signature");
        }
    }

    /**
     * @param xSignature header {@code x-signature} ({@code ts=...,v1=...})
     * @param xRequestId header {@code x-request-id} (puede faltar; se omite del manifest)
     * @param dataId     query param {@code data.id} (puede faltar; se omite del manifest)
     * @param ahora      reloj inyectable para la tolerancia de {@code ts}
     */
    public boolean esValida(String xSignature, String xRequestId, String dataId, Instant ahora) {
        if (xSignature == null || xSignature.isBlank()) {
            return false;
        }
        String ts = null;
        String hashRecibido = null;
        for (String parte : xSignature.split(",")) {
            String[] kv = parte.split("=", 2);
            if (kv.length != 2) {
                continue;
            }
            switch (kv[0].trim()) {
                case "ts" -> ts = kv[1].trim();
                case "v1" -> hashRecibido = kv[1].trim();
                default -> { /* otros términos del header se ignoran */ }
            }
        }
        if (ts == null || hashRecibido == null) {
            return false;
        }
        if (!timestampVigente(ts, ahora)) {
            log.warn("Webhook MP rechazado: ts fuera de la tolerancia de {} min",
                    TOLERANCIA_TS.toMinutes());
            return false;
        }
        String hashEsperado = hmacHex(manifest(dataId, xRequestId, ts));
        return MessageDigest.isEqual(
                hashEsperado.getBytes(StandardCharsets.UTF_8),
                hashRecibido.toLowerCase().getBytes(StandardCharsets.UTF_8));
    }

    /** Términos presentes únicamente (regla de MP); {@code data.id} alfanumérico en minúsculas. */
    static String manifest(String dataId, String xRequestId, String ts) {
        var sb = new StringBuilder();
        if (dataId != null && !dataId.isBlank()) {
            sb.append("id:").append(dataId.toLowerCase()).append(";");
        }
        if (xRequestId != null && !xRequestId.isBlank()) {
            sb.append("request-id:").append(xRequestId).append(";");
        }
        sb.append("ts:").append(ts).append(";");
        return sb.toString();
    }

    private static boolean timestampVigente(String ts, Instant ahora) {
        try {
            long valor = Long.parseLong(ts);
            // MP manda epoch en SEGUNDOS (comprobado contra el panel); se tolera también en
            // milisegundos por si algún emisor lo cambia (>= 1e12 no es plausible en segundos).
            var emitido = valor >= 1_000_000_000_000L
                    ? Instant.ofEpochMilli(valor)
                    : Instant.ofEpochSecond(valor);
            return Duration.between(emitido, ahora).abs().compareTo(TOLERANCIA_TS) <= 0;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private String hmacHex(String manifest) {
        try {
            Mac mac = Mac.getInstance(ALGORITMO);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), ALGORITMO));
            return HexFormat.of().formatHex(mac.doFinal(manifest.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.GeneralSecurityException e) {
            throw new IllegalStateException("No se pudo calcular la firma HMAC del webhook", e);
        }
    }
}
