package com.reforma.domain.common.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class TokenHasherTest {

    @Test
    @DisplayName("generarTokenPlano: tokens URL-safe, no vacíos y distintos entre sí")
    void generarTokenPlano() {
        var t1 = TokenHasher.generarTokenPlano();
        var t2 = TokenHasher.generarTokenPlano();

        assertThat(t1).isNotBlank().doesNotContain("+", "/", "=");
        assertThat(t1).isNotEqualTo(t2);
    }

    @Test
    @DisplayName("hash: determinista, 64 hex; entradas distintas → hashes distintos")
    void hash() {
        var token = "mi-token-de-prueba";

        var h1 = TokenHasher.hash(token);
        var h2 = TokenHasher.hash(token);

        assertThat(h1).isEqualTo(h2).hasSize(64).matches("[0-9a-f]{64}");
        assertThat(TokenHasher.hash("otro")).isNotEqualTo(h1);
    }
}
