package com.reforma.domain.usuarios.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record SolicitarResetRequest(@NotBlank @Email String email) {}
