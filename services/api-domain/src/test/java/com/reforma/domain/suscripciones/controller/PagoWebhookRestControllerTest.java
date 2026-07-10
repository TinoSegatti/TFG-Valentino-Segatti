package com.reforma.domain.suscripciones.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.reforma.domain.suscripciones.pasarela.MpWebhookFirmaValidator;
import com.reforma.domain.suscripciones.service.PagoWebhookService;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/** RD-P9: sin firma válida el webhook responde 401 y NO procesa; con firma, 200 y enruta. */
@ExtendWith(MockitoExtension.class)
class PagoWebhookRestControllerTest {

    @Mock private MpWebhookFirmaValidator firmaValidator;
    @Mock private PagoWebhookService webhookService;

    private MockMvc mvc;

    @BeforeEach
    void configurar() {
        mvc = MockMvcBuilders
                .standaloneSetup(new PagoWebhookRestController(firmaValidator, webhookService))
                .build();
    }

    @Test
    @DisplayName("firma inválida → 401 sin procesar nada")
    void firmaInvalida() throws Exception {
        when(firmaValidator.esValida(any(), any(), any(), any(Instant.class))).thenReturn(false);

        mvc.perform(post("/api/pagos/webhook")
                        .param("data.id", "123")
                        .param("type", "payment")
                        .header("x-signature", "ts=1,v1=basura"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(webhookService);
    }

    @Test
    @DisplayName("firma válida → 200 y enruta type + data.id de la query")
    void firmaValida() throws Exception {
        when(firmaValidator.esValida(eq("ts=1,v1=ok"), eq("req-1"), eq("123"), any(Instant.class)))
                .thenReturn(true);

        mvc.perform(post("/api/pagos/webhook")
                        .param("data.id", "123")
                        .param("type", "subscription_preapproval")
                        .header("x-signature", "ts=1,v1=ok")
                        .header("x-request-id", "req-1"))
                .andExpect(status().isOk());

        verify(webhookService).procesar("subscription_preapproval", "123");
    }

    @Test
    @DisplayName("sin query params toma type y data.id del body (fallback de enrutamiento)")
    void fallbackAlBody() throws Exception {
        when(firmaValidator.esValida(any(), any(), isNull(), any(Instant.class))).thenReturn(true);

        mvc.perform(post("/api/pagos/webhook")
                        .header("x-signature", "ts=1,v1=ok")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"subscription_preapproval\",\"data\":{\"id\":\"999\"}}"))
                .andExpect(status().isOk());

        verify(webhookService).procesar("subscription_preapproval", "999");
    }
}
