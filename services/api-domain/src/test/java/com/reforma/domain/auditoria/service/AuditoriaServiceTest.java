package com.reforma.domain.auditoria.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.reforma.domain.auditoria.domain.AccionAuditoria;
import com.reforma.domain.auditoria.dto.AuditoriaEvento;
import com.reforma.domain.auditoria.entity.Auditoria;
import com.reforma.domain.auditoria.repository.AuditoriaRepository;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AuditoriaServiceTest {

    @Mock private AuditoriaRepository auditoriaRepository;
    @Captor private ArgumentCaptor<Auditoria> auditoriaCaptor;

    // ObjectMapper real: la serialización a JSON es parte de lo que se verifica.
    private AuditoriaService servicio() {
        return new AuditoriaService(auditoriaRepository, new ObjectMapper());
    }

    @Test
    @DisplayName("registrar: persiste la fila con los campos del evento y serializa datos a JSON")
    void registrar_persisteFila() {
        var evento = AuditoriaEvento.builder()
                .idUsuario("u_1")
                .tablaOrigen("t_usuarios")
                .idRegistro("u_1")
                .accion(AccionAuditoria.REGISTRO)
                .descripcion("Registro de usuario dueño")
                .datosNuevos(Map.of("email", "a@b.com", "tipoUsuario", "CLIENTE"))
                .build();

        servicio().registrar(evento);

        verify(auditoriaRepository).save(auditoriaCaptor.capture());
        Auditoria guardada = auditoriaCaptor.getValue();
        assertThat(guardada.getId()).isNotBlank();
        assertThat(guardada.getIdUsuario()).isEqualTo("u_1");
        assertThat(guardada.getTablaOrigen()).isEqualTo("t_usuarios");
        assertThat(guardada.getIdRegistro()).isEqualTo("u_1");
        assertThat(guardada.getAccion()).isEqualTo(AccionAuditoria.REGISTRO);
        assertThat(guardada.getDescripcion()).isEqualTo("Registro de usuario dueño");
        assertThat(guardada.getFechaOperacion()).isNotNull();
        assertThat(guardada.getDatosNuevosJson())
                .contains("\"email\":\"a@b.com\"")
                .contains("\"tipoUsuario\":\"CLIENTE\"");
        assertThat(guardada.getDatosAnterioresJson()).isNull();
        // Fuera de un contexto web, IP y User-Agent quedan nulos sin romper la operación.
        assertThat(guardada.getIpAddress()).isNull();
        assertThat(guardada.getUserAgent()).isNull();
    }

    @Test
    @DisplayName("registrar: sin datos anteriores/nuevos los JSON quedan nulos")
    void registrar_sinDatosJsonNulos() {
        var evento = AuditoriaEvento.builder()
                .idUsuario("u_2")
                .tablaOrigen("t_usuarios")
                .idRegistro("u_2")
                .accion(AccionAuditoria.LOGIN)
                .descripcion("Inicio de sesión")
                .build();

        servicio().registrarIndependiente(evento);

        verify(auditoriaRepository).save(auditoriaCaptor.capture());
        Auditoria guardada = auditoriaCaptor.getValue();
        assertThat(guardada.getAccion()).isEqualTo(AccionAuditoria.LOGIN);
        assertThat(guardada.getDatosNuevosJson()).isNull();
        assertThat(guardada.getDatosAnterioresJson()).isNull();
    }
}
