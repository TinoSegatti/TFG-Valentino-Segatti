package com.reforma.domain.suscripciones.pasarela;

import com.reforma.domain.common.domain.PlanSuscripcion;
import com.reforma.domain.suscripciones.domain.EstadoPago;
import com.reforma.domain.suscripciones.domain.PeriodoFacturacion;
import com.reforma.domain.suscripciones.entity.Suscripcion;
import com.reforma.domain.usuarios.entity.Usuario;
import java.math.BigDecimal;
import java.util.Optional;

/**
 * Abstracción de la pasarela de cobro recurrente (RD-P2, patrón {@code EmailNotificacionService}):
 * dos implementaciones seleccionadas por {@code reforma.pagos.mode} — {@code simulado}
 * ({@link PasarelaSimuladaService}, default: sin credenciales ni red, para dev/demo/tests) y
 * {@code mp} (Mercado Pago real vía SDK, Etapa 4). Toda la lógica de negocio (máquina de
 * estados, upgrades, downgrades, vencimientos) vive FUERA de la pasarela.
 */
public interface PasarelaPagosService {

    String MODO_SIMULADO = "simulado";

    /** Modo activo ({@code simulado} | {@code mp}); el frontend decide la pantalla de pago con esto. */
    String modo();

    /**
     * Inicia el checkout de {@code (plan, periodo)} por {@code montoArs} y devuelve la URL a la
     * que enviar al dueño: el checkout hospedado de MP ({@code init_point}) o la pantalla
     * simulada del frontend. En MP nunca se activa nada acá: la confirmación llega por webhook.
     */
    String iniciarCheckout(Usuario dueno, PlanSuscripcion plan, PeriodoFacturacion periodo, BigDecimal montoArs);

    /** Detiene el cobro recurrente en la pasarela (cancelación RD-P7 / expiración RD-P8). */
    void cancelarCobroRecurrente(Suscripcion suscripcion);

    /** Reanuda el cobro tras des-programar una cancelación aún no aplicada (reactivar). */
    void reanudarCobroRecurrente(Suscripcion suscripcion);

    /** Ajusta el monto recurrente tras aplicar un downgrade (RD-P5, plan B: update del preapproval). */
    void actualizarMontoRecurrente(Suscripcion suscripcion, BigDecimal nuevoMontoArs);

    /**
     * Cobro de renovación de ciclo pedido por el job de vencimientos (RD-P8). La simulada
     * siempre aprueba (como si MP hubiera cobrado); la impl real devuelve {@code empty}
     * porque MP cobra solo y notifica por webhook.
     */
    Optional<EstadoPago> cobrarRenovacion(Suscripcion suscripcion);
}
