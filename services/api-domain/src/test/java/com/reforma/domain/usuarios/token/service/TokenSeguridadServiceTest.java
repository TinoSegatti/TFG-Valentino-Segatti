package com.reforma.domain.usuarios.token.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.reforma.domain.common.util.TokenHasher;
import com.reforma.domain.usuarios.entity.Usuario;
import com.reforma.domain.usuarios.token.domain.TipoToken;
import com.reforma.domain.usuarios.token.entity.TokenSeguridad;
import com.reforma.domain.usuarios.token.repository.TokenSeguridadRepository;
import java.time.Duration;
import java.time.Instant;
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
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class TokenSeguridadServiceTest {

    @Mock private TokenSeguridadRepository tokenRepository;
    @InjectMocks private TokenSeguridadService servicio;
    @Captor private ArgumentCaptor<TokenSeguridad> tokenCaptor;

    private final Usuario usuario = Usuario.builder().id("u_1").email("u@reforma.com").build();

    @Test
    @DisplayName("emitir: invalida vigentes, guarda el hash y devuelve el token EN CLARO")
    void emitir() {
        var plano = servicio.emitir(usuario, TipoToken.VERIFICACION_EMAIL, Duration.ofHours(24));

        assertThat(plano).isNotBlank();
        verify(tokenRepository).invalidarVigentes(eq("u_1"), eq(TipoToken.VERIFICACION_EMAIL), any());
        verify(tokenRepository).saveAndFlush(tokenCaptor.capture());
        TokenSeguridad guardado = tokenCaptor.getValue();
        // Se persiste el hash del token, nunca el valor en claro.
        assertThat(guardado.getTokenHash()).isEqualTo(TokenHasher.hash(plano)).isNotEqualTo(plano);
        assertThat(guardado.getTipo()).isEqualTo(TipoToken.VERIFICACION_EMAIL);
        assertThat(guardado.getUsuario()).isSameAs(usuario);
        assertThat(guardado.getConsumidoEn()).isNull();
        assertThat(guardado.getFechaExpiracion()).isAfter(Instant.now());
    }

    @Test
    @DisplayName("validarYConsumir: token válido → devuelve usuario y lo marca consumido")
    void validarYConsumir_ok() {
        var plano = "token-en-claro";
        var token = tokenVigente(plano);
        when(tokenRepository.findByTokenHashAndTipo(
                        TokenHasher.hash(plano), TipoToken.RESET_PASSWORD))
                .thenReturn(Optional.of(token));

        var resultado = servicio.validarYConsumir(plano, TipoToken.RESET_PASSWORD);

        assertThat(resultado).isSameAs(usuario);
        assertThat(token.getConsumidoEn()).isNotNull();
    }

    @Test
    @DisplayName("validarYConsumir: token inexistente → 400")
    void validarYConsumir_inexistente() {
        when(tokenRepository.findByTokenHashAndTipo(any(), eq(TipoToken.RESET_PASSWORD)))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.validarYConsumir("x", TipoToken.RESET_PASSWORD))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    @DisplayName("validarYConsumir: token expirado → 400")
    void validarYConsumir_expirado() {
        var plano = "viejo";
        var token = tokenVigente(plano);
        token.setFechaExpiracion(Instant.now().minusSeconds(60));
        when(tokenRepository.findByTokenHashAndTipo(
                        TokenHasher.hash(plano), TipoToken.VERIFICACION_EMAIL))
                .thenReturn(Optional.of(token));

        assertThatThrownBy(() -> servicio.validarYConsumir(plano, TipoToken.VERIFICACION_EMAIL))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    @DisplayName("validarYConsumir: token ya utilizado → 400")
    void validarYConsumir_yaConsumido() {
        var plano = "usado";
        var token = tokenVigente(plano);
        token.setConsumidoEn(Instant.now().minusSeconds(10));
        when(tokenRepository.findByTokenHashAndTipo(
                        TokenHasher.hash(plano), TipoToken.VERIFICACION_EMAIL))
                .thenReturn(Optional.of(token));

        assertThatThrownBy(() -> servicio.validarYConsumir(plano, TipoToken.VERIFICACION_EMAIL))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.BAD_REQUEST));
    }

    private TokenSeguridad tokenVigente(String plano) {
        return TokenSeguridad.builder()
                .id("tk_1")
                .usuario(usuario)
                .tipo(TipoToken.RESET_PASSWORD)
                .tokenHash(TokenHasher.hash(plano))
                .fechaCreacion(Instant.now())
                .fechaExpiracion(Instant.now().plusSeconds(3600))
                .build();
    }
}
