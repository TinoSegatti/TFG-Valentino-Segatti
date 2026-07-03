package com.reforma.domain.archivos.support;

/**
 * Resultado de capturar un módulo: el payload a serializar en {@code t_archivo.datos}
 * y la cantidad de registros que contiene (para la cabecera del archivo).
 */
public record ArchivoSnapshot(Object datos, int totalRegistros) {}
