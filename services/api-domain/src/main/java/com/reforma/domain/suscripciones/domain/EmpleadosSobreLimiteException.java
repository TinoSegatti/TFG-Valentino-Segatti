package com.reforma.domain.suscripciones.domain;

import com.reforma.domain.common.domain.PlanSuscripcion;
import lombok.Getter;

/**
 * Precondición de empleados del modelo "cumplir antes de bajar" (RD-P6.b.1): no se puede
 * contratar un plan cuyo límite de empleados quede por debajo de los empleados activos.
 * {@code GlobalExceptionHandler} la publica como 409 con payload estructurado
 * ({@code empleadosActivos}, {@code limiteDestino}, {@code excedente}) para que el frontend
 * arme el modal "Gestionar equipo".
 */
@Getter
public class EmpleadosSobreLimiteException extends RuntimeException {

    private final PlanSuscripcion planDestino;
    private final long empleadosActivos;
    private final int limiteDestino;

    public EmpleadosSobreLimiteException(
            PlanSuscripcion planDestino, long empleadosActivos, int limiteDestino) {
        super("Desactivá " + (empleadosActivos - limiteDestino)
                + " integrante(s) del equipo para poder pasar a " + planDestino.name()
                + " (límite: " + limiteDestino + ")");
        this.planDestino = planDestino;
        this.empleadosActivos = empleadosActivos;
        this.limiteDestino = limiteDestino;
    }

    public long getExcedente() {
        return empleadosActivos - limiteDestino;
    }
}
