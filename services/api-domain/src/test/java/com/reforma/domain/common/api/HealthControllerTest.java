package com.reforma.domain.common.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.reforma.domain.auth.jwt.TokenJwtServicio;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = HealthController.class)
@AutoConfigureMockMvc(addFilters = false)
class HealthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    // JwtAuthenticationFilter es un @Component que se autoincluye al cargar el contexto
    // del @WebMvcTest. Su dependencia TokenJwtServicio queda fuera del scope reducido,
    // así que la mockeamos para que el filter pueda construirse aunque no se invoque
    // (addFilters = false desactiva su ejecución, pero igual debe instanciarse).
    @MockBean
    private TokenJwtServicio tokenJwtServicio;

    @Test
    void healthEsPublico() throws Exception {
        mockMvc.perform(get("/api/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ok"))
                .andExpect(jsonPath("$.service").value("api-domain"));
    }
}
