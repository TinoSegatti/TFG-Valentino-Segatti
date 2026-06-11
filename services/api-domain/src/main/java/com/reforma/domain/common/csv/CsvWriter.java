package com.reforma.domain.common.csv;

import java.util.List;

/**
 * Escritor CSV minimalista (RFC 4180 simplificado).
 * <ul>
 *   <li>Delimitador: {@code ,}</li>
 *   <li>Quote: {@code "} solo si la celda contiene coma, comillas o saltos de línea</li>
 *   <li>Escape: {@code ""} (doble comilla doble) dentro de celdas entrecomilladas</li>
 *   <li>Línea: {@code \r\n} (compatible con Excel)</li>
 * </ul>
 */
public final class CsvWriter {

    public static final String LINE_SEPARATOR = "\r\n";

    private CsvWriter() {}

    /** Serializa una matriz de filas (incluyendo header como primera fila) a String CSV. */
    public static String escribir(List<List<String>> filas) {
        StringBuilder sb = new StringBuilder();
        for (List<String> fila : filas) {
            for (int i = 0; i < fila.size(); i++) {
                if (i > 0) sb.append(',');
                sb.append(escapar(fila.get(i)));
            }
            sb.append(LINE_SEPARATOR);
        }
        return sb.toString();
    }

    /** Escapa un valor según RFC 4180. {@code null} se serializa como vacío. */
    public static String escapar(String valor) {
        if (valor == null || valor.isEmpty()) return "";
        boolean necesitaQuote =
                valor.indexOf(',') >= 0
                        || valor.indexOf('"') >= 0
                        || valor.indexOf('\n') >= 0
                        || valor.indexOf('\r') >= 0;
        if (!necesitaQuote) return valor;
        return '"' + valor.replace("\"", "\"\"") + '"';
    }
}
