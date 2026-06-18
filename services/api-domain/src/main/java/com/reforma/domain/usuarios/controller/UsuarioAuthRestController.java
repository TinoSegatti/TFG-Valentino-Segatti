

package com.reforma.domain.usuarios.controller;

import com.reforma.domain.auth.SecurityUtils;
import com.reforma.domain.usuarios.dto.AuthResponse;
import com.reforma.domain.usuarios.dto.ConfirmarResetRequest;
import com.reforma.domain.usuarios.dto.LoginRequest;
import com.reforma.domain.usuarios.dto.PerfilResponse;
import com.reforma.domain.usuarios.dto.RegistroRequest;
import com.reforma.domain.usuarios.dto.ReenviarVerificacionRequest;
import com.reforma.domain.usuarios.dto.SolicitarResetRequest;
import com.reforma.domain.usuarios.dto.VerificarEmailRequest;
import com.reforma.domain.usuarios.service.CredencialesUsuarioService;
import com.reforma.domain.usuarios.service.PerfilService;
import com.reforma.domain.usuarios.service.RecuperacionCuentaService;
import jakarta.validation.Valid;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/usuarios")
@RequiredArgsConstructor
public class UsuarioAuthRestController {

    private final CredencialesUsuarioService credencialesUsuarioService;
    private final RecuperacionCuentaService recuperacionCuentaService;
    private final PerfilService perfilService;

    @PostMapping("/registro")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> registro(@Valid @RequestBody RegistroRequest request) {
        return credencialesUsuarioService.registrarUsuario(request);
    }

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest request) {
        return credencialesUsuarioService.iniciarSesion(request);
    }

    /** Perfil del usuario autenticado: identidad, rol efectivo y permisos. */
    @GetMapping("/perfil")
    public PerfilResponse perfil() {
        return perfilService.obtenerPerfil(SecurityUtils.requireUserId());
    }

    @PostMapping("/verificar-email")
    public Map<String, Object> verificarEmail(@Valid @RequestBody VerificarEmailRequest request) {
        recuperacionCuentaService.verificarEmail(request.token());
        return Map.of("verificado", true);
    }

    /** Respuesta genérica (anti-enumeración): no revela si el email existe o ya está verificado. */
    @PostMapping("/reenviar-verificacion")
    public Map<String, Object> reenviarVerificacion(
            @Valid @RequestBody ReenviarVerificacionRequest request) {
        recuperacionCuentaService.reenviarVerificacion(request.email());
        return Map.of("enviado", true);
    }

    /** Respuesta genérica (anti-enumeración): no revela si el email existe. */
    @PostMapping("/solicitar-reset")
    public Map<String, Object> solicitarReset(@Valid @RequestBody SolicitarResetRequest request) {
        recuperacionCuentaService.solicitarReset(request.email());
        return Map.of("enviado", true);
    }

    @PostMapping("/confirmar-reset")
    public Map<String, Object> confirmarReset(@Valid @RequestBody ConfirmarResetRequest request) {
        recuperacionCuentaService.confirmarReset(request.token(), request.nuevaPassword());
        return Map.of("restablecido", true);
    }
}
