package com.reforma.domain.usuarios.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

/**
 * Preferencias de UI a guardar (módulo Personalización). {@code fondo} es una clave de la
 * galería curada o {@code "personalizada"}; en ese caso {@code imagenPersonalizada} debe traer
 * la imagen elegida por el usuario como data URL ({@code data:image/...;base64,...}, comprimida
 * por el frontend, con tope de tamaño en el service). Nunca se aceptan URLs externas.
 * {@code intensidadCortina} es el alfa de la cortina de contraste, acotado a [0.35, 0.85] en el
 * service (RD-C3: el mínimo garantiza legibilidad sobre cualquier imagen y no es configurable).
 */
public record PreferenciasUiRequest(
        @NotBlank String fondo,
        @NotNull BigDecimal intensidadCortina,
        String imagenPersonalizada) {}
