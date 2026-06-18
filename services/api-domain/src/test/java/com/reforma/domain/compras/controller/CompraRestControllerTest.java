package com.reforma.domain.compras.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.reforma.domain.auth.jwt.JwtUserPrincipal;
import com.reforma.domain.auth.jwt.TokenJwtServicio;
import com.reforma.domain.compras.domain.EstadoCompra;
import com.reforma.domain.compras.dto.CompraCabeceraRequest;
import com.reforma.domain.compras.dto.CompraCompletaResponse;
import com.reforma.domain.compras.dto.CompraDetalleLineRequest;
import com.reforma.domain.compras.dto.CompraResumenResponse;
import com.reforma.domain.compras.dto.GuardarCompraDetalleRequest;
import com.reforma.domain.compras.service.CompraService;
import com.reforma.domain.common.domain.PlanSuscripcion;
import com.reforma.domain.common.domain.TipoUsuario;
import java.time.LocalDate;
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

@WebMvcTest(controllers = CompraRestController.class)
@AutoConfigureMockMvc(addFilters = false)
class CompraRestControllerTest {

    private static final String ID_GRANJA = "g_demo";
    private static final String ID_COMPRA = "c_test01";

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @MockBean private CompraService compraService;
    @MockBean private TokenJwtServicio tokenJwtServicio;

    @BeforeEach
    void autenticar() {
        var principal = new JwtUserPrincipal(
                "u_demo", "demo@test.com", TipoUsuario.CLIENTE, PlanSuscripcion.DEMO, false,
                false, null, null);
        SecurityContextHolder.getContext()
                .setAuthentication(new UsernamePasswordAuthenticationToken(principal, null, List.of()));
    }

    @AfterEach
    void limpiar() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void listar_devuelveCompras() throws Exception {
        when(compraService.listar("u_demo", ID_GRANJA))
                .thenReturn(List.of(new CompraResumenResponse(
                        ID_COMPRA,
                        "F-001",
                        LocalDate.parse("2026-06-01"),
                        1500.0,
                        1L,
                        "PROV1",
                        "Proveedor Uno",
                        EstadoCompra.BORRADOR,
                        0,
                        0.0)));

        mockMvc.perform(get("/api/compras/{idGranja}", ID_GRANJA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].numeroFactura").value("F-001"));
    }

    @Test
    void crearCabecera_devuelve201() throws Exception {
        var request = new CompraCabeceraRequest(1L, "F-001", LocalDate.parse("2026-06-01"), 1500.0, null);
        when(compraService.crearCabecera(eq("u_demo"), eq(ID_GRANJA), any(CompraCabeceraRequest.class)))
                .thenReturn(compraCompletaStub());

        mockMvc.perform(post("/api/compras/{idGranja}", ID_GRANJA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.estado").value("BORRADOR"));

        verify(compraService).crearCabecera(eq("u_demo"), eq(ID_GRANJA), any(CompraCabeceraRequest.class));
    }

    @Test
    void guardarDetalle_devuelveCompraRegistrada() throws Exception {
        var body = new GuardarCompraDetalleRequest(
                List.of(new CompraDetalleLineRequest(10L, 10.0, 100.0, 1000.0)));
        when(compraService.guardarDetalle(
                        eq("u_demo"), eq(ID_GRANJA), eq(ID_COMPRA), any(GuardarCompraDetalleRequest.class)))
                .thenReturn(new CompraCompletaResponse(
                        ID_COMPRA,
                        "F-001",
                        LocalDate.parse("2026-06-01"),
                        1000.0,
                        null,
                        EstadoCompra.REGISTRADA,
                        1L,
                        "PROV1",
                        "Proveedor Uno",
                        1000.0,
                        List.of(),
                        false));

        mockMvc.perform(put("/api/compras/{idGranja}/{idCompra}/detalle", ID_GRANJA, ID_COMPRA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("REGISTRADA"));
    }

    @Test
    void guardarDetalle_aceptaPayloadConDecimales() throws Exception {
        var body = new GuardarCompraDetalleRequest(List.of(
                new CompraDetalleLineRequest(10L, 100.125, 300.333, 30_070.842),
                new CompraDetalleLineRequest(11L, 5_000.0, 3_000.0, 15_000_000.0)));
        when(compraService.guardarDetalle(
                        eq("u_demo"), eq(ID_GRANJA), eq(ID_COMPRA), any(GuardarCompraDetalleRequest.class)))
                .thenReturn(new CompraCompletaResponse(
                        ID_COMPRA,
                        "F-30M",
                        LocalDate.parse("2026-06-01"),
                        30_070_842.0 + 15_000_000.0,
                        null,
                        EstadoCompra.REGISTRADA,
                        1L,
                        "PROV1",
                        "Proveedor Uno",
                        30_070_842.0 + 15_000_000.0,
                        List.of(),
                        false));

        mockMvc.perform(put("/api/compras/{idGranja}/{idCompra}/detalle", ID_GRANJA, ID_COMPRA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk());

        verify(compraService)
                .guardarDetalle(eq("u_demo"), eq(ID_GRANJA), eq(ID_COMPRA), any(GuardarCompraDetalleRequest.class));
    }

    private static CompraCompletaResponse compraCompletaStub() {
        return new CompraCompletaResponse(
                ID_COMPRA,
                "F-001",
                LocalDate.parse("2026-06-01"),
                1500.0,
                null,
                EstadoCompra.BORRADOR,
                1L,
                "PROV1",
                "Proveedor Uno",
                0.0,
                List.of(),
                false);
    }
}
