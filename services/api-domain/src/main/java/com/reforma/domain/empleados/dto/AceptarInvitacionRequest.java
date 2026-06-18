package com.reforma.domain.empleados.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Aceptación de invitación: el empleado abre el enlace y fija su contraseña (Etapa 3).
 * Las reglas de contraseña replican {@code RegistroRequest} para mantener una única política.
 */
public record AceptarInvitacionRequest(
        @NotBlank String token,
        @NotBlank
                @Size(min = 8, message = "La contraseña debe tener al menos 8 caracteres")
                @Pattern(
                        regexp = "^(?=.*[A-Z])(?=.*\\d).+$",
                        message = "La contraseña debe incluir al menos una mayúscula y un número")
                String password) {}
