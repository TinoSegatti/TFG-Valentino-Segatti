package com.reforma.domain.suscripciones.pasarela;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * RD-P9: la firma {@code x-signature} de MP es HMAC-SHA256(secret) sobre el manifest
 * {@code id:{data.id};request-id:{x-request-id};ts:{ts};} (términos ausentes se omiten,
 * data.id alfanumérico en minúsculas), con tolerancia de ±5 minutos sobre {@code ts}.
 */
class MpWebhookFirmaValidatorTest {

    private static final String SECRET = "clave-secreta-de-prueba";
    private static final Instant AHORA = Instant.parse("2026-07-07T12:00:00Z");
    private static final String REQUEST_ID = "req-abc-123";
    private static final String DATA_ID = "2c9380847e9b451c017ea1bd70ba0219";

    private final MpWebhookFirmaValidator validator = new MpWebhookFirmaValidator(SECRET);

    private static String firmar(String secret, String manifest) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(manifest.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String header(String ts, String v1) {
        return "ts=" + ts + ",v1=" + v1;
    }

    @Test
    @DisplayName("firma correcta dentro de la tolerancia → válida (ts en segundos, como manda MP)")
    void firmaCorrecta() {
        String ts = String.valueOf(AHORA.getEpochSecond());
        String v1 = firmar(SECRET, "id:" + DATA_ID + ";request-id:" + REQUEST_ID + ";ts:" + ts + ";");

        assertThat(validator.esValida(header(ts, v1), REQUEST_ID, DATA_ID, AHORA)).isTrue();
    }

    @Test
    @DisplayName("ts en milisegundos también se acepta (defensivo ante un cambio del emisor)")
    void timestampEnMilisegundos() {
        String ts = String.valueOf(AHORA.toEpochMilli());
        String v1 = firmar(SECRET, "id:" + DATA_ID + ";request-id:" + REQUEST_ID + ";ts:" + ts + ";");

        assertThat(validator.esValida(header(ts, v1), REQUEST_ID, DATA_ID, AHORA)).isTrue();
    }

    @Test
    @DisplayName("data.id alfanumérico en mayúsculas se firma en minúsculas (regla de MP)")
    void dataIdEnMayusculas() {
        String ts = String.valueOf(AHORA.getEpochSecond());
        String v1 = firmar(SECRET, "id:" + DATA_ID + ";request-id:" + REQUEST_ID + ";ts:" + ts + ";");

        assertThat(validator.esValida(
                header(ts, v1), REQUEST_ID, DATA_ID.toUpperCase(), AHORA)).isTrue();
    }

    @Test
    @DisplayName("sin data.id ni request-id el manifest solo lleva ts (términos ausentes se omiten)")
    void manifestSinTerminosAusentes() {
        String ts = String.valueOf(AHORA.getEpochSecond());
        String v1 = firmar(SECRET, "ts:" + ts + ";");

        assertThat(validator.esValida(header(ts, v1), null, null, AHORA)).isTrue();
    }

    @Test
    @DisplayName("firmada con otro secret → inválida")
    void otroSecret() {
        String ts = String.valueOf(AHORA.getEpochSecond());
        String v1 = firmar("otro-secret",
                "id:" + DATA_ID + ";request-id:" + REQUEST_ID + ";ts:" + ts + ";");

        assertThat(validator.esValida(header(ts, v1), REQUEST_ID, DATA_ID, AHORA)).isFalse();
    }

    @Test
    @DisplayName("ts fuera de la tolerancia de 5 minutos → inválida aunque el hash cierre")
    void timestampVencido() {
        var emitido = AHORA.minus(MpWebhookFirmaValidator.TOLERANCIA_TS).minusSeconds(1);
        String ts = String.valueOf(emitido.getEpochSecond());
        String v1 = firmar(SECRET, "id:" + DATA_ID + ";request-id:" + REQUEST_ID + ";ts:" + ts + ";");

        assertThat(validator.esValida(header(ts, v1), REQUEST_ID, DATA_ID, AHORA)).isFalse();
    }

    @Test
    @DisplayName("header ausente, vacío o malformado → inválida")
    void headerInvalido() {
        assertThat(validator.esValida(null, REQUEST_ID, DATA_ID, AHORA)).isFalse();
        assertThat(validator.esValida("", REQUEST_ID, DATA_ID, AHORA)).isFalse();
        assertThat(validator.esValida("basura-sin-formato", REQUEST_ID, DATA_ID, AHORA)).isFalse();
        assertThat(validator.esValida("ts=123", REQUEST_ID, DATA_ID, AHORA)).isFalse();
        assertThat(validator.esValida("v1=abc", REQUEST_ID, DATA_ID, AHORA)).isFalse();
        assertThat(validator.esValida("ts=no-numerico,v1=abc", REQUEST_ID, DATA_ID, AHORA)).isFalse();
    }

    @Test
    @DisplayName("con mode=mp el secret vacío tira al bootear (fail-fast)")
    void secretVacioFallaAlBootear() {
        assertThatThrownBy(() -> new MpWebhookFirmaValidator(" ").validarConfiguracion())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("MP_WEBHOOK_SECRET");
    }
}
