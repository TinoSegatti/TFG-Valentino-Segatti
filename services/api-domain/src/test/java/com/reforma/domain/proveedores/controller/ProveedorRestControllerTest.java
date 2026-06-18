package com.reforma.domain.proveedores.controller;



import static org.mockito.ArgumentMatchers.any;

import static org.mockito.ArgumentMatchers.eq;

import static org.mockito.Mockito.doNothing;

import static org.mockito.Mockito.doThrow;

import static org.mockito.Mockito.verify;

import static org.mockito.Mockito.when;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;



import com.fasterxml.jackson.databind.ObjectMapper;

import com.reforma.domain.auth.jwt.JwtUserPrincipal;

import com.reforma.domain.auth.jwt.TokenJwtServicio;

import com.reforma.domain.common.domain.PlanSuscripcion;

import com.reforma.domain.common.domain.TipoUsuario;

import com.reforma.domain.proveedores.dto.ProveedorRequest;

import com.reforma.domain.proveedores.dto.ProveedorResponse;

import com.reforma.domain.proveedores.service.ProveedorService;

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



@WebMvcTest(controllers = ProveedorRestController.class)

@AutoConfigureMockMvc(addFilters = false)

class ProveedorRestControllerTest {



    private static final String ID_USUARIO = "u_demo";

    private static final String ID_GRANJA = "g_demo";

    private static final String BASE = "/api/proveedores/" + ID_GRANJA;

    private static final Long ID_PROV = 1L;



    @Autowired private MockMvc mockMvc;

    @Autowired private ObjectMapper objectMapper;



    @MockBean private ProveedorService proveedorService;

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



    private static ProveedorResponse sample(Long id, String codigo, String nombre) {

        Instant now = Instant.now();

        return new ProveedorResponse(

                id, ID_GRANJA, codigo, nombre,

                null, null, null, null, null, null,

                true, now, now);

    }



    @Test

    void listar_200_sinBuscar() throws Exception {

        when(proveedorService.listarPorGranja(ID_USUARIO, ID_GRANJA, null))

                .thenReturn(List.of(sample(ID_PROV, "P001", "Acopio del Sur SA")));



        mockMvc.perform(get(BASE))

                .andExpect(status().isOk())

                .andExpect(jsonPath("$.length()").value(1))

                .andExpect(jsonPath("$[0].codigoProveedor").value("P001"));

    }



    @Test

    void listar_200_conBuscar() throws Exception {

        when(proveedorService.listarPorGranja(ID_USUARIO, ID_GRANJA, "aco"))

                .thenReturn(List.of(sample(ID_PROV, "P001", "Acopio del Sur SA")));



        mockMvc.perform(get(BASE).param("buscar", "aco"))

                .andExpect(status().isOk())

                .andExpect(jsonPath("$[0].nombreProveedor").value("Acopio del Sur SA"));

    }



    @Test

    void crear_201_devuelveRecurso() throws Exception {

        var request = new ProveedorRequest(

                "P001", "Acopio del Sur SA", null, null, null, null, null, null);

        when(proveedorService.crear(eq(ID_USUARIO), eq(ID_GRANJA), any(ProveedorRequest.class)))

                .thenReturn(sample(ID_PROV, "P001", "Acopio del Sur SA"));



        mockMvc.perform(post(BASE)

                        .contentType(MediaType.APPLICATION_JSON)

                        .content(objectMapper.writeValueAsString(request)))

                .andExpect(status().isCreated())

                .andExpect(jsonPath("$.id").value(1))

                .andExpect(jsonPath("$.codigoProveedor").value("P001"));

    }



    @Test

    void crear_400_si_nombre_vacio() throws Exception {

        var request = new ProveedorRequest(

                "P001", "  ", null, null, null, null, null, null);



        mockMvc.perform(post(BASE)

                        .contentType(MediaType.APPLICATION_JSON)

                        .content(objectMapper.writeValueAsString(request)))

                .andExpect(status().isBadRequest());

    }



    @Test

    void crear_400_si_email_invalido() throws Exception {

        var request = new ProveedorRequest(

                "P001", "Acopio", null, "no-es-un-email", null, null, null, null);



        mockMvc.perform(post(BASE)

                        .contentType(MediaType.APPLICATION_JSON)

                        .content(objectMapper.writeValueAsString(request)))

                .andExpect(status().isBadRequest());

    }



    @Test

    void crear_409_si_service_lanza_conflict() throws Exception {

        var request = new ProveedorRequest(

                "P001", "Acopio", null, null, null, null, null, null);

        when(proveedorService.crear(eq(ID_USUARIO), eq(ID_GRANJA), any(ProveedorRequest.class)))

                .thenThrow(new ResponseStatusException(HttpStatus.CONFLICT, "Duplicado"));



        mockMvc.perform(post(BASE)

                        .contentType(MediaType.APPLICATION_JSON)

                        .content(objectMapper.writeValueAsString(request)))

                .andExpect(status().isConflict());

    }



    @Test

    void desactivar_204_devuelveNoContent() throws Exception {

        doNothing().when(proveedorService).desactivar(ID_USUARIO, ID_GRANJA, ID_PROV);



        mockMvc.perform(delete(BASE + "/1"))

                .andExpect(status().isNoContent());



        verify(proveedorService).desactivar(ID_USUARIO, ID_GRANJA, ID_PROV);

    }



    @Test

    void desactivar_404_si_service_lanza_notfound() throws Exception {

        doThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "No encontrado"))

                .when(proveedorService)

                .desactivar(ID_USUARIO, ID_GRANJA, 999L);



        mockMvc.perform(delete(BASE + "/999"))

                .andExpect(status().isNotFound());

    }

}

