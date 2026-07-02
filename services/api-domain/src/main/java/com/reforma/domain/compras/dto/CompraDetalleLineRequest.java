package com.reforma.domain.compras.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

public record CompraDetalleLineRequest(
        @NotNull @Positive Long idMateriaPrima,
        @NotNull @PositiveOrZero Double cantidadKg,
        @NotNull @PositiveOrZero Double precioPorKilo,
        @NotNull @PositiveOrZero Double subtotal,
        /** El usuario confirmó este precio pese a la alerta de anomalía (RF-IA-ANOM-002). Opcional. */
        Boolean confirmoPrecio) {

    /** Constructor de compatibilidad (líneas sin confirmación de anomalía). */
    public CompraDetalleLineRequest(
            Long idMateriaPrima, Double cantidadKg, Double precioPorKilo, Double subtotal) {
        this(idMateriaPrima, cantidadKg, precioPorKilo, subtotal, null);
    }
}
