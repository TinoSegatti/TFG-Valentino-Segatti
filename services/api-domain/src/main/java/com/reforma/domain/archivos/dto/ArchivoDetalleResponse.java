package com.reforma.domain.archivos.dto;

import com.fasterxml.jackson.databind.JsonNode;
import com.reforma.domain.archivos.domain.TipoModuloArchivo;
import com.reforma.domain.archivos.entity.Archivo;
import java.time.Instant;

/**
 * Archivo completo: cabecera + snapshot. {@code datos} es el JSON guardado al crear el
 * archivo (la respuesta del módulo en ese momento), que el frontend renderiza con los
 * mismos modelos de la pantalla original.
 */
public record ArchivoDetalleResponse(
        Long id,
        TipoModuloArchivo tipo,
        String codigoArchivo,
        String descripcion,
        Instant fechaCreacion,
        String creadoPorEmail,
        int totalRegistros,
        JsonNode datos) {

    public static ArchivoDetalleResponse from(Archivo archivo, JsonNode datos) {
        return new ArchivoDetalleResponse(
                archivo.getId(),
                archivo.getTipoModulo(),
                archivo.getCodigoArchivo(),
                archivo.getDescripcion(),
                archivo.getFechaCreacion(),
                archivo.getCreadoPorEmail(),
                archivo.getTotalRegistros(),
                datos);
    }
}
