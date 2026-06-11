package com.reforma.domain.common.csv;

import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/**
 * Helpers para leer campos de una fila CSV ya tokenizada (header → valor crudo).
 * Centraliza el tratamiento de valores vacíos, parsing numérico y validación de columnas
 * requeridas para no duplicar lógica en los services de catálogos.
 */
public final class CsvFields {

    private CsvFields() {}

    public static String requerido(Map<String, String> fila, String columna) {
        String v = fila.get(columna);
        if (v == null || v.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "Columna requerida '" + columna + "' vacía o ausente");
        }
        return v.trim();
    }

    /** Devuelve {@code null} si el valor es {@code null}/vacío/blank. Trimea en caso contrario. */
    public static String opcional(Map<String, String> fila, String columna) {
        String v = fila.get(columna);
        if (v == null) return null;
        String t = v.trim();
        return t.isEmpty() ? null : t;
    }

    /**
     * Parsea un decimal opcional. Acepta {@code 1.234,56} (formato es-AR con miles y coma decimal),
     * {@code 1234.56} (formato técnico) o {@code 1234,56} (coma decimal sin separador de miles).
     * Devuelve {@code null} si el campo está vacío.
     */
    public static Double decimalOpcional(Map<String, String> fila, String columna) {
        String v = opcional(fila, columna);
        if (v == null) return null;
        String normalizado;
        boolean tieneComa = v.indexOf(',') >= 0;
        boolean tienePunto = v.indexOf('.') >= 0;
        if (tieneComa && tienePunto) {
            // Asumir es-AR: puntos = miles, coma = decimal.
            normalizado = v.replace(".", "").replace(',', '.');
        } else if (tieneComa) {
            // Solo coma → tratar como separador decimal.
            normalizado = v.replace(',', '.');
        } else {
            normalizado = v;
        }
        try {
            return Double.parseDouble(normalizado);
        } catch (NumberFormatException e) {
            throw new ResponseStatusException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "Valor inválido en '" + columna + "': '" + v + "' no es un número");
        }
    }

    public static void validarColumnas(Map<String, String> fila, String... requeridas) {
        for (String columna : requeridas) {
            if (!fila.containsKey(columna)) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Falta la columna obligatoria '" + columna + "' en el CSV");
            }
        }
    }
}
