package com.reforma.domain.common.csv;

import java.util.List;

/**
 * Resultado de un import CSV.
 * <p>RF-MP-004: "Valida el formato antes de importar y muestra un resumen de filas importadas /
 * con error." Se devuelven ambos contadores y la lista detallada de errores.
 *
 * @param filasOk cantidad de filas importadas con éxito
 * @param filasError cantidad de filas que fallaron
 * @param errores detalle por error (línea, código si pudo leerse, mensaje)
 */
public record CsvImportResult(int filasOk, int filasError, List<CsvImportError> errores) {

    public static CsvImportResult vacio() {
        return new CsvImportResult(0, 0, List.of());
    }
}
