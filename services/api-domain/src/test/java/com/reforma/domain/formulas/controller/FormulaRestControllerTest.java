package com.reforma.domain.formulas.controller;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.reforma.domain.auth.jwt.JwtUserPrincipal;
import com.reforma.domain.auth.jwt.TokenJwtServicio;
import com.reforma.domain.common.domain.PlanSuscripcion;
import com.reforma.domain.common.domain.TipoUsuario;
import com.reforma.domain.formulas.dto.FormulaCabeceraRequest;
import com.reforma.domain.formulas.dto.FormulaCompletaResponse;
import com.reforma.domain.formulas.dto.FormulaResumenResponse;
import com.reforma.domain.formulas.service.FormulaService;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = FormulaRestController.class)
@AutoConfigureMockMvc(addFilters = false)
class FormulaRestControllerTest {

    private static final String ID_GRANJA = "g_demo";

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @MockBean private FormulaService formulaService;
    @MockBean private TokenJwtServicio tokenJwtServicio;

    @BeforeEach
    void autenticar() {
        var principal = new JwtUserPrincipal(
                "u_demo", "demo@test.com", TipoUsuario.CLIENTE, PlanSuscripcion.DEMO, false);
        SecurityContextHolder.getContext()
                .setAuthentication(new UsernamePasswordAuthenticationToken(principal, null, List.of()));
    }

    @AfterEach
    void limpiar() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void listar_devuelveFormulas() throws Exception {
        when(formulaService.listar("u_demo", ID_GRANJA))
                .thenReturn(List.of(new FormulaResumenResponse(
                        "f1", "F-01", "Cerda gestante", 1L, "CERDA", "Cerda", 100_000.0, true)));

        mockMvc.perform(get("/api/formulas/{idGranja}", ID_GRANJA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].codigoFormula").value("F-01"));
    }

    @Test
    void crearCabecera_devuelve201() throws Exception {
        when(formulaService.crearCabecera(eq("u_demo"), eq(ID_GRANJA), org.mockito.ArgumentMatchers.any()))
                .thenReturn(new FormulaCompletaResponse(
                        "f1", "F-01", "Test", 1L, "CERDA", "Cerda", 1000, 0, 0, 1000, false, List.of()));

        mockMvc.perform(post("/api/formulas/{idGranja}", ID_GRANJA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new FormulaCabeceraRequest("F-01", "Test", 1L))))
                .andExpect(status().isCreated());

        verify(formulaService).crearCabecera(eq("u_demo"), eq(ID_GRANJA), org.mockito.ArgumentMatchers.any());
    }
}
