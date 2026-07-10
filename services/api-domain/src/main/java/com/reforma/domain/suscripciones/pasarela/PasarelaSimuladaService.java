package com.reforma.domain.suscripciones.pasarela;

import com.reforma.domain.common.domain.PlanSuscripcion;
import com.reforma.domain.config.ReformaProperties;
import com.reforma.domain.suscripciones.domain.EstadoPago;
import com.reforma.domain.suscripciones.domain.PeriodoFacturacion;
import com.reforma.domain.suscripciones.entity.Suscripcion;
import com.reforma.domain.usuarios.entity.Usuario;
import java.math.BigDecimal;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * Pasarela simulada (default, {@code reforma.pagos.mode=simulado}): el "checkout" es una
 * pantalla propia del frontend que aprueba o rechaza al instante vía
 * {@code POST /api/suscripcion/confirmar-simulado} — sin credenciales, sin URL pública,
 * demo 100 % reproducible offline (RD-P2). No habla con ningún servicio externo.
 */
@Service
@Slf4j
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "reforma.pagos", name = "mode", havingValue = "simulado",
        matchIfMissing = true)
public class PasarelaSimuladaService implements PasarelaPagosService {

    private final ReformaProperties properties;

    @Override
    public String modo() {
        return MODO_SIMULADO;
    }

    @Override
    public String iniciarCheckout(
            Usuario dueno, PlanSuscripcion plan, PeriodoFacturacion periodo, BigDecimal montoArs) {
        var url = properties.frontendUrl() + "/planes/checkout-simulado?plan=" + plan.name()
                + "&periodo=" + periodo.name();
        log.info("[PAGOS:SIMULADO] checkout {} {} (${} ARS) para {} -> {}",
                plan, periodo, montoArs, dueno.getEmail(), url);
        return url;
    }

    @Override
    public void cancelarCobroRecurrente(Suscripcion suscripcion) {
        log.info("[PAGOS:SIMULADO] cobro recurrente detenido para la suscripción {}", suscripcion.getId());
    }

    @Override
    public void reanudarCobroRecurrente(Suscripcion suscripcion) {
        log.info("[PAGOS:SIMULADO] cobro recurrente reanudado para la suscripción {}", suscripcion.getId());
    }

    @Override
    public void actualizarMontoRecurrente(Suscripcion suscripcion, BigDecimal nuevoMontoArs) {
        log.info("[PAGOS:SIMULADO] monto recurrente de la suscripción {} actualizado a ${} ARS",
                suscripcion.getId(), nuevoMontoArs);
    }

    @Override
    public Optional<EstadoPago> cobrarRenovacion(Suscripcion suscripcion) {
        // Como si MP hubiera cobrado el ciclo: la renovación simulada siempre aprueba.
        return Optional.of(EstadoPago.APROBADO);
    }
}
