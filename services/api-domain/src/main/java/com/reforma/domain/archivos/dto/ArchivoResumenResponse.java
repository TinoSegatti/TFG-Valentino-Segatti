package com.reforma.domain.archivos.dto;

import com.reforma.domain.archivos.domain.TipoModuloArchivo;
import com.reforma.domain.archivos.entity.Archivo;
import java.time.Instant;

/** Cabecera de un archivo, sin el snapshot ({@code datos}): lo que ve el explorador. */
public record ArchivoResumenResponse(
        Long id,
        TipoModuloArchivo tipo,
        String codigoArchivo,
        String descripcion,
        Instant fechaCreacion,
        String creadoPorEmail,
        int totalRegistros) {

    public static ArchivoResumenResponse from(Archivo archivo) {
        return new ArchivoResumenResponse(
                archivo.getId(),
                archivo.getTipoModulo(),
                archivo.getCodigoArchivo(),
                archivo.getDescripcion(),
                archivo.getFechaCreacion(),
                archivo.getCreadoPorEmail(),
                archivo.getTotalRegistros());
    }
}
