package com.reforma.domain.common.csv;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class CsvFieldsTest {

    @Test
    @DisplayName("requerido: trimea y rechaza vacíos/blancos con 422")
    void requerido() {
        Map<String, String> fila = Map.of("codigo", "  MAIZ  ", "vacio", "   ");
        assertThat(CsvFields.requerido(fila, "codigo")).isEqualTo("MAIZ");
        assertThatThrownBy(() -> CsvFields.requerido(fila, "vacio"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("vacio");
    }

    @Test
    @DisplayName("opcional: trim + null si vacío")
    void opcional() {
        Map<String, String> fila = new java.util.HashMap<>();
        fila.put("a", "  hola  ");
        fila.put("b", "");
        fila.put("c", "   ");
        fila.put("d", null);
        assertThat(CsvFields.opcional(fila, "a")).isEqualTo("hola");
        assertThat(CsvFields.opcional(fila, "b")).isNull();
        assertThat(CsvFields.opcional(fila, "c")).isNull();
        assertThat(CsvFields.opcional(fila, "d")).isNull();
        assertThat(CsvFields.opcional(fila, "missing")).isNull();
    }

    @Test
    @DisplayName("decimalOpcional: acepta punto, coma y formato es-AR con miles")
    void decimalOpcional() {
        Map<String, String> fila = Map.of(
                "tecnico", "1234.56",
                "esAr", "1.234,56",
                "comaSola", "1234,56",
                "entero", "42",
                "vacio", "");
        assertThat(CsvFields.decimalOpcional(fila, "tecnico")).isEqualTo(1234.56);
        assertThat(CsvFields.decimalOpcional(fila, "esAr")).isEqualTo(1234.56);
        assertThat(CsvFields.decimalOpcional(fila, "comaSola")).isEqualTo(1234.56);
        assertThat(CsvFields.decimalOpcional(fila, "entero")).isEqualTo(42.0);
        assertThat(CsvFields.decimalOpcional(fila, "vacio")).isNull();
    }

    @Test
    @DisplayName("decimalOpcional: valor no numérico → 422")
    void decimalOpcional_invalido() {
        Map<String, String> fila = Map.of("monto", "no-es-numero");
        assertThatThrownBy(() -> CsvFields.decimalOpcional(fila, "monto"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("monto");
    }

    @Test
    @DisplayName("validarColumnas: falta una requerida → 400")
    void validarColumnas() {
        Map<String, String> fila = Map.of("codigo", "X");
        assertThatThrownBy(() -> CsvFields.validarColumnas(fila, "codigo", "nombre"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("nombre");
    }
}
