package com.reforma.domain.suscripciones.dto;

import com.reforma.domain.common.domain.PlanSuscripcion;
import java.math.BigDecimal;

/**
 * Card del catálogo público de planes ({@code GET /api/suscripcion/planes}). Los valores se
 * construyen desde {@code PlanService} (límites) y {@code PrecioPlanService} (precios): el
 * catálogo nunca duplica números en código ni en el frontend.
 *
 * <p>En {@code limites}, {@code null} significa <em>ilimitado</em> (ENTERPRISE).
 */
public record PlanCatalogoResponse(
        PlanSuscripcion plan,
        BigDecimal precioMensualArs,
        BigDecimal precioAnualArs,
        LimitesPlan limites,
        boolean prediccionStock) {

    public record LimitesPlan(
            Integer granjas,
            Integer empleados,
            Integer materiasPrimas,
            Integer proveedores,
            Integer animales,
            Integer formulas,
            Integer fabricaciones,
            Integer archivos) {}
}
