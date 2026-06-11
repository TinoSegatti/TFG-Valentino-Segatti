package com.reforma.domain.common.csv;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CsvWriterTest {

    @Test
    @DisplayName("escapa solo valores con coma, comillas o saltos de línea")
    void escapar_aplicaQuoteSoloCuandoEsNecesario() {
        assertThat(CsvWriter.escapar("simple")).isEqualTo("simple");
        assertThat(CsvWriter.escapar("")).isEqualTo("");
        assertThat(CsvWriter.escapar(null)).isEqualTo("");
        assertThat(CsvWriter.escapar("tiene,coma")).isEqualTo("\"tiene,coma\"");
        assertThat(CsvWriter.escapar("salto\nlinea")).isEqualTo("\"salto\nlinea\"");
        assertThat(CsvWriter.escapar("dijo \"hola\"")).isEqualTo("\"dijo \"\"hola\"\"\"");
    }

    @Test
    @DisplayName("escribir(): respeta orden de filas, separa por coma y termina con CRLF")
    void escribir_serializaMatriz() {
        String csv = CsvWriter.escribir(List.of(
                List.of("codigo", "nombre", "precio"),
                List.of("MAIZ", "Maíz, molido", "35.5"),
                List.of("SOJA", "Soja", "40.0")));

        assertThat(csv)
                .isEqualTo(
                        "codigo,nombre,precio\r\n"
                                + "MAIZ,\"Maíz, molido\",35.5\r\n"
                                + "SOJA,Soja,40.0\r\n");
    }
}
