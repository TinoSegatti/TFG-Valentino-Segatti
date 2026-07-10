package com.reforma.domain.usuarios.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.reforma.domain.auditoria.domain.AccionAuditoria;
import com.reforma.domain.auditoria.dto.AuditoriaEvento;
import com.reforma.domain.auditoria.service.AuditoriaService;
import com.reforma.domain.auth.jwt.TokenVersionCache;
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
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class RecuperacionCuentaServiceTest {

    @Mock private UsuarioRepository usuarioRepository;
    @Mock private TokenSeguridadService tokenSeguridadService;
    @Mock private EmailNotificacionService emailNotificacionService;
    @Mock private AuditoriaService auditoriaService;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private TokenVersionCache tokenVersionCache;

    @InjectMocks private RecuperacionCuentaService servicio;

    @Captor private ArgumentCaptor<AuditoriaEvento> eventoCaptor;

    // ---------- verificarEmail ----------

    @Test
    @DisplayName("verificarEmail: consume token, marca emailVerificado y audita")
    void verificarEmail() {
        var usuario = usuario(false, true);
        when(tokenSeguridadService.validarYConsumir("tok", TipoToken.VERIFICACION_EMAIL))
                .thenReturn(usuario);

        servicio.verificarEmail("tok");

        assertThat(usuario.getEmailVerificado()).isTrue();
        verify(auditoriaService).registrar(eventoCaptor.capture());
        assertThat(eventoCaptor.getValue().accion()).isEqualTo(AccionAuditoria.VERIFICACION_EMAIL);
    }

    // ---------- reenviarVerificacion ----------

    @Test
    @DisplayName("reenviarVerificacion: usuario no verificado → emite token y envía email")
    void reenviar_noVerificado() {
        var usuario = usuario(false, true);
        when(usuarioRepository.findByEmailIgnoreCase("u@reforma.com")).thenReturn(Optional.of(usuario));
        when(tokenSeguridadService.emitir(eq(usuario), eq(TipoToken.VERIFICACION_EMAIL), any()))
                .thenReturn("nuevo-token");

        servicio.reenviarVerificacion("u@reforma.com");

        verify(emailNotificacionService).enviarVerificacionEmail(usuario, "nuevo-token");
        verify(auditoriaService).registrar(any());
    }

    @Test
    @DisplayName("reenviarVerificacion: email inexistente → no hace nada y no lanza (anti-enumeración)")
    void reenviar_inexistente() {
        when(usuarioRepository.findByEmailIgnoreCase("nadie@reforma.com")).thenReturn(Optional.empty());

        servicio.reenviarVerificacion("nadie@reforma.com");

        verify(tokenSeguridadService, never()).emitir(any(), any(), any());
        verify(emailNotificacionService, never()).enviarVerificacionEmail(any(), any());
    }

    @Test
    @DisplayName("reenviarVerificacion: usuario ya verificado → no reenvía")
    void reenviar_yaVerificado() {
        var usuario = usuario(true, true);
        when(usuarioRepository.findByEmailIgnoreCase("u@reforma.com")).thenReturn(Optional.of(usuario));

        servicio.reenviarVerificacion("u@reforma.com");

        verify(tokenSeguridadService, never()).emitir(any(), any(), any());
    }

    // ---------- solicitarReset ----------

    @Test
    @DisplayName("solicitarReset: usuario activo → emite token RESET y envía email")
    void solicitarReset_activo() {
        var usuario = usuario(true, true);
        when(usuarioRepository.findByEmailIgnoreCase("u@reforma.com")).thenReturn(Optional.of(usuario));
        when(tokenSeguridadService.emitir(eq(usuario), eq(TipoToken.RESET_PASSWORD), any()))
                .thenReturn("reset-token");

        servicio.solicitarReset("u@reforma.com");

        verify(emailNotificacionService).enviarResetPassword(usuario, "reset-token");
        verify(auditoriaService).registrar(eventoCaptor.capture());
        assertThat(eventoCaptor.getValue().accion()).isEqualTo(AccionAuditoria.SOLICITUD_RESET);
    }

    @Test
    @DisplayName("solicitarReset: email inexistente → no hace nada y no lanza")
    void solicitarReset_inexistente() {
        when(usuarioRepository.findByEmailIgnoreCase("nadie@reforma.com")).thenReturn(Optional.empty());

        servicio.solicitarReset("nadie@reforma.com");

        verify(tokenSeguridadService, never()).emitir(any(), any(), any());
        verify(emailNotificacionService, never()).enviarResetPassword(any(), any());
    }

    // ---------- confirmarReset ----------

    @Test
    @DisplayName("confirmarReset: consume token, fija nueva contraseña (hash) y audita")
    void confirmarReset() {
        var usuario = usuario(false, true);
        when(tokenSeguridadService.validarYConsumir("reset-token", TipoToken.RESET_PASSWORD))
                .thenReturn(usuario);
        when(passwordEncoder.encode("NuevaClave1")).thenReturn("nuevo-hash");

        servicio.confirmarReset("reset-token", "NuevaClave1");

        assertThat(usuario.getPasswordHash()).isEqualTo("nuevo-hash");
        assertThat(usuario.getEmailVerificado()).isTrue();
        // El reset revoca las sesiones abiertas con la clave anterior (tokenVersion 0 → 1).
        assertThat(usuario.getTokenVersion()).isEqualTo(1);
        verify(auditoriaService).registrar(eventoCaptor.capture());
        assertThat(eventoCaptor.getValue().accion()).isEqualTo(AccionAuditoria.RESET_PASSWORD);
    }

    private static Usuario usuario(boolean verificado, boolean activo) {
        return Usuario.builder()
                .id("u_1")
                .email("u@reforma.com")
                .passwordHash("hash-viejo")
                .activo(activo)
                .emailVerificado(verificado)
                .build();
    }
}
