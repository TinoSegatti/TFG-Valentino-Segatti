package com.reforma.domain.auth.jwt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.reforma.domain.common.domain.PlanSuscripcion;
import com.reforma.domain.common.domain.RolEmpleado;
import com.reforma.domain.common.domain.TipoUsuario;
import com.reforma.domain.config.ReformaProperties;
import com.reforma.domain.usuarios.entity.Usuario;
import com.reforma.domain.usuarios.repository.UsuarioRepository;
import io.jsonwebtoken.JwtException;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TokenJwtServicioTest {

    private static final String SECRET =
            "dev_jwt_secret_minimo_64_caracteres_para_desarrollo_local_reforma_2026";

    @Mock private UsuarioRepository usuarioRepository;

    private TokenJwtServicio servicio() {
        var props = new ReformaProperties(
                new ReformaProperties.Jwt(SECRET, 24),
                new ReformaProperties.Ml("http://localhost:8081", SECRET),
                "http://localhost:4200");
        return new TokenJwtServicio(props, usuarioRepository);
    }

    private static Usuario owner(int tokenVersion) {
        return Usuario.builder()
                .id("u_1")
                .email("owner@reforma.com")
                .tipoUsuario(TipoUsuario.CLIENTE)
                .planSuscripcion(PlanSuscripcion.BUSINESS)
                .emailVerificado(true)
                .esUsuarioEmpleado(false)
                .tokenVersion(tokenVersion)
                .build();
    }

    @Test
    @DisplayName("validarToken: tv del token coincide con la BD → principal válido")
    void validar_tvCoincide() {
        var servicio = servicio();
        var token = servicio.generarToken(owner(3));
        when(usuarioRepository.findTokenVersionById("u_1")).thenReturn(Optional.of(3));

        var principal = servicio.validarToken(token);

        assertThat(principal.id()).isEqualTo("u_1");
        assertThat(principal.email()).isEqualTo("owner@reforma.com");
        assertThat(principal.esEmpleado()).isFalse();
        assertThat(principal.rolEmpleado()).isNull();
    }

    @Test
    @DisplayName("validarToken: tv del token quedó atrás (sesión revocada) → JwtException")
    void validar_tvRevocado() {
        var servicio = servicio();
        var token = servicio.generarToken(owner(3));
        // La BD avanzó (p. ej. tras desactivar/cambiar rol/reset): el token viejo ya no vale.
        when(usuarioRepository.findTokenVersionById("u_1")).thenReturn(Optional.of(4));

        assertThatThrownBy(() -> servicio.validarToken(token))
                .isInstanceOf(JwtException.class);
    }

    @Test
    @DisplayName("validarToken: usuario inexistente (cuenta borrada) → JwtException")
    void validar_usuarioInexistente() {
        var servicio = servicio();
        var token = servicio.generarToken(owner(0));
        when(usuarioRepository.findTokenVersionById("u_1")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.validarToken(token))
                .isInstanceOf(JwtException.class);
    }

    @Test
    @DisplayName("generarToken: para un empleado incluye rol e idDueno además del tv")
    void generar_empleadoLlevaRol() {
        var servicio = servicio();
        var dueno = owner(0);
        var empleado = Usuario.builder()
                .id("u_emp")
                .email("emp@reforma.com")
                .tipoUsuario(TipoUsuario.CLIENTE)
                .planSuscripcion(PlanSuscripcion.BUSINESS)
                .emailVerificado(true)
                .esUsuarioEmpleado(true)
                .usuarioDueno(dueno)
                .rolEmpleado(RolEmpleado.EDITOR)
                .tokenVersion(0)
                .build();
        var token = servicio.generarToken(empleado);
        when(usuarioRepository.findTokenVersionById("u_emp")).thenReturn(Optional.of(0));

        var principal = servicio.validarToken(token);

        assertThat(principal.esEmpleado()).isTrue();
        assertThat(principal.rolEmpleado()).isEqualTo(RolEmpleado.EDITOR);
        assertThat(principal.idDueno()).isEqualTo("u_1");
        assertThat(principal.tenantId()).isEqualTo("u_1");
    }
}
