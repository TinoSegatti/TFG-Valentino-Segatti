package com.reforma.domain.common.csv;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class CsvReaderTest {

    @Test
    @DisplayName("lee header y filas básicas; normaliza header a minúsculas")
    void leer_csvSimple() {
        String csv =
                "Codigo,Nombre,Precio\r\n"
                        + "MAIZ,Maíz molido,35.5\r\n"
                        + "SOJA,Soja,40.0\r\n";

        List<Map<String, String>> filas = CsvReader.leer(inputStream(csv));

        assertThat(filas).hasSize(2);
        assertThat(filas.get(0)).containsEntry("codigo", "MAIZ").containsEntry("nombre", "Maíz molido");
        assertThat(filas.get(1)).containsEntry("precio", "40.0");
    }

    @Test
    @DisplayName("respeta celdas entre comillas con comas y comillas escapadas")
    void leer_celdasEntreComillas() {
        String csv =
                "codigo,nombre\n"
                        + "ABC,\"Tiene, coma y dijo \"\"hola\"\"\"\n"
                        + "DEF,texto simple\n";

        List<Map<String, String>> filas = CsvReader.leer(inputStream(csv));

        assertThat(filas).hasSize(2);
        assertThat(filas.get(0).get("nombre")).isEqualTo("Tiene, coma y dijo \"hola\"");
    }

    @Test
    @DisplayName("descarta BOM UTF-8 inicial y filas vacías")
    void leer_descartaBomYFilasVacias() {
        String csv =
                "\uFEFFcodigo,nombre\n"
                        + "A,uno\n"
                        + "\n"
                        + "B,dos\n"
                        + "   \n";

        List<Map<String, String>> filas = CsvReader.leer(inputStream(csv));

        assertThat(filas).hasSize(2);
        assertThat(filas.get(0).get("codigo")).isEqualTo("A");
        assertThat(filas.get(1).get("codigo")).isEqualTo("B");
    }

    @Test
    @DisplayName("CSV vacío lanza 400")
    void leer_csvVacioFalla() {
        assertThatThrownBy(() -> CsvReader.leer(inputStream("")))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("vacío");
    }

    private static InputStream inputStream(String s) {
        return new ByteArrayInputStream(s.getBytes(StandardCharsets.UTF_8));
    }
}
