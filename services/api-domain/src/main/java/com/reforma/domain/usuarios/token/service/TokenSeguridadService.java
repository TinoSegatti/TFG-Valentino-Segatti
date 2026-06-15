package com.reforma.domain.usuarios.token.service;

import com.reforma.domain.common.util.IdGenerator;
import com.reforma.domain.common.util.TokenHasher;
import com.reforma.domain.usuarios.entity.Usuario;
import com.reforma.domain.usuarios.token.domain.TipoToken;
import com.reforma.domain.usuarios.token.entity.TokenSeguridad;
import com.reforma.domain.usuarios.token.repository.TokenSeguridadRepository;
import java.time.Duration;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Emite y valida tokens de seguridad de un solo uso ({@code t_token_seguridad}). Centraliza
 * verificación de email, reset de contraseña e invitación de empleados (Etapa 3).
 * Solo se persiste el hash del token; el valor en claro lo devuelve {@link #emitir} para
 * incrustarlo en el email y nunca se vuelve a poder recuperar.
 */
@Service
@RequiredArgsConstructor
public class TokenSeguridadService {

    private final TokenSeguridadRepository tokenRepository;

    /**
     * Genera un token nuevo para el usuario, invalidando los vigentes del mismo tipo
     * (un único token activo por tipo). Devuelve el token EN CLARO para el email.
     */
    @Transactional
    public String emitir(Usuario usuario, TipoToken tipo, Duration vigencia) {
        var ahora = Instant.now();
        tokenRepository.invalidarVigentes(usuario.getId(), tipo, ahora);
        var tokenPlano = TokenHasher.generarTokenPlano();
        var token = TokenSeguridad.builder()
                .id(IdGenerator.newId())
                .usuario(usuario)
                .tipo(tipo)
                .tokenHash(TokenHasher.hash(tokenPlano))
                .fechaCreacion(ahora)
                .fechaExpiracion(ahora.plus(vigencia))
                .build();
        tokenRepository.saveAndFlush(token);
        return tokenPlano;
    }

    /**
     * Valida un token en claro y lo consume (un solo uso). Devuelve el usuario asociado.
     * Lanza 400 si el token es inexistente, ya utilizado o expirado (mensaje uniforme para
     * no filtrar el motivo exacto).
     */
    @Transactional
    public Usuario validarYConsumir(String tokenPlano, TipoToken tipo) {
        if (tokenPlano == null || tokenPlano.isBlank()) {
            throw tokenInvalido();
        }
        var token = tokenRepository
                .findByTokenHashAndTipo(TokenHasher.hash(tokenPlano), tipo)
                .orElseThrow(TokenSeguridadService::tokenInvalido);
        if (token.getConsumidoEn() != null || token.getFechaExpiracion().isBefore(Instant.now())) {
            throw tokenInvalido();
        }
        token.setConsumidoEn(Instant.now());
        return token.getUsuario();
    }

    private static ResponseStatusException tokenInvalido() {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, "Token inválido o expirado");
    }
}
