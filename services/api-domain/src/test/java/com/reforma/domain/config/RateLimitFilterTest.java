package com.reforma.domain.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class RateLimitFilterTest {

    // findAndRegisterModules: ApiError.timestamp es Instant y necesita el módulo jsr310.
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    private final RateLimitFilter filtro = new RateLimitFilter(true, objectMapper);

    private MockHttpServletRequest post(String path, String ip) {
        var request = new MockHttpServletRequest("POST", path);
        request.setRequestURI(path);
        request.setRemoteAddr(ip);
        return request;
    }

    private int ejecutar(MockHttpServletRequest request) throws Exception {
        var response = new MockHttpServletResponse();
        filtro.doFilter(request, response, new MockFilterChain());
        return response.getStatus();
    }

    @Test
    @DisplayName("dentro del límite pasa; al exceder responde 429 con JSON")
    void limitePorIpYPath() throws Exception {
        for (int i = 0; i < 3; i++) {
            assertThat(ejecutar(post("/api/usuarios/solicitar-reset", "1.1.1.1"))).isEqualTo(200);
        }
        var response = new MockHttpServletResponse();
        filtro.doFilter(post("/api/usuarios/solicitar-reset", "1.1.1.1"), response, new MockFilterChain());
        assertThat(response.getStatus()).isEqualTo(429);
        assertThat(response.getContentType()).contains("application/json");
        assertThat(response.getContentAsString()).contains("Demasiados intentos");
    }

    @Test
    @DisplayName("el contador es por IP: otra IP no se ve afectada")
    void otraIpNoAfectada() throws Exception {
        for (int i = 0; i < 4; i++) {
            ejecutar(post("/api/usuarios/solicitar-reset", "2.2.2.2"));
        }
        assertThat(ejecutar(post("/api/usuarios/solicitar-reset", "3.3.3.3"))).isEqualTo(200);
    }

    @Test
    @DisplayName("paths no listados y métodos GET no se filtran")
    void soloPostsSensibles() throws Exception {
        for (int i = 0; i < 50; i++) {
            assertThat(ejecutar(post("/api/granjas", "4.4.4.4"))).isEqualTo(200);
        }
        var get = new MockHttpServletRequest("GET", "/api/usuarios/login");
        get.setRequestURI("/api/usuarios/login");
        get.setRemoteAddr("4.4.4.4");
        var response = new MockHttpServletResponse();
        filtro.doFilter(get, response, new MockFilterChain());
        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    @DisplayName("con X-Forwarded-For usa la primera IP de la lista")
    void usaXForwardedFor() throws Exception {
        for (int i = 0; i < 3; i++) {
            var request = post("/api/usuarios/solicitar-reset", "10.0.0.1");
            request.addHeader("X-Forwarded-For", "5.5.5.5, 10.0.0.1");
            ejecutar(request);
        }
        // Misma IP de proxy pero otro cliente original: no comparte contador.
        var otro = post("/api/usuarios/solicitar-reset", "10.0.0.1");
        otro.addHeader("X-Forwarded-For", "6.6.6.6, 10.0.0.1");
        assertThat(ejecutar(otro)).isEqualTo(200);
    }

    @Test
    @DisplayName("deshabilitado por configuración: no limita nada")
    void deshabilitado() throws Exception {
        var apagado = new RateLimitFilter(false, objectMapper);
        for (int i = 0; i < 20; i++) {
            var response = new MockHttpServletResponse();
            apagado.doFilter(post("/api/usuarios/login", "7.7.7.7"), response, new MockFilterChain());
            assertThat(response.getStatus()).isEqualTo(200);
        }
    }
}
