package com.reforma.domain.animales.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Payload para alta/edición de Animal (RF-ANI-001).
 * Obligatorios: código y descripción. Categoría y observaciones son opcionales.
 */
public record AnimalRequest(
        @NotBlank @Size(max = 50) String codigoAnimal,
        @NotBlank @Size(max = 200) String descripcionAnimal,
        @Size(max = 100) String categoriaAnimal,
        @Size(max = 5000) String observaciones) {}
