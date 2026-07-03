package com.reforma.domain.archivos.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.reforma.domain.archivos.domain.TipoModuloArchivo;
import com.reforma.domain.archivos.dto.ArchivoCrearRequest;
import com.reforma.domain.archivos.dto.ArchivoDetalleResponse;
import com.reforma.domain.archivos.dto.ArchivoResumenResponse;
import com.reforma.domain.archivos.service.ArchivoService;
import com.reforma.domain.auth.jwt.JwtUserPrincipal;
import com.reforma.domain.auth.jwt.TokenJwtServicio;
import com.reforma.domain.common.domain.PlanSuscripcion;
import com.reforma.domain.common.domain.TipoUsuario;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

@WebMvcTest(controllers = ArchivoRestController.class)
@AutoConfigureMockMvc(addFilters = false)
class ArchivoRestControllerTest {

    private static final String ID_USUARIO = "u_demo";
    private static final String EMAIL = "demo@reforma.local";
    private static final String ID_GRANJA = "g_demo";
    private static final String BASE = "/api/archivos/" + ID_GRANJA;

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @MockBean private ArchivoService archivoService;
    @MockBean private TokenJwtServicio tokenJwtServicio;

    @BeforeEach
    void autenticarUsuario() {
        var principal = new JwtUserPrincipal(
                ID_USUARIO, EMAIL, TipoUsuario.CLIENTE, PlanSuscripcion.BUSINESS, true,
                false, null, null);
        var auth = new UsernamePasswordAuthenticationToken(
                principal, null, principal.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    @AfterEach
    void limpiarContexto() {
        SecurityContextHolder.clearContext();
    }

    private static ArchivoResumenResponse resumen(Long id, TipoModuloArchivo tipo, String codigo) {
        return new ArchivoResumenResponse(
                id, tipo, codigo, "Cierre de mes", Instant.now(), EMAIL, 4);
    }

    @Test
    void crear_201_devuelveCabecera() throws Exception {
        var request = new ArchivoCrearRequest(
                TipoModuloArchivo.INVENTARIO, "INV-2026-07-02", "Cierre de mes");
        when(archivoService.crear(
                        eq(ID_USUARIO), eq(ID_USUARIO), eq(EMAIL), eq(ID_GRANJA),
                        any(ArchivoCrearRequest.class)))
                .thenReturn(resumen(1L, TipoModuloArchivo.INVENTARIO, "INV-2026-07-02"));

        mockMvc.perform(post(BASE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.tipo").value("INVENTARIO"))
                .andExpect(jsonPath("$.codigoArchivo").value("INV-2026-07-02"))
                .andExpect(jsonPath("$.totalRegistros").value(4));
    }

    @Test
    void crear_400_si_falta_tipo() throws Exception {
        mockMvc.perform(post(BASE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"codigoArchivo\":\"INV-01\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void crear_400_si_codigo_vacio() throws Exception {
        var request = new ArchivoCrearRequest(TipoModuloArchivo.COMPRAS, "  ", null);

        mockMvc.perform(post(BASE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void crear_409_si_service_lanza_conflict() throws Exception {
        var request = new ArchivoCrearRequest(TipoModuloArchivo.INVENTARIO, "INV-01", null);
        when(archivoService.crear(
                        eq(ID_USUARIO), eq(ID_USUARIO), eq(EMAIL), eq(ID_GRANJA),
                        any(ArchivoCrearRequest.class)))
                .thenThrow(new ResponseStatusException(HttpStatus.CONFLICT, "Duplicado"));

        mockMvc.perform(post(BASE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict());
    }

    @Test
    void listar_200_sinFiltro() throws Exception {
        when(archivoService.listar(eq(ID_USUARIO), eq(ID_GRANJA), isNull()))
                .thenReturn(List.of(
                        resumen(2L, TipoModuloArchivo.COMPRAS, "CMP-01"),
                        resumen(1L, TipoModuloArchivo.INVENTARIO, "INV-01")));

        mockMvc.perform(get(BASE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].codigoArchivo").value("CMP-01"));
    }

    @Test
    void listar_200_conFiltroDeTipo() throws Exception {
        when(archivoService.listar(ID_USUARIO, ID_GRANJA, TipoModuloArchivo.FORMULAS))
                .thenReturn(List.of(resumen(3L, TipoModuloArchivo.FORMULAS, "FOR-01")));

        mockMvc.perform(get(BASE).param("tipo", "FORMULAS"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].tipo").value("FORMULAS"));
    }

    @Test
    void listar_400_si_tipo_invalido() throws Exception {
        mockMvc.perform(get(BASE).param("tipo", "NOEXISTE"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void obtener_200_incluyeDatos() throws Exception {
        var datos = objectMapper.readTree("{\"inicializado\":true,\"items\":[]}");
        when(archivoService.obtener(ID_USUARIO, ID_GRANJA, 7L))
                .thenReturn(new ArchivoDetalleResponse(
                        7L, TipoModuloArchivo.INVENTARIO, "INV-07", null,
                        Instant.now(), EMAIL, 0, datos));

        mockMvc.perform(get(BASE + "/7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(7))
                .andExpect(jsonPath("$.datos.inicializado").value(true));
    }

    @Test
    void obtener_404_si_no_existe() throws Exception {
        when(archivoService.obtener(ID_USUARIO, ID_GRANJA, 99L))
                .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Archivo no encontrado"));

        mockMvc.perform(get(BASE + "/99"))
                .andExpect(status().isNotFound());
    }

    @Test
    void obtener_403_si_sin_acceso() throws Exception {
        when(archivoService.obtener(ID_USUARIO, ID_GRANJA, 7L))
                .thenThrow(new ResponseStatusException(HttpStatus.FORBIDDEN, "Sin acceso"));

        mockMvc.perform(get(BASE + "/7"))
                .andExpect(status().isForbidden());
    }
}
