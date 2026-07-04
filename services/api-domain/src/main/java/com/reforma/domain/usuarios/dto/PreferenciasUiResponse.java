package com.reforma.domain.usuarios.dto;

import java.math.BigDecimal;

/**
 * Preferencias de UI vigentes del usuario (con defaults si nunca personalizó).
 * {@code imagenPersonalizada} solo viene cuando {@code fondo = "personalizada"}.
 */
public record PreferenciasUiResponse(
        String fondo, BigDecimal intensidadCortina, String imagenPersonalizada) {}
