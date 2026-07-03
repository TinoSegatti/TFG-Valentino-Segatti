package com.reforma.domain.reporte.controller;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.reforma.domain.auth.jwt.JwtUserPrincipal;
import com.reforma.domain.auth.jwt.TokenJwtServicio;
import com.reforma.domain.common.domain.PlanSuscripcion;
import com.reforma.domain.common.domain.TipoUsuario;
import com.reforma.domain.reporte.dto.InformeEstadoResponse;
import com.reforma.domain.reporte.service.InformeCsvService;
import com.reforma.domain.reporte.service.InformeEstadoService;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

@WebMvcTest(controllers = ReporteRestController.class)
@AutoConfigureMockMvc(addFilters = false)
class ReporteRestControllerTest {

    private static final String ID_USUARIO = "u_demo";
    private static final String ID_GRANJA = "g_demo";
    private static final String BASE = "/api/reportes/" + ID_GRANJA;
    private static final LocalDate DESDE = LocalDate.of(2026, 4, 1);
    private static final LocalDate HASTA = LocalDate.of(2026, 6, 30);

    @Autowired private MockMvc mockMvc;

    @MockBean private InformeEstadoService informeEstadoService;
    @MockBean private InformeCsvService informeCsvService;
    @MockBean private TokenJwtServicio tokenJwtServicio;

    @BeforeEach
    void autenticarUsuario() {
        var principal = new JwtUserPrincipal(
                ID_USUARIO, "demo@reforma.local", TipoUsuario.CLIENTE, PlanSuscripcion.BUSINESS, true,
                false, null, null);
        var auth = new UsernamePasswordAuthenticationToken(
                principal, null, principal.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    @AfterEach
    void limpiarContexto() {
        SecurityContextHolder.clearContext();
    }

    private static InformeEstadoResponse informeVacio() {
        return new InformeEstadoResponse(
                ID_GRANJA,
                DESDE,
                HASTA,
                new InformeEstadoResponse.ResumenGeneral(0, 0, 0, 0, 0, 0),
                new InformeEstadoResponse.SeccionProveedores(List.of()),
                new InformeEstadoResponse.SeccionInventario(List.of(), 0, 0),
                new InformeEstadoResponse.SeccionCompras(List.of(), List.of()),
                new InformeEstadoResponse.SeccionConsumos(List.of(), List.of()),
                new InformeEstadoResponse.SeccionIa(List.of(), false, List.of()));
    }

    @Test
    void informe_200_conPeriodo() throws Exception {
        when(informeEstadoService.generar(ID_USUARIO, ID_USUARIO, ID_GRANJA, DESDE, HASTA))
                .thenReturn(informeVacio());

        mockMvc.perform(get(BASE + "/informe")
                        .param("desde", "2026-04-01")
                        .param("hasta", "2026-06-30"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.idGranja").value(ID_GRANJA))
                .andExpect(jsonPath("$.resumen.compras").value(0))
                .andExpect(jsonPath("$.ia.prediccionesDisponibles").value(false));
    }

    @Test
    void informe_200_sinPeriodo() throws Exception {
        when(informeEstadoService.generar(ID_USUARIO, ID_USUARIO, ID_GRANJA, null, null))
                .thenReturn(informeVacio());

        mockMvc.perform(get(BASE + "/informe")).andExpect(status().isOk());
    }

    @Test
    void informe_400_si_periodo_invalido() throws Exception {
        when(informeEstadoService.generar(ID_USUARIO, ID_USUARIO, ID_GRANJA, HASTA, DESDE))
                .thenThrow(new ResponseStatusException(HttpStatus.BAD_REQUEST, "Período inválido"));

        mockMvc.perform(get(BASE + "/informe")
                        .param("desde", "2026-06-30")
                        .param("hasta", "2026-04-01"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void informe_403_si_sin_acceso() throws Exception {
        when(informeEstadoService.generar(ID_USUARIO, ID_USUARIO, ID_GRANJA, null, null))
                .thenThrow(new ResponseStatusException(HttpStatus.FORBIDDEN, "Sin acceso"));

        mockMvc.perform(get(BASE + "/informe")).andExpect(status().isForbidden());
    }

    @Test
    void informeCsv_200_descargaSeccion() throws Exception {
        InformeEstadoResponse informe = informeVacio();
        when(informeEstadoService.generar(ID_USUARIO, ID_USUARIO, ID_GRANJA, DESDE, HASTA))
                .thenReturn(informe);
        when(informeCsvService.exportar(informe, "proveedores"))
                .thenReturn("codigo,nombre\r\n");

        mockMvc.perform(get(BASE + "/informe/csv")
                        .param("seccion", "proveedores")
                        .param("desde", "2026-04-01")
                        .param("hasta", "2026-06-30"))
                .andExpect(status().isOk())
                .andExpect(content().contentType("text/csv;charset=UTF-8"))
                .andExpect(header().string(
                        "Content-Disposition",
                        "attachment; filename=\"informe_proveedores_" + ID_GRANJA
                                + "_2026-04-01_2026-06-30.csv\""));
    }

    @Test
    void informeCsv_400_si_seccion_desconocida() throws Exception {
        InformeEstadoResponse informe = informeVacio();
        when(informeEstadoService.generar(eq(ID_USUARIO), eq(ID_USUARIO), eq(ID_GRANJA), eq(null), eq(null)))
                .thenReturn(informe);
        when(informeCsvService.exportar(informe, "noexiste"))
                .thenThrow(new ResponseStatusException(HttpStatus.BAD_REQUEST, "Sección desconocida"));

        mockMvc.perform(get(BASE + "/informe/csv").param("seccion", "noexiste"))
                .andExpect(status().isBadRequest());
    }
}
