package com.reforma.domain.anomalias.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

/** Pedido de evaluación de un precio en el formulario de compra (preview, no persiste). */
public record EvaluarAnomaliaRequest(
        @NotNull @Positive Long idMateriaPrima,
        @NotNull @PositiveOrZero Double precio,
        /** Mes (1-12) de la compra evaluada; habilita la comparación estacional. Opcional. */
        Integer mesReferencia) {}
