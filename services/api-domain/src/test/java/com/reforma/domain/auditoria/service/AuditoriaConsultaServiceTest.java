package com.reforma.domain.auditoria.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;

import com.reforma.domain.auditoria.domain.AccionAuditoria;
import com.reforma.domain.auditoria.entity.Auditoria;
import com.reforma.domain.auditoria.repository.AuditoriaRepository;
import com.reforma.domain.common.domain.PlanSuscripcion;
import com.reforma.domain.common.domain.RolEmpleado;
import com.reforma.domain.common.domain.TipoUsuario;
import com.reforma.domain.usuarios.entity.Usuario;
import com.reforma.domain.usuarios.repository.UsuarioRepository;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class AuditoriaConsultaServiceTest {

    private static final String ID_DUENO = "u_dueno";

    @Mock private AuditoriaRepository auditoriaRepository;
    @Mock private UsuarioRepository usuarioRepository;

    @InjectMocks private AuditoriaConsultaService servicio;

    @Captor private ArgumentCaptor<Collection<String>> idsCaptor;

    @Test
    @DisplayName("consultar: el dueño ve su propia actividad y la de sus empleados; enriquece el actor")
    void consultar_scopingYEnriquecimiento() {
        var dueno = dueno();
        var emp = empleado("e1", RolEmpleado.EDITOR, dueno, true);
        when(usuarioRepository.findById(ID_DUENO)).thenReturn(Optional.of(dueno));
        when(usuarioRepository.findByUsuarioDuenoIdOrderByFechaVinculacionDesc(ID_DUENO))
                .thenReturn(List.of(emp));

        var fila = Auditoria.builder()
                .id("a1")
                .idUsuario("e1")
                .accion(AccionAuditoria.LOGIN)
                .fechaOperacion(Instant.now())
                .build();
        when(auditoriaRepository.buscar(
                        idsCaptor.capture(),
                        eq(false), isNull(),
                        eq(false), isNull(),
                        eq(false), isNull(),
                        eq(false), isNull(),
                        eq(false), isNull(),
                        any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(fila)));
        when(usuarioRepository.findAllById(List.of("e1"))).thenReturn(List.of(emp));

        var pagina = servicio.consultar(ID_DUENO, null, null, null, null, null, 0, 20);

        // El conjunto de actores incluye al dueño y a su empleado.
        assertThat(idsCaptor.getValue()).containsExactlyInAnyOrder(ID_DUENO, "e1");
        assertThat(pagina.contenido()).hasSize(1);
        assertThat(pagina.contenido().get(0).actorEmail()).isEqualTo("e1@reforma.com");
        assertThat(pagina.contenido().get(0).accion()).isEqualTo(AccionAuditoria.LOGIN);
        assertThat(pagina.totalElementos()).isEqualTo(1);
    }

    @Test
    @DisplayName("consultar: un jefe ADMIN resuelve al dueño y ve la actividad del tenant")
    void consultar_jefeResuelveDueno() {
        var dueno = dueno();
        var jefe = empleado("u_jefe", RolEmpleado.ADMIN, dueno, true);
        when(usuarioRepository.findById("u_jefe")).thenReturn(Optional.of(jefe));
        when(usuarioRepository.findByUsuarioDuenoIdOrderByFechaVinculacionDesc(ID_DUENO))
                .thenReturn(List.of(jefe));
        when(auditoriaRepository.buscar(
                        idsCaptor.capture(),
                        eq(false), isNull(),
                        eq(true), eq("e1"),
                        eq(true), eq(AccionAuditoria.RESET_PASSWORD),
                        eq(false), isNull(),
                        eq(false), isNull(),
                        any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        servicio.consultar("u_jefe", null, "e1", AccionAuditoria.RESET_PASSWORD, null, null, 0, 20);

        assertThat(idsCaptor.getValue()).contains(ID_DUENO, "u_jefe");
    }

    @Test
    @DisplayName("consultar: un empleado no-ADMIN no puede consultar la auditoría → 403")
    void consultar_empleadoNoAdmin() {
        var dueno = dueno();
        var editor = empleado("e1", RolEmpleado.EDITOR, dueno, true);
        when(usuarioRepository.findById("e1")).thenReturn(Optional.of(editor));

        assertThatThrownBy(() -> servicio.consultar("e1", null, null, null, null, null, 0, 20))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.FORBIDDEN));
    }

    private static Usuario dueno() {
        return Usuario.builder()
                .id(ID_DUENO)
                .email(ID_DUENO + "@reforma.com")
                .nombreUsuario("Dueño")
                .apellidoUsuario("Demo")
                .tipoUsuario(TipoUsuario.CLIENTE)
                .planSuscripcion(PlanSuscripcion.BUSINESS)
                .maxGranjas(3)
                .activo(true)
                .emailVerificado(true)
                .esUsuarioEmpleado(false)
                .activoComoEmpleado(false)
                .build();
    }

    private static Usuario empleado(String id, RolEmpleado rol, Usuario dueno, boolean activo) {
        return Usuario.builder()
                .id(id)
                .email(id + "@reforma.com")
                .nombreUsuario("Emp")
                .apellidoUsuario("Demo")
                .tipoUsuario(TipoUsuario.CLIENTE)
                .planSuscripcion(dueno.getPlanSuscripcion())
                .maxGranjas(dueno.getMaxGranjas())
                .activo(true)
                .emailVerificado(true)
                .esUsuarioEmpleado(true)
                .usuarioDueno(dueno)
                .rolEmpleado(rol)
                .activoComoEmpleado(activo)
                .build();
    }
}
