package com.reforma.domain.usuarios.service;

import com.reforma.domain.auth.jwt.TokenJwtServicio;
import com.reforma.domain.common.domain.PlanSuscripcion;
import com.reforma.domain.common.domain.TipoUsuario;
import com.reforma.domain.common.util.IdGenerator;
import com.reforma.domain.usuarios.dto.AuthResponse;
import com.reforma.domain.usuarios.dto.LoginRequest;
import com.reforma.domain.usuarios.dto.RegistroRequest;
import com.reforma.domain.usuarios.dto.UsuarioResponse;
import com.reforma.domain.usuarios.entity.Usuario;
import com.reforma.domain.usuarios.repository.UsuarioRepository;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class CredencialesUsuarioService {

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;
    private final TokenJwtServicio tokenJwtServicio;

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
                .tokenVerificacion(UUID.randomUUID().toString().replace("-", ""))
                .fechaExpiracionToken(Instant.now().plusSeconds(86400))
                .fechaRegistro(Instant.now())
                .esUsuarioEmpleado(false)
                .activoComoEmpleado(false)
                .build();
        usuarioRepository.save(usuario);
        // TODO: EmailNotificacionService — enviar verificación (RF-AUTH-001)
        return Map.of(
                "usuario", UsuarioResponse.from(usuario),
                "requiereVerificacion", true,
                "emailEnviado", false,
                "esEmpleado", false);
    }

    @Transactional(readOnly = true)
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
        usuario.setUltimoAcceso(Instant.now());
        var token = tokenJwtServicio.generarToken(usuario);
        return new AuthResponse(UsuarioResponse.from(usuario), token);
    }
}
