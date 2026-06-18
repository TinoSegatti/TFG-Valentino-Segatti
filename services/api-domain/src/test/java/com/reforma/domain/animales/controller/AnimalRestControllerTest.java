package com.reforma.domain.animales.controller;



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

import com.reforma.domain.animales.dto.AnimalRequest;

import com.reforma.domain.animales.dto.AnimalResponse;

import com.reforma.domain.animales.service.AnimalService;

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



@WebMvcTest(controllers = AnimalRestController.class)

@AutoConfigureMockMvc(addFilters = false)

class AnimalRestControllerTest {



    private static final String ID_USUARIO = "u_demo";

    private static final String ID_GRANJA = "g_demo";

    private static final String BASE = "/api/animales/" + ID_GRANJA;

    private static final Long ID_ANIMAL = 1L;



    @Autowired private MockMvc mockMvc;

    @Autowired private ObjectMapper objectMapper;



    @MockBean private AnimalService animalService;

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



    private static AnimalResponse sample(Long id, String codigo, String descripcion) {

        Instant now = Instant.now();

        return new AnimalResponse(

                id, ID_GRANJA, codigo, descripcion, null, null, true, now, now);

    }



    @Test

    void listar_200_sinBuscar() throws Exception {

        when(animalService.listarPorGranja(ID_USUARIO, ID_GRANJA, null))

                .thenReturn(List.of(sample(ID_ANIMAL, "CAT01", "Cerda gestante")));



        mockMvc.perform(get(BASE))

                .andExpect(status().isOk())

                .andExpect(jsonPath("$.length()").value(1))

                .andExpect(jsonPath("$[0].codigoAnimal").value("CAT01"));

    }



    @Test

    void listar_200_conBuscar() throws Exception {

        when(animalService.listarPorGranja(ID_USUARIO, ID_GRANJA, "cerda"))

                .thenReturn(List.of(sample(ID_ANIMAL, "CAT01", "Cerda gestante")));



        mockMvc.perform(get(BASE).param("buscar", "cerda"))

                .andExpect(status().isOk())

                .andExpect(jsonPath("$[0].descripcionAnimal").value("Cerda gestante"));

    }



    @Test

    void crear_201_devuelveRecurso() throws Exception {

        var request = new AnimalRequest("CAT01", "Cerda gestante", null, null);

        when(animalService.crear(eq(ID_USUARIO), eq(ID_GRANJA), any(AnimalRequest.class)))

                .thenReturn(sample(ID_ANIMAL, "CAT01", "Cerda gestante"));



        mockMvc.perform(post(BASE)

                        .contentType(MediaType.APPLICATION_JSON)

                        .content(objectMapper.writeValueAsString(request)))

                .andExpect(status().isCreated())

                .andExpect(jsonPath("$.id").value(1))

                .andExpect(jsonPath("$.codigoAnimal").value("CAT01"));

    }



    @Test

    void crear_400_si_codigo_vacio() throws Exception {

        var request = new AnimalRequest("  ", "Cerda gestante", null, null);



        mockMvc.perform(post(BASE)

                        .contentType(MediaType.APPLICATION_JSON)

                        .content(objectMapper.writeValueAsString(request)))

                .andExpect(status().isBadRequest());

    }



    @Test

    void crear_400_si_descripcion_vacia() throws Exception {

        var request = new AnimalRequest("CAT01", "  ", null, null);



        mockMvc.perform(post(BASE)

                        .contentType(MediaType.APPLICATION_JSON)

                        .content(objectMapper.writeValueAsString(request)))

                .andExpect(status().isBadRequest());

    }



    @Test

    void crear_409_si_service_lanza_conflict() throws Exception {

        var request = new AnimalRequest("CAT01", "Cerda gestante", null, null);

        when(animalService.crear(eq(ID_USUARIO), eq(ID_GRANJA), any(AnimalRequest.class)))

                .thenThrow(new ResponseStatusException(HttpStatus.CONFLICT, "Duplicado"));



        mockMvc.perform(post(BASE)

                        .contentType(MediaType.APPLICATION_JSON)

                        .content(objectMapper.writeValueAsString(request)))

                .andExpect(status().isConflict());

    }



    @Test

    void desactivar_204_devuelveNoContent() throws Exception {

        doNothing().when(animalService).desactivar(ID_USUARIO, ID_GRANJA, ID_ANIMAL);



        mockMvc.perform(delete(BASE + "/1"))

                .andExpect(status().isNoContent());



        verify(animalService).desactivar(ID_USUARIO, ID_GRANJA, ID_ANIMAL);

    }



    @Test

    void desactivar_404_si_service_lanza_notfound() throws Exception {

        doThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "No encontrado"))

                .when(animalService)

                .desactivar(ID_USUARIO, ID_GRANJA, 999L);



        mockMvc.perform(delete(BASE + "/999"))

                .andExpect(status().isNotFound());

    }

}

