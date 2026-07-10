package com.reforma.domain.suscripciones.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.reforma.domain.common.domain.PlanSuscripcion;
import com.reforma.domain.suscripciones.domain.PeriodoFacturacion;
import com.reforma.domain.suscripciones.dto.CheckoutRequest;
import com.reforma.domain.suscripciones.dto.CheckoutResponse;
import java.time.Duration;
import java.util.function.Supplier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Ventana de idempotencia del checkout con cobro: el mismo usuario pidiendo el mismo
 * {@code (plan, período)} dentro de la ventana recibe la MISMA respuesta (misma URL de pago)
 * en lugar de crear otro preapproval en Mercado Pago. Cubre doble clic, doble pestaña y
 * retries de red — el guard del frontend ({@code procesando()}) solo cubre el primer caso.
 *
 * <p>{@link Cache#get(Object, java.util.function.Function)} de Caffeine es atómico por clave:
 * dos requests concurrentes del mismo usuario se serializan y la segunda ve la entrada de la
 * primera (límite de concurrencia por usuario, no solo dedupe temporal).
 *
 * <p>Un pedido de un (plan, período) DISTINTO dentro de la ventana es un cambio de intención
 * legítimo: reemplaza la entrada y sí crea un checkout nuevo.
 */
@Component
public class CheckoutIdempotencia {

    private record Entrada(PlanSuscripcion plan, PeriodoFacturacion periodo, CheckoutResponse respuesta) {}

    private final Cache<String, Entrada> enCurso;

    public CheckoutIdempotencia(
            @Value("${reforma.pagos.checkout-ventana-segundos:120}") long ventanaSegundos) {
        this.enCurso = Caffeine.newBuilder()
                .maximumSize(10_000)
                .expireAfterWrite(Duration.ofSeconds(ventanaSegundos))
                .build();
    }

    public CheckoutResponse obtenerOCrear(
            String idUsuario, CheckoutRequest request, Supplier<CheckoutResponse> crear) {
        var entrada = enCurso.get(idUsuario,
                k -> new Entrada(request.plan(), request.periodo(), crear.get()));
        if (entrada.plan() != request.plan() || entrada.periodo() != request.periodo()) {
            entrada = new Entrada(request.plan(), request.periodo(), crear.get());
            enCurso.put(idUsuario, entrada);
        }
        return entrada.respuesta();
    }

    /** Cierra la ventana (p. ej. al activarse la suscripción: la URL cacheada ya no sirve). */
    public void invalidar(String idUsuario) {
        enCurso.invalidate(idUsuario);
    }
}
