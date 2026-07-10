package com.reforma.domain.usuarios.service;

import com.reforma.domain.auditoria.domain.AccionAuditoria;
import com.reforma.domain.auditoria.dto.AuditoriaEvento;
import com.reforma.domain.auditoria.service.AuditoriaService;
import com.reforma.domain.auth.jwt.TokenVersionCache;
import com.reforma.domain.usuarios.email.EmailNotificacionService;
import com.reforma.domain.usuarios.entity.Usuario;
import com.reforma.domain.usuarios.repository.UsuarioRepository;
import com.reforma.domain.usuarios.token.domain.TipoToken;
import com.reforma.domain.usuarios.token.service.TokenSeguridadService;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Verificación de email y recuperación de contraseña (RF-AUTH, Etapa 1).
 *
 * <p>Anti-enumeración: {@link #reenviarVerificacion} y {@link #solicitarReset} no revelan si el
 * email existe; siempre terminan sin error. Solo se actúa (token + email) cuando corresponde.
 */
@Service
@RequiredArgsConstructor
public class RecuperacionCuentaService {

    private static final String TABLA_USUARIOS = "t_usuarios";
    private static final Duration VIGENCIA_VERIFICACION = Duration.ofHours(24);
    private static final Duration VIGENCIA_RESET = Duration.ofHours(1);

    private final UsuarioRepository usuarioRepository;
    private final TokenSeguridadService tokenSeguridadService;
    private final EmailNotificacionService emailNotificacionService;
    private final AuditoriaService auditoriaService;
    private final PasswordEncoder passwordEncoder;
    private final TokenVersionCache tokenVersionCache;

    @Transactional
    public void verificarEmail(String tokenPlano) {
        var usuario = tokenSeguridadService.validarYConsumir(tokenPlano, TipoToken.VERIFICACION_EMAIL);
        usuario.setEmailVerificado(true);
        auditar(usuario, AccionAuditoria.VERIFICACION_EMAIL, "Email verificado");
    }

    @Transactional
    public void reenviarVerificacion(String email) {
        usuarioRepository
                .findByEmailIgnoreCase(email.trim())
                .filter(u -> !Boolean.TRUE.equals(u.getEmailVerificado()))
                .ifPresent(usuario -> {
                    var token = tokenSeguridadService.emitir(
                            usuario, TipoToken.VERIFICACION_EMAIL, VIGENCIA_VERIFICACION);
                    emailNotificacionService.enviarVerificacionEmail(usuario, token);
                    auditar(usuario, AccionAuditoria.REENVIO_VERIFICACION, "Reenvío de verificación");
                });
    }

    @Transactional
    public void solicitarReset(String email) {
        usuarioRepository
                .findByEmailIgnoreCase(email.trim())
                .filter(u -> Boolean.TRUE.equals(u.getActivo()))
                .ifPresent(usuario -> {
                    var token = tokenSeguridadService.emitir(
                            usuario, TipoToken.RESET_PASSWORD, VIGENCIA_RESET);
                    emailNotificacionService.enviarResetPassword(usuario, token);
                    auditar(usuario, AccionAuditoria.SOLICITUD_RESET, "Solicitud de recuperación de contraseña");
                });
    }

    @Transactional
    public void confirmarReset(String tokenPlano, String nuevaPassword) {
        var usuario = tokenSeguridadService.validarYConsumir(tokenPlano, TipoToken.RESET_PASSWORD);
        usuario.setPasswordHash(passwordEncoder.encode(nuevaPassword));
        // Controlar el email implica probarlo: si no estaba verificado, queda verificado.
        usuario.setEmailVerificado(true);
        // Un reset de contraseña invalida cualquier sesión abierta con la clave anterior.
        usuario.revocarSesiones();
        tokenVersionCache.invalidar(usuario.getId());
        auditar(usuario, AccionAuditoria.RESET_PASSWORD, "Contraseña restablecida");
    }

    private void auditar(Usuario usuario, AccionAuditoria accion, String descripcion) {
        auditoriaService.registrar(AuditoriaEvento.builder()
                .idUsuario(usuario.getId())
                .tablaOrigen(TABLA_USUARIOS)
                .idRegistro(usuario.getId())
                .accion(accion)
                .descripcion(descripcion)
                .build());
    }
}
