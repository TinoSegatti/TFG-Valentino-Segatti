package com.reforma.domain.suscripciones.domain;

/**
 * Estados de {@code t_suscripcion.estado} (máquina de estados comercial, RD-P8).
 *
 * <ul>
 *   <li>{@link #PENDIENTE_PAGO} — checkout iniciado, aún sin autorización de cobro.</li>
 *   <li>{@link #ACTIVA} — cobro autorizado; el plan contratado es el efectivo.</li>
 *   <li>{@link #CANCELADA} — el usuario canceló: no renueva, pero sigue vigente hasta
 *       {@code fecha_fin_periodo} (luego cae a DEMO, RD-P7).</li>
 *   <li>{@link #EXPIRADA} — terminó sin renovación (cancelación aplicada o pagos
 *       rechazados agotada la gracia); el plan efectivo volvió a DEMO.</li>
 * </ul>
 */
public enum EstadoSuscripcion {
    PENDIENTE_PAGO,
    ACTIVA,
    CANCELADA,
    EXPIRADA
}
