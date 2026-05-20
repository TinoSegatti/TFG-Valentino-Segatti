package com.reforma.domain.usuarios.controller;

import com.reforma.domain.usuarios.dto.AuthResponse;
import com.reforma.domain.usuarios.dto.LoginRequest;
import com.reforma.domain.usuarios.dto.RegistroRequest;
import com.reforma.domain.usuarios.service.CredencialesUsuarioService;
import jakarta.validation.Valid;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
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

    @PostMapping("/registro")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> registro(@Valid @RequestBody RegistroRequest request) {
        return credencialesUsuarioService.registrarUsuario(request);
    }

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest request) {
        return credencialesUsuarioService.iniciarSesion(request);
    }
}
