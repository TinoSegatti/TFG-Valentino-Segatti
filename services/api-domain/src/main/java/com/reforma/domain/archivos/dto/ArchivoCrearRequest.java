package com.reforma.domain.archivos.dto;

import com.reforma.domain.archivos.domain.TipoModuloArchivo;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ArchivoCrearRequest(
        @NotNull TipoModuloArchivo tipo,
        @NotBlank @Size(max = 50) String codigoArchivo,
        @Size(max = 500) String descripcion) {}
