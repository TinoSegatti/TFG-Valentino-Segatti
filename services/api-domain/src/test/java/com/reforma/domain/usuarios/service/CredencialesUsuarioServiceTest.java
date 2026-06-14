package com.reforma.domain.usuarios.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.reforma.domain.auditoria.domain.AccionAuditoria;
import com.reforma.domain.auditoria.dto.AuditoriaEvento;
import com.reforma.domain.auditoria.service.AuditoriaService;
import com.reforma.domain.auth.jwt.TokenJwtServicio;
import com.reforma.domain.usuarios.dto.LoginRequest;
import com.reforma.domain.usuarios.dto.RegistroRequest;
import com.reforma.domain.usuarios.entity.Usuario;
import com.reforma.domain.usuarios.repository.UsuarioRepository;
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
class CredencialesUsuarioServiceTest {

    @Mock private UsuarioRepository usuarioRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private TokenJwtServicio tokenJwtServicio;
    @Mock private AuditoriaService auditoriaService;

    @InjectMocks private CredencialesUsuarioService servicio;

    @Captor private ArgumentCaptor<AuditoriaEvento> eventoCaptor;

    // ---------- registro ----------

    @Test
    @DisplayName("registro: persiste con saveAndFlush y audita acción REGISTRO")
    void registro_auditaAlta() {
        var request = new RegistroRequest("Nuevo@Reforma.com ", "Clave123", "Ana", "Pérez", null);
        when(usuarioRepository.existsByEmailIgnoreCase("Nuevo@Reforma.com ")).thenReturn(false);
        when(passwordEncoder.encode("Clave123")).thenReturn("hash");

        var resultado = servicio.registrarUsuario(request);

        assertThat(resultado.get("requiereVerificacion")).isEqualTo(true);
        verify(usuarioRepository).saveAndFlush(any(Usuario.class));
        verify(auditoriaService).registrar(eventoCaptor.capture());
        AuditoriaEvento evento = eventoCaptor.getValue();
        assertThat(evento.accion()).isEqualTo(AccionAuditoria.REGISTRO);
        assertThat(evento.tablaOrigen()).isEqualTo("t_usuarios");
        assertThat(evento.idUsuario()).isNotBlank();
        assertThat(evento.datosNuevos()).isNotNull();
    }

    @Test
    @DisplayName("registro: email ya existente → 409 sin persistir ni auditar")
    void registro_emailDuplicado() {
        var request = new RegistroRequest("dup@reforma.com", "Clave123", "Ana", "Pérez", null);
        when(usuarioRepository.existsByEmailIgnoreCase("dup@reforma.com")).thenReturn(true);

        assertThatThrownBy(() -> servicio.registrarUsuario(request))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.CONFLICT));

        verify(usuarioRepository, never()).saveAndFlush(any());
        verify(auditoriaService, never()).registrar(any());
    }

    // ---------- login ----------

    @Test
    @DisplayName("login: credenciales válidas y verificado → token + auditoría LOGIN")
    void login_exitoAuditaLogin() {
        var usuario = usuarioActivoVerificado();
        when(usuarioRepository.findByEmailIgnoreCase("u@reforma.com")).thenReturn(Optional.of(usuario));
        when(passwordEncoder.matches("Clave123", "hash")).thenReturn(true);
        when(tokenJwtServicio.generarToken(usuario)).thenReturn("jwt-token");

        var response = servicio.iniciarSesion(new LoginRequest("u@reforma.com", "Clave123"));

        assertThat(response.token()).isEqualTo("jwt-token");
        assertThat(usuario.getUltimoAcceso()).isNotNull();
        verify(auditoriaService).registrar(eventoCaptor.capture());
        assertThat(eventoCaptor.getValue().accion()).isEqualTo(AccionAuditoria.LOGIN);
    }

    @Test
    @DisplayName("login: contraseña inválida → 401 y auditoría LOGIN_FALLIDO en tx independiente")
    void login_passwordInvalida() {
        var usuario = usuarioActivoVerificado();
        when(usuarioRepository.findByEmailIgnoreCase("u@reforma.com")).thenReturn(Optional.of(usuario));
        when(passwordEncoder.matches("mala", "hash")).thenReturn(false);

        assertThatThrownBy(() -> servicio.iniciarSesion(new LoginRequest("u@reforma.com", "mala")))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.UNAUTHORIZED));

        verify(auditoriaService).registrarIndependiente(eventoCaptor.capture());
        assertThat(eventoCaptor.getValue().accion()).isEqualTo(AccionAuditoria.LOGIN_FALLIDO);
        verify(auditoriaService, never()).registrar(any());
        verify(tokenJwtServicio, never()).generarToken(any());
    }

    @Test
    @DisplayName("login: email no verificado → 403 y auditoría LOGIN_FALLIDO")
    void login_noVerificado() {
        var usuario = usuarioActivoVerificado();
        usuario.setEmailVerificado(false);
        when(usuarioRepository.findByEmailIgnoreCase("u@reforma.com")).thenReturn(Optional.of(usuario));
        when(passwordEncoder.matches("Clave123", "hash")).thenReturn(true);

        assertThatThrownBy(() -> servicio.iniciarSesion(new LoginRequest("u@reforma.com", "Clave123")))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.FORBIDDEN));

        verify(auditoriaService).registrarIndependiente(eventoCaptor.capture());
        assertThat(eventoCaptor.getValue().accion()).isEqualTo(AccionAuditoria.LOGIN_FALLIDO);
    }

    @Test
    @DisplayName("login: email inexistente → 401 sin auditoría (no hay usuario referenciable)")
    void login_emailInexistente() {
        when(usuarioRepository.findByEmailIgnoreCase("nadie@reforma.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.iniciarSesion(new LoginRequest("nadie@reforma.com", "x")))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.UNAUTHORIZED));

        verify(auditoriaService, never()).registrar(any());
        verify(auditoriaService, never()).registrarIndependiente(any());
    }

    private static Usuario usuarioActivoVerificado() {
        return Usuario.builder()
                .id("u_1")
                .email("u@reforma.com")
                .passwordHash("hash")
                .nombreUsuario("Ana")
                .apellidoUsuario("Pérez")
                .tipoUsuario(com.reforma.domain.common.domain.TipoUsuario.CLIENTE)
                .planSuscripcion(com.reforma.domain.common.domain.PlanSuscripcion.DEMO)
                .maxGranjas(1)
                .activo(true)
                .emailVerificado(true)
                .build();
    }
}
