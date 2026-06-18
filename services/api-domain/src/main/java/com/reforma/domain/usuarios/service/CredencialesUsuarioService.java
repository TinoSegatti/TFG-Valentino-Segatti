package com.reforma.domain.usuarios.service;

import com.reforma.domain.auditoria.domain.AccionAuditoria;
import com.reforma.domain.auditoria.dto.AuditoriaEvento;
import com.reforma.domain.auditoria.service.AuditoriaService;
import com.reforma.domain.auth.jwt.TokenJwtServicio;
import com.reforma.domain.common.domain.PlanSuscripcion;
import com.reforma.domain.common.domain.TipoUsuario;
import com.reforma.domain.common.util.IdGenerator;
import com.reforma.domain.usuarios.dto.AuthResponse;
import com.reforma.domain.usuarios.dto.LoginRequest;
import com.reforma.domain.usuarios.dto.RegistroRequest;
import com.reforma.domain.usuarios.dto.UsuarioResponse;
import com.reforma.domain.usuarios.email.EmailNotificacionService;
import com.reforma.domain.usuarios.entity.Usuario;
import com.reforma.domain.usuarios.repository.UsuarioRepository;
import com.reforma.domain.usuarios.token.domain.TipoToken;
import com.reforma.domain.usuarios.token.service.TokenSeguridadService;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class CredencialesUsuarioService {

    private static final String TABLA_USUARIOS = "t_usuarios";

    private static final Duration VIGENCIA_VERIFICACION = Duration.ofHours(24);

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;
    private final TokenJwtServicio tokenJwtServicio;
    private final AuditoriaService auditoriaService;
    private final TokenSeguridadService tokenSeguridadService;
    private final EmailNotificacionService emailNotificacionService;

    @Transactional
    public Map<String, Object> registrarUsuario(RegistroRequest request) {
        if (usuarioRepository.existsByEmailIgnoreCase(request.email())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "El email ya está registrado");
        }
        var usuario = Usuario.builder()
                .id(IdGenerator.newId())
                .email(request.email().toLowerCase().trim())
                .passwordHash(passwordEncoder.encode(request.password()))
                .nombreUsuario(request.nombreUsuario().trim())
                .apellidoUsuario(request.apellidoUsuario().trim())
                .tipoUsuario(TipoUsuario.CLIENTE)
                .planSuscripcion(PlanSuscripcion.DEMO)
                .maxGranjas(1)
                .activo(true)
                .emailVerificado(false)
                .fechaRegistro(Instant.now())
                .esUsuarioEmpleado(false)
                .activoComoEmpleado(false)
                .build();
        // saveAndFlush: la fila debe existir en la transacción antes de auditar/emitir token (FK id_usuario).
        usuarioRepository.saveAndFlush(usuario);
        auditoriaService.registrar(AuditoriaEvento.builder()
                .idUsuario(usuario.getId())
                .tablaOrigen(TABLA_USUARIOS)
                .idRegistro(usuario.getId())
                .accion(AccionAuditoria.REGISTRO)
                .descripcion("Registro de usuario dueño")
                .datosNuevos(Map.of(
                        "email", usuario.getEmail(),
                        "tipoUsuario", usuario.getTipoUsuario().name(),
                        "planSuscripcion", usuario.getPlanSuscripcion().name()))
                .build());
        var tokenVerificacion =
                tokenSeguridadService.emitir(usuario, TipoToken.VERIFICACION_EMAIL, VIGENCIA_VERIFICACION);
        emailNotificacionService.enviarVerificacionEmail(usuario, tokenVerificacion);
        return Map.of(
                "usuario", UsuarioResponse.from(usuario),
                "requiereVerificacion", true,
                "emailEnviado", true,
                "esEmpleado", false);
    }

    @Transactional
    public AuthResponse iniciarSesion(LoginRequest request) {
        var usuario = usuarioRepository
                .findByEmailIgnoreCase(request.email())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Credenciales inválidas"));
        if (usuario.getPasswordHash() == null
                || !passwordEncoder.matches(request.password(), usuario.getPasswordHash())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Credenciales inválidas");
        }
        if (!Boolean.TRUE.equals(usuario.getActivo())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Cuenta desactivada");
        }
        if (!Boolean.TRUE.equals(usuario.getEmailVerificado())) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN, "Debe verificar su email antes de acceder");
        }
        // Un empleado desactivado (baja lógica) no puede iniciar sesión, aunque su cuenta siga activa.
        if (Boolean.TRUE.equals(usuario.getEsUsuarioEmpleado())
                && !Boolean.TRUE.equals(usuario.getActivoComoEmpleado())) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN, "Tu cuenta de empleado fue desactivada");
        }
        usuario.setUltimoAcceso(Instant.now());
        var token = tokenJwtServicio.generarToken(usuario);
        auditoriaService.registrar(AuditoriaEvento.builder()
                .idUsuario(usuario.getId())
                .tablaOrigen(TABLA_USUARIOS)
                .idRegistro(usuario.getId())
                .accion(AccionAuditoria.LOGIN)
                .descripcion("Inicio de sesión")
                .build());
        return new AuthResponse(UsuarioResponse.from(usuario), token);
    }
}
