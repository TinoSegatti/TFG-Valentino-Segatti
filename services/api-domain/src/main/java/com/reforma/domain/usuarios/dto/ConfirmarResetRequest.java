package com.reforma.domain.usuarios.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ConfirmarResetRequest(
        @NotBlank String token,
        @NotBlank
                @Size(min = 8, message = "La contraseña debe tener al menos 8 caracteres")
                @Pattern(
                        regexp = "^(?=.*[A-Z])(?=.*\\d).+$",
                        message = "La contraseña debe incluir al menos una mayúscula y un número")
                String nuevaPassword) {}
