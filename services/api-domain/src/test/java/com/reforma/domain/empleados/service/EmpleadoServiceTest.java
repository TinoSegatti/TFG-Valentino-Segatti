package com.reforma.domain.empleados.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.reforma.domain.auditoria.domain.AccionAuditoria;
import com.reforma.domain.auditoria.dto.AuditoriaEvento;
import com.reforma.domain.auditoria.service.AuditoriaService;
import com.reforma.domain.common.domain.PlanSuscripcion;
import com.reforma.domain.common.domain.RolEmpleado;
import com.reforma.domain.common.domain.TipoUsuario;
import com.reforma.domain.empleados.dto.AceptarInvitacionRequest;
import com.reforma.domain.empleados.dto.InvitarEmpleadoRequest;
import com.reforma.domain.suscripciones.service.PlanService;
import com.reforma.domain.usuarios.email.EmailNotificacionService;
import com.reforma.domain.usuarios.entity.Usuario;
import com.reforma.domain.usuarios.repository.UsuarioRepository;
import com.reforma.domain.usuarios.token.domain.TipoToken;
import com.reforma.domain.usuarios.token.service.TokenSeguridadService;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class EmpleadoServiceTest {

    private static final String ID_DUENO = "u_dueno";

    @Mock private UsuarioRepository usuarioRepository;
    @Mock private PlanService planService;
    @Mock private TokenSeguridadService tokenSeguridadService;
    @Mock private EmailNotificacionService emailNotificacionService;
    @Mock private AuditoriaService auditoriaService;
    @Mock private PasswordEncoder passwordEncoder;

    @InjectMocks private EmpleadoService servicio;

    @Captor private ArgumentCaptor<AuditoriaEvento> eventoCaptor;
    @Captor private ArgumentCaptor<Usuario> usuarioCaptor;

    // ---------- invitar ----------

    @Test
    @DisplayName("invitar: persiste empleado pendiente, audita CREAR_EMPLEADO, emite token y envía email")
    void invitar_ok() {
        var dueno = dueno(PlanSuscripcion.BUSINESS);
        var request = new InvitarEmpleadoRequest(
                "Nuevo@Reforma.com ", "Ana", "Pérez", RolEmpleado.EDITOR);
        when(usuarioRepository.findById(ID_DUENO)).thenReturn(Optional.of(dueno));
        when(usuarioRepository.existsByEmailIgnoreCase("nuevo@reforma.com")).thenReturn(false);
        when(planService.obtenerPlanEfectivo(ID_DUENO)).thenReturn(PlanSuscripcion.BUSINESS);
        when(planService.limiteEmpleados(PlanSuscripcion.BUSINESS)).thenReturn(10);
        when(usuarioRepository.countByUsuarioDuenoIdAndActivoComoEmpleadoTrue(ID_DUENO))
                .thenReturn(0L);
        when(tokenSeguridadService.emitir(
                        any(Usuario.class), eq(TipoToken.INVITACION_EMPLEADO), any()))
                .thenReturn("tok-inv");

        var response = servicio.invitar(ID_DUENO, request);

        assertThat(response.email()).isEqualTo("nuevo@reforma.com");
        assertThat(response.rolEmpleado()).isEqualTo(RolEmpleado.EDITOR);
        assertThat(response.activoComoEmpleado()).isFalse();

        verify(usuarioRepository).saveAndFlush(usuarioCaptor.capture());
        Usuario persistido = usuarioCaptor.getValue();
        assertThat(persistido.getEsUsuarioEmpleado()).isTrue();
        assertThat(persistido.getUsuarioDueno()).isSameAs(dueno);
        assertThat(persistido.getPasswordHash()).isNull();
        assertThat(persistido.getEmailVerificado()).isFalse();
        assertThat(persistido.getActivoComoEmpleado()).isFalse();
        // Snapshot no-nulo heredado del dueño (no se usa para gating).
        assertThat(persistido.getPlanSuscripcion()).isEqualTo(PlanSuscripcion.BUSINESS);

        verify(auditoriaService).registrar(eventoCaptor.capture());
        assertThat(eventoCaptor.getValue().accion()).isEqualTo(AccionAuditoria.CREAR_EMPLEADO);
        assertThat(eventoCaptor.getValue().idUsuario()).isEqualTo(ID_DUENO);
        verify(emailNotificacionService).enviarInvitacionEmpleado(eq(dueno), any(Usuario.class), eq("tok-inv"));
    }

    @Test
    @DisplayName("invitar: email ya registrado → 409 sin persistir ni auditar")
    void invitar_emailDuplicado() {
        when(usuarioRepository.findById(ID_DUENO)).thenReturn(Optional.of(dueno(PlanSuscripcion.BUSINESS)));
        when(usuarioRepository.existsByEmailIgnoreCase("dup@reforma.com")).thenReturn(true);
        var request = new InvitarEmpleadoRequest("dup@reforma.com", "Ana", "Pérez", RolEmpleado.LECTOR);

        assertThatThrownBy(() -> servicio.invitar(ID_DUENO, request))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.CONFLICT));

        verify(usuarioRepository, never()).saveAndFlush(any());
        verify(auditoriaService, never()).registrar(any());
    }

    @Test
    @DisplayName("invitar: límite de empleados alcanzado → 403 sin persistir")
    void invitar_sobreLimite() {
        when(usuarioRepository.findById(ID_DUENO)).thenReturn(Optional.of(dueno(PlanSuscripcion.STARTER)));
        when(usuarioRepository.existsByEmailIgnoreCase("nuevo@reforma.com")).thenReturn(false);
        when(planService.obtenerPlanEfectivo(ID_DUENO)).thenReturn(PlanSuscripcion.STARTER);
        when(planService.limiteEmpleados(PlanSuscripcion.STARTER)).thenReturn(2);
        when(usuarioRepository.countByUsuarioDuenoIdAndActivoComoEmpleadoTrue(ID_DUENO))
                .thenReturn(2L);
        var request = new InvitarEmpleadoRequest("nuevo@reforma.com", "Ana", "Pérez", RolEmpleado.EDITOR);

        assertThatThrownBy(() -> servicio.invitar(ID_DUENO, request))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.FORBIDDEN));

        verify(usuarioRepository, never()).saveAndFlush(any());
        verify(tokenSeguridadService, never()).emitir(any(), any(), any());
    }

    @Test
    @DisplayName("invitar: el invitador es un empleado → 403 (solo el dueño invita en esta etapa)")
    void invitar_invitadorEsEmpleado() {
        var empleado = dueno(PlanSuscripcion.BUSINESS);
        empleado.setEsUsuarioEmpleado(true);
        when(usuarioRepository.findById(ID_DUENO)).thenReturn(Optional.of(empleado));
        var request = new InvitarEmpleadoRequest("nuevo@reforma.com", "Ana", "Pérez", RolEmpleado.EDITOR);

        assertThatThrownBy(() -> servicio.invitar(ID_DUENO, request))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.FORBIDDEN));

        verify(usuarioRepository, never()).saveAndFlush(any());
    }

    // ---------- aceptarInvitacion ----------

    @Test
    @DisplayName("aceptarInvitacion: fija contraseña, activa al empleado y audita ACEPTAR_INVITACION")
    void aceptar_ok() {
        var empleado = Usuario.builder()
                .id("u_emp")
                .email("emp@reforma.com")
                .esUsuarioEmpleado(true)
                .rolEmpleado(RolEmpleado.EDITOR)
                .emailVerificado(false)
                .activoComoEmpleado(false)
                .build();
        when(tokenSeguridadService.validarYConsumir("tok-inv", TipoToken.INVITACION_EMPLEADO))
                .thenReturn(empleado);
        when(passwordEncoder.encode("Clave123")).thenReturn("nuevo-hash");

        var response = servicio.aceptarInvitacion(new AceptarInvitacionRequest("tok-inv", "Clave123"));

        assertThat(empleado.getPasswordHash()).isEqualTo("nuevo-hash");
        assertThat(empleado.getEmailVerificado()).isTrue();
        assertThat(empleado.getActivoComoEmpleado()).isTrue();
        assertThat(empleado.getFechaVinculacion()).isNotNull();
        assertThat(response.activoComoEmpleado()).isTrue();
        verify(auditoriaService).registrar(eventoCaptor.capture());
        assertThat(eventoCaptor.getValue().accion()).isEqualTo(AccionAuditoria.ACEPTAR_INVITACION);
    }

    @Test
    @DisplayName("aceptarInvitacion: token inválido → 400 propagado, sin cambios ni auditoría")
    void aceptar_tokenInvalido() {
        when(tokenSeguridadService.validarYConsumir("malo", TipoToken.INVITACION_EMPLEADO))
                .thenThrow(new ResponseStatusException(HttpStatus.BAD_REQUEST, "Token inválido o expirado"));

        assertThatThrownBy(() ->
                        servicio.aceptarInvitacion(new AceptarInvitacionRequest("malo", "Clave123")))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.BAD_REQUEST));

        verify(auditoriaService, never()).registrar(any());
    }

    // ---------- invitar por jefe ADMIN ----------

    @Test
    @DisplayName("invitar: un jefe ADMIN invita bajo su dueño; el plan-gating usa el plan del dueño")
    void invitar_porJefeAdmin() {
        var dueno = dueno(PlanSuscripcion.BUSINESS);
        var jefe = empleado("u_jefe", RolEmpleado.ADMIN, dueno, true);
        when(usuarioRepository.findById("u_jefe")).thenReturn(Optional.of(jefe));
        when(usuarioRepository.existsByEmailIgnoreCase("nuevo@reforma.com")).thenReturn(false);
        when(planService.obtenerPlanEfectivo(ID_DUENO)).thenReturn(PlanSuscripcion.BUSINESS);
        when(planService.limiteEmpleados(PlanSuscripcion.BUSINESS)).thenReturn(10);
        when(usuarioRepository.countByUsuarioDuenoIdAndActivoComoEmpleadoTrue(ID_DUENO)).thenReturn(0L);
        when(tokenSeguridadService.emitir(any(Usuario.class), eq(TipoToken.INVITACION_EMPLEADO), any()))
                .thenReturn("tok-inv");

        servicio.invitar("u_jefe", new InvitarEmpleadoRequest(
                "nuevo@reforma.com", "Ana", "Pérez", RolEmpleado.EDITOR));

        verify(usuarioRepository).saveAndFlush(usuarioCaptor.capture());
        assertThat(usuarioCaptor.getValue().getUsuarioDueno()).isSameAs(dueno);
        verify(emailNotificacionService).enviarInvitacionEmpleado(eq(dueno), any(Usuario.class), eq("tok-inv"));
        verify(auditoriaService).registrar(eventoCaptor.capture());
        assertThat(eventoCaptor.getValue().idUsuario()).isEqualTo("u_jefe");
    }

    @Test
    @DisplayName("invitar: un jefe ADMIN no puede invitar a otro ADMIN → 403")
    void invitar_jefeNoCreaAdmin() {
        var dueno = dueno(PlanSuscripcion.BUSINESS);
        var jefe = empleado("u_jefe", RolEmpleado.ADMIN, dueno, true);
        when(usuarioRepository.findById("u_jefe")).thenReturn(Optional.of(jefe));

        assertThatThrownBy(() -> servicio.invitar("u_jefe",
                new InvitarEmpleadoRequest("nuevo@reforma.com", "Ana", "Pérez", RolEmpleado.ADMIN)))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.FORBIDDEN));
        verify(usuarioRepository, never()).saveAndFlush(any());
    }

    // ---------- listar ----------

    @Test
    @DisplayName("listar: devuelve los empleados del dueño (excluye no-empleados)")
    void listar_ok() {
        var dueno = dueno(PlanSuscripcion.BUSINESS);
        var emp = empleado("e1", RolEmpleado.EDITOR, dueno, true);
        when(usuarioRepository.findById(ID_DUENO)).thenReturn(Optional.of(dueno));
        when(usuarioRepository.findByUsuarioDuenoIdOrderByFechaVinculacionDesc(ID_DUENO))
                .thenReturn(java.util.List.of(emp));

        var lista = servicio.listar(ID_DUENO);

        assertThat(lista).hasSize(1);
        assertThat(lista.get(0).id()).isEqualTo("e1");
    }

    // ---------- cambiarRol ----------

    @Test
    @DisplayName("cambiarRol: el dueño baja un EDITOR a LECTOR y audita")
    void cambiarRol_ok() {
        var dueno = dueno(PlanSuscripcion.BUSINESS);
        var emp = empleado("e1", RolEmpleado.EDITOR, dueno, true);
        when(usuarioRepository.findById(ID_DUENO)).thenReturn(Optional.of(dueno));
        when(usuarioRepository.findById("e1")).thenReturn(Optional.of(emp));

        servicio.cambiarRol(ID_DUENO, "e1", RolEmpleado.LECTOR);

        assertThat(emp.getRolEmpleado()).isEqualTo(RolEmpleado.LECTOR);
        // Cambiar el rol revoca el token en vuelo (lleva las authorities anteriores).
        assertThat(emp.getTokenVersion()).isEqualTo(1);
        verify(auditoriaService).registrar(eventoCaptor.capture());
        assertThat(eventoCaptor.getValue().accion()).isEqualTo(AccionAuditoria.CAMBIO_ROL_EMPLEADO);
    }

    @Test
    @DisplayName("cambiarRol: un jefe no puede modificar a otro ADMIN → 403")
    void cambiarRol_jefeNoTocaOtroAdmin() {
        var dueno = dueno(PlanSuscripcion.BUSINESS);
        var jefe = empleado("u_jefe", RolEmpleado.ADMIN, dueno, true);
        var otroAdmin = empleado("u_admin2", RolEmpleado.ADMIN, dueno, true);
        when(usuarioRepository.findById("u_jefe")).thenReturn(Optional.of(jefe));
        when(usuarioRepository.findById("u_admin2")).thenReturn(Optional.of(otroAdmin));

        assertThatThrownBy(() -> servicio.cambiarRol("u_jefe", "u_admin2", RolEmpleado.EDITOR))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.FORBIDDEN));
    }

    @Test
    @DisplayName("cambiarRol: un jefe no puede designar ADMIN → 403")
    void cambiarRol_jefeNoDesignaAdmin() {
        var dueno = dueno(PlanSuscripcion.BUSINESS);
        var jefe = empleado("u_jefe", RolEmpleado.ADMIN, dueno, true);
        var emp = empleado("e1", RolEmpleado.EDITOR, dueno, true);
        when(usuarioRepository.findById("u_jefe")).thenReturn(Optional.of(jefe));
        when(usuarioRepository.findById("e1")).thenReturn(Optional.of(emp));

        assertThatThrownBy(() -> servicio.cambiarRol("u_jefe", "e1", RolEmpleado.ADMIN))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.FORBIDDEN));
    }

    @Test
    @DisplayName("cambiarRol: no se puede modificar la propia cuenta → 403")
    void cambiarRol_noAutoModificacion() {
        var dueno = dueno(PlanSuscripcion.BUSINESS);
        var jefe = empleado("u_jefe", RolEmpleado.ADMIN, dueno, true);
        when(usuarioRepository.findById("u_jefe")).thenReturn(Optional.of(jefe));

        assertThatThrownBy(() -> servicio.cambiarRol("u_jefe", "u_jefe", RolEmpleado.EDITOR))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.FORBIDDEN));
    }

    // ---------- cambiarEstado ----------

    @Test
    @DisplayName("cambiarEstado: el dueño desactiva (baja lógica) y audita DESACTIVAR_EMPLEADO")
    void cambiarEstado_desactiva() {
        var dueno = dueno(PlanSuscripcion.BUSINESS);
        var emp = empleado("e1", RolEmpleado.EDITOR, dueno, true);
        when(usuarioRepository.findById(ID_DUENO)).thenReturn(Optional.of(dueno));
        when(usuarioRepository.findById("e1")).thenReturn(Optional.of(emp));

        servicio.cambiarEstado(ID_DUENO, "e1", false);

        assertThat(emp.getActivoComoEmpleado()).isFalse();
        // Desactivar corta las sesiones en vuelo de inmediato (tokenVersion 0 → 1).
        assertThat(emp.getTokenVersion()).isEqualTo(1);
        verify(auditoriaService).registrar(eventoCaptor.capture());
        assertThat(eventoCaptor.getValue().accion()).isEqualTo(AccionAuditoria.DESACTIVAR_EMPLEADO);
    }

    @Test
    @DisplayName("cambiarEstado: reactivar NO revoca sesiones (no incrementa tokenVersion)")
    void cambiarEstado_reactivarNoRevoca() {
        var dueno = dueno(PlanSuscripcion.BUSINESS);
        var emp = empleado("e1", RolEmpleado.EDITOR, dueno, false);
        when(usuarioRepository.findById(ID_DUENO)).thenReturn(Optional.of(dueno));
        when(usuarioRepository.findById("e1")).thenReturn(Optional.of(emp));

        servicio.cambiarEstado(ID_DUENO, "e1", true);

        assertThat(emp.getActivoComoEmpleado()).isTrue();
        assertThat(emp.getTokenVersion()).isZero();
    }

    @Test
    @DisplayName("cambiarEstado: empleado de OTRO dueño → 404 (aislamiento de tenant)")
    void cambiarEstado_empleadoDeOtroDueno() {
        var dueno = dueno(PlanSuscripcion.BUSINESS);
        var otroDueno = duenoConId("u_otro", PlanSuscripcion.BUSINESS);
        var ajeno = empleado("e1", RolEmpleado.EDITOR, otroDueno, true);
        when(usuarioRepository.findById(ID_DUENO)).thenReturn(Optional.of(dueno));
        when(usuarioRepository.findById("e1")).thenReturn(Optional.of(ajeno));

        assertThatThrownBy(() -> servicio.cambiarEstado(ID_DUENO, "e1", false))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.NOT_FOUND));
    }

    private static Usuario dueno(PlanSuscripcion plan) {
        return duenoConId(ID_DUENO, plan);
    }

    private static Usuario duenoConId(String id, PlanSuscripcion plan) {
        return Usuario.builder()
                .id(id)
                .email(id + "@reforma.com")
                .nombreUsuario("Dueño")
                .apellidoUsuario("Demo")
                .tipoUsuario(TipoUsuario.CLIENTE)
                .planSuscripcion(plan)
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
