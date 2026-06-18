package com.reforma.domain.materiasprimas.controller;



import static org.mockito.ArgumentMatchers.any;

import static org.mockito.ArgumentMatchers.eq;

import static org.mockito.Mockito.doNothing;

import static org.mockito.Mockito.doThrow;

import static org.mockito.Mockito.verify;

import static org.mockito.Mockito.when;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;



import com.fasterxml.jackson.databind.ObjectMapper;

import com.reforma.domain.auth.jwt.JwtUserPrincipal;

import com.reforma.domain.auth.jwt.TokenJwtServicio;

import com.reforma.domain.common.domain.PlanSuscripcion;

import com.reforma.domain.common.domain.TipoUsuario;

import com.reforma.domain.materiasprimas.dto.MateriaPrimaRequest;

import com.reforma.domain.materiasprimas.dto.MateriaPrimaResponse;

import com.reforma.domain.materiasprimas.service.MateriaPrimaService;

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



@WebMvcTest(controllers = MateriaPrimaRestController.class)

@AutoConfigureMockMvc(addFilters = false)

class MateriaPrimaRestControllerTest {



    private static final String ID_USUARIO = "u_demo";

    private static final String ID_GRANJA = "g_demo";

    private static final String BASE = "/api/materias-primas/" + ID_GRANJA;

    private static final Long ID_MP = 1L;



    @Autowired private MockMvc mockMvc;

    @Autowired private ObjectMapper objectMapper;



    @MockBean private MateriaPrimaService materiaPrimaService;

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



    private static MateriaPrimaResponse sample(Long id, String codigo, String nombre) {

        Instant now = Instant.now();

        return new MateriaPrimaResponse(id, ID_GRANJA, codigo, nombre, 35.50, true, now, now);

    }



    @Test

    void listar_200_devuelveLista() throws Exception {

        when(materiaPrimaService.listarPorGranja(ID_USUARIO, ID_GRANJA))

                .thenReturn(List.of(sample(ID_MP, "MAIZ", "Maíz molido")));



        mockMvc.perform(get(BASE))

                .andExpect(status().isOk())

                .andExpect(jsonPath("$.length()").value(1))

                .andExpect(jsonPath("$[0].codigoMateriaPrima").value("MAIZ"));

    }



    @Test

    void crear_201_devuelveRecurso() throws Exception {

        var request = new MateriaPrimaRequest("MAIZ", "Maíz molido", 35.50);

        when(materiaPrimaService.crear(eq(ID_USUARIO), eq(ID_GRANJA), any(MateriaPrimaRequest.class)))

                .thenReturn(sample(ID_MP, "MAIZ", "Maíz molido"));



        mockMvc.perform(post(BASE)

                        .contentType(MediaType.APPLICATION_JSON)

                        .content(objectMapper.writeValueAsString(request)))

                .andExpect(status().isCreated())

                .andExpect(jsonPath("$.id").value(1))

                .andExpect(jsonPath("$.codigoMateriaPrima").value("MAIZ"));

    }



    @Test

    void crear_400_si_codigo_vacio() throws Exception {

        var request = new MateriaPrimaRequest("", "Maíz molido", 35.50);



        mockMvc.perform(post(BASE)

                        .contentType(MediaType.APPLICATION_JSON)

                        .content(objectMapper.writeValueAsString(request)))

                .andExpect(status().isBadRequest());

    }



    @Test

    void crear_409_si_service_lanza_conflict() throws Exception {

        var request = new MateriaPrimaRequest("MAIZ", "Maíz molido", 35.50);

        when(materiaPrimaService.crear(eq(ID_USUARIO), eq(ID_GRANJA), any(MateriaPrimaRequest.class)))

                .thenThrow(new ResponseStatusException(HttpStatus.CONFLICT, "Duplicado"));



        mockMvc.perform(post(BASE)

                        .contentType(MediaType.APPLICATION_JSON)

                        .content(objectMapper.writeValueAsString(request)))

                .andExpect(status().isConflict());

    }



    @Test

    void crear_403_si_service_lanza_forbidden() throws Exception {

        var request = new MateriaPrimaRequest("MAIZ", "Maíz molido", 35.50);

        when(materiaPrimaService.crear(eq(ID_USUARIO), eq(ID_GRANJA), any(MateriaPrimaRequest.class)))

                .thenThrow(new ResponseStatusException(HttpStatus.FORBIDDEN, "Sin acceso"));



        mockMvc.perform(post(BASE)

                        .contentType(MediaType.APPLICATION_JSON)

                        .content(objectMapper.writeValueAsString(request)))

                .andExpect(status().isForbidden());

    }



    @Test

    void actualizar_200_devuelveActualizado() throws Exception {

        var request = new MateriaPrimaRequest("MAIZ", "Maíz molido", 42.0);

        when(materiaPrimaService.actualizar(

                        eq(ID_USUARIO), eq(ID_GRANJA), eq(ID_MP), any(MateriaPrimaRequest.class)))

                .thenReturn(new MateriaPrimaResponse(

                        ID_MP, ID_GRANJA, "MAIZ", "Maíz molido", 42.0,

                        true, Instant.now(), Instant.now()));



        mockMvc.perform(put(BASE + "/1")

                        .contentType(MediaType.APPLICATION_JSON)

                        .content(objectMapper.writeValueAsString(request)))

                .andExpect(status().isOk())

                .andExpect(jsonPath("$.precioPorKilo").value(42.0));

    }



    @Test

    void actualizar_404_si_service_lanza_notfound() throws Exception {

        var request = new MateriaPrimaRequest("MAIZ", "Maíz molido", 42.0);

        when(materiaPrimaService.actualizar(

                        eq(ID_USUARIO), eq(ID_GRANJA), eq(999L), any(MateriaPrimaRequest.class)))

                .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "No encontrada"));



        mockMvc.perform(put(BASE + "/999")

                        .contentType(MediaType.APPLICATION_JSON)

                        .content(objectMapper.writeValueAsString(request)))

                .andExpect(status().isNotFound());

    }



    @Test

    void desactivar_204_devuelveNoContent() throws Exception {

        doNothing().when(materiaPrimaService).desactivar(ID_USUARIO, ID_GRANJA, ID_MP);



        mockMvc.perform(delete(BASE + "/1"))

                .andExpect(status().isNoContent());



        verify(materiaPrimaService).desactivar(ID_USUARIO, ID_GRANJA, ID_MP);

    }



    @Test

    void desactivar_404_si_service_lanza_notfound() throws Exception {

        doThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "No encontrada"))

                .when(materiaPrimaService)

                .desactivar(ID_USUARIO, ID_GRANJA, 999L);



        mockMvc.perform(delete(BASE + "/999"))

                .andExpect(status().isNotFound());

    }

}

