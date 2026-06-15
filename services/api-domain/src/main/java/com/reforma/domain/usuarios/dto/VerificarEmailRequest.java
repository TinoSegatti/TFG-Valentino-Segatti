package com.reforma.domain.usuarios.dto;

import jakarta.validation.constraints.NotBlank;

public record VerificarEmailRequest(@NotBlank String token) {}
