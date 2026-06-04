package com.reforma.domain.common.api;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.server.ResponseStatusException;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void handleResponseStatus_devuelveApiErrorConMessage() {
        var request = new MockHttpServletRequest();
        request.setRequestURI("/api/formulas/g_demo");

        var response = handler.handleResponseStatus(
                new ResponseStatusException(
                        HttpStatus.CONFLICT, "Ya existe una formula activa con codigo F-01"),
                request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().message()).contains("Ya existe una formula activa");
        assertThat(response.getBody().status()).isEqualTo(409);
    }
}
