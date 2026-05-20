package com.reforma.domain.usuarios.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegistroRequest(
        @NotBlank @Email String email,
        @NotBlank
                @Size(min = 8, message = "La contraseña debe tener al menos 8 caracteres")
                @Pattern(
                        regexp = "^(?=.*[A-Z])(?=.*\\d).+$",
                        message = "La contraseña debe incluir al menos una mayúscula y un número")
                String password,
        @NotBlank @Size(max = 100) String nombreUsuario,
        @NotBlank @Size(max = 100) String apellidoUsuario,
        String codigoReferencia) {}
