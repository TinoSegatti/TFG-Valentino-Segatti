package com.reforma.domain.suscripciones.controller;

import com.reforma.domain.suscripciones.pasarela.MpWebhookFirmaValidator;
import com.reforma.domain.suscripciones.service.PagoWebhookService;
import java.time.Instant;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Webhook de Mercado Pago ({@code POST /api/pagos/webhook}, RD-P9): público en
 * {@code SecurityConfig} (MP no manda JWT) pero autenticado por la firma HMAC
 * {@code x-signature} — sin firma válida se responde 401 y no se procesa nada. Solo existe con
 * {@code reforma.pagos.mode=mp}; en modo simulado la ruta ni se registra (404).
 *
 * <p>Responde 200 rápido: el procesamiento re-consulta a MP y aplica transiciones idempotentes
 * ({@link PagoWebhookService}); un error transitorio responde no-2xx para que MP reintente.
 * Nunca se loguean la firma, el secret ni el payload crudo.
 */
@RestController
@Slf4j
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "reforma.pagos", name = "mode", havingValue = "mp")
public class PagoWebhookRestController {

    private final MpWebhookFirmaValidator firmaValidator;
    private final PagoWebhookService webhookService;

    @PostMapping("/api/pagos/webhook")
    public ResponseEntity<Void> recibir(
            @RequestHeader(value = "x-signature", required = false) String xSignature,
            @RequestHeader(value = "x-request-id", required = false) String xRequestId,
            @RequestParam Map<String, String> params,
            @RequestBody(required = false) Map<String, Object> body) {
        // La firma se calcula sobre el data.id de la QUERY (así lo define MP); el body solo
        // sirve de fallback para el enrutamiento cuando la query no lo trae.
        String dataIdQuery = params.get("data.id");
        if (!firmaValidator.esValida(xSignature, xRequestId, dataIdQuery, Instant.now())) {
            log.warn("Webhook MP rechazado: firma x-signature inválida o ausente");
            return ResponseEntity.status(401).build();
        }
        String type = primerNoVacio(params.get("type"), params.get("topic"), deBody(body, "type"));
        String dataId = primerNoVacio(dataIdQuery, dataIdDeBody(body), params.get("id"));
        webhookService.procesar(type, dataId);
        return ResponseEntity.ok().build();
    }

    private static String primerNoVacio(String... valores) {
        for (String v : valores) {
            if (v != null && !v.isBlank()) {
                return v;
            }
        }
        return null;
    }

    private static String deBody(Map<String, Object> body, String campo) {
        return body != null && body.get(campo) instanceof String s ? s : null;
    }

    private static String dataIdDeBody(Map<String, Object> body) {
        if (body != null && body.get("data") instanceof Map<?, ?> data
                && data.get("id") != null) {
            return String.valueOf(data.get("id"));
        }
        return null;
    }
}
