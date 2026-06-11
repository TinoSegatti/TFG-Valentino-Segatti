package com.reforma.domain.common.csv;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/**
 * Lector CSV minimalista (RFC 4180 simplificado).
 * <p>Diseño:
 * <ul>
 *   <li>Lee todo el archivo a memoria — uso solo para catálogos pequeños (máximo cientos de filas).</li>
 *   <li>Acepta BOM UTF-8 inicial; lo descarta.</li>
 *   <li>Soporta saltos de línea {@code \n} y {@code \r\n}, y celdas multilínea entrecomilladas.</li>
 *   <li>Devuelve cada fila como mapa header → valor (orden de header respetado).</li>
 * </ul>
 */
public final class CsvReader {

    private CsvReader() {}

    /**
     * Parsea un InputStream como CSV con header. Lanza 400 si está vacío.
     *
     * @return lista de filas (la primera no es header — viene ya excluida); cada fila es un mapa
     *     header → valor crudo (sin trim).
     */
    public static List<Map<String, String>> leer(InputStream in) {
        try (BufferedReader reader =
                new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String contenido = leerTodo(reader);
            return parsear(contenido);
        } catch (IOException e) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "No se pudo leer el archivo CSV: " + e.getMessage());
        }
    }

    private static String leerTodo(BufferedReader reader) throws IOException {
        StringBuilder sb = new StringBuilder();
        char[] buffer = new char[4096];
        int leidos;
        while ((leidos = reader.read(buffer)) != -1) {
            sb.append(buffer, 0, leidos);
        }
        if (sb.length() > 0 && sb.charAt(0) == '\uFEFF') {
            sb.deleteCharAt(0);
        }
        return sb.toString();
    }

    private static List<Map<String, String>> parsear(String contenido) {
        List<List<String>> matriz = tokenizar(contenido);
        if (matriz.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El CSV está vacío");
        }
        List<String> headerCrudo = matriz.get(0);
        List<String> header = new ArrayList<>(headerCrudo.size());
        for (String h : headerCrudo) header.add(h.trim().toLowerCase());

        List<Map<String, String>> filas = new ArrayList<>(matriz.size() - 1);
        for (int i = 1; i < matriz.size(); i++) {
            List<String> celdas = matriz.get(i);
            if (esFilaVacia(celdas)) continue;
            Map<String, String> fila = new LinkedHashMap<>();
            for (int j = 0; j < header.size(); j++) {
                String valor = j < celdas.size() ? celdas.get(j) : "";
                fila.put(header.get(j), valor);
            }
            filas.add(fila);
        }
        return filas;
    }

    private static boolean esFilaVacia(List<String> celdas) {
        if (celdas.isEmpty()) return true;
        for (String c : celdas) {
            if (c != null && !c.isBlank()) return false;
        }
        return true;
    }

    /** Tokeniza el contenido completo en filas → celdas. */
    private static List<List<String>> tokenizar(String contenido) {
        List<List<String>> filas = new ArrayList<>();
        List<String> filaActual = new ArrayList<>();
        StringBuilder celda = new StringBuilder();
        boolean dentroComillas = false;

        for (int i = 0; i < contenido.length(); i++) {
            char c = contenido.charAt(i);
            if (dentroComillas) {
                if (c == '"') {
                    if (i + 1 < contenido.length() && contenido.charAt(i + 1) == '"') {
                        celda.append('"');
                        i++;
                    } else {
                        dentroComillas = false;
                    }
                } else {
                    celda.append(c);
                }
                continue;
            }
            switch (c) {
                case '"' -> dentroComillas = true;
                case ',' -> {
                    filaActual.add(celda.toString());
                    celda.setLength(0);
                }
                case '\r' -> {
                    if (i + 1 < contenido.length() && contenido.charAt(i + 1) == '\n') i++;
                    filaActual.add(celda.toString());
                    filas.add(filaActual);
                    filaActual = new ArrayList<>();
                    celda.setLength(0);
                }
                case '\n' -> {
                    filaActual.add(celda.toString());
                    filas.add(filaActual);
                    filaActual = new ArrayList<>();
                    celda.setLength(0);
                }
                default -> celda.append(c);
            }
        }
        if (celda.length() > 0 || !filaActual.isEmpty()) {
            filaActual.add(celda.toString());
            filas.add(filaActual);
        }
        return filas;
    }
}
