package com.reforma.domain.common.csv;

/**
 * Un error puntual de import CSV.
 *
 * @param linea número de línea en el archivo original (1-indexado, contando el header como línea 1)
 * @param codigo código de negocio de la fila si pudo identificarse (o {@code null})
 * @param mensaje mensaje legible para el usuario
 */
public record CsvImportError(int linea, String codigo, String mensaje) {}
