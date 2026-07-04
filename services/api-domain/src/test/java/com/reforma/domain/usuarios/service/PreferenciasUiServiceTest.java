package com.reforma.domain.usuarios.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.reforma.domain.usuarios.dto.PreferenciasUiRequest;
import com.reforma.domain.usuarios.entity.Usuario;
import com.reforma.domain.usuarios.repository.UsuarioRepository;
import java.math.BigDecimal;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class PreferenciasUiServiceTest {

    private static final String IMAGEN_OK = "data:image/jpeg;base64,aGVsbG8=";

    @Mock private UsuarioRepository usuarioRepository;

    private PreferenciasUiService servicio;

    @BeforeEach
    void crearServicio() {
        servicio = new PreferenciasUiService(usuarioRepository, new ObjectMapper());
    }

    private Usuario usuario(String preferencias) {
        return Usuario.builder()
                .id("u_1")
                .email("ana@reforma.com")
                .nombreUsuario("Ana")
                .apellidoUsuario("Pérez")
                .esUsuarioEmpleado(false)
                .preferenciasUi(preferencias)
                .build();
    }

    @Test
    void obtener_sinPreferenciasGuardadas_devuelveDefaults() {
        when(usuarioRepository.findById("u_1")).thenReturn(Optional.of(usuario(null)));

        var prefs = servicio.obtener("u_1");

        assertThat(prefs.fondo()).isEqualTo("default");
        assertThat(prefs.intensidadCortina()).isEqualByComparingTo("0.55");
        assertThat(prefs.imagenPersonalizada()).isNull();
    }

    @Test
    void actualizar_persisteYLuegoSeLee() {
        var u = usuario(null);
        when(usuarioRepository.findById("u_1")).thenReturn(Optional.of(u));

        var guardadas = servicio.actualizar(
                "u_1",
                new PreferenciasUiRequest("escena-amanecer", new BigDecimal("0.50"), null));

        assertThat(guardadas.fondo()).isEqualTo("escena-amanecer");
        verify(usuarioRepository).save(u);
        assertThat(u.getPreferenciasUi()).contains("escena-amanecer").contains("0.50");

        var leidas = servicio.obtener("u_1");
        assertThat(leidas.fondo()).isEqualTo("escena-amanecer");
        assertThat(leidas.intensidadCortina()).isEqualByComparingTo("0.50");
    }

    @Test
    void actualizar_imagenPersonalizada_persisteDataUrl() {
        var u = usuario(null);
        when(usuarioRepository.findById("u_1")).thenReturn(Optional.of(u));

        var guardadas = servicio.actualizar(
                "u_1",
                new PreferenciasUiRequest("personalizada", new BigDecimal("0.60"), IMAGEN_OK));

        assertThat(guardadas.imagenPersonalizada()).isEqualTo(IMAGEN_OK);

        var leidas = servicio.obtener("u_1");
        assertThat(leidas.fondo()).isEqualTo("personalizada");
        assertThat(leidas.imagenPersonalizada()).isEqualTo(IMAGEN_OK);
    }

    @Test
    void actualizar_fondoDeGaleria_descartaImagenResidual() {
        var u = usuario(null);
        when(usuarioRepository.findById("u_1")).thenReturn(Optional.of(u));

        var guardadas = servicio.actualizar(
                "u_1",
                new PreferenciasUiRequest("color-bosque", new BigDecimal("0.50"), IMAGEN_OK));

        assertThat(guardadas.imagenPersonalizada()).isNull();
        assertThat(u.getPreferenciasUi()).doesNotContain("base64");
    }

    @Test
    void actualizar_personalizadaSinImagen_400() {
        assertThatThrownBy(() -> servicio.actualizar(
                        "u_1",
                        new PreferenciasUiRequest("personalizada", new BigDecimal("0.50"), null)))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("400");
        verify(usuarioRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void actualizar_imagenQueNoEsDataUrl_400() {
        assertThatThrownBy(() -> servicio.actualizar(
                        "u_1",
                        new PreferenciasUiRequest(
                                "personalizada",
                                new BigDecimal("0.50"),
                                "https://evil.example/fondo.png")))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("400");
    }

    @Test
    void actualizar_imagenDemasiadoGrande_400() {
        String gigante = "data:image/jpeg;base64," + "A".repeat(1_400_001);
        assertThatThrownBy(() -> servicio.actualizar(
                        "u_1",
                        new PreferenciasUiRequest("personalizada", new BigDecimal("0.50"), gigante)))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("400");
    }

    @Test
    void actualizar_fondoFueraDeLaGaleria_400() {
        assertThatThrownBy(() -> servicio.actualizar(
                        "u_1",
                        new PreferenciasUiRequest(
                                "https://evil.example/fondo.png", new BigDecimal("0.50"), null)))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("400");
        verify(usuarioRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void actualizar_cortinaBajoElMinimo_400() {
        assertThatThrownBy(() -> servicio.actualizar(
                        "u_1", new PreferenciasUiRequest("default", new BigDecimal("0.20"), null)))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("400");
    }

    @Test
    void actualizar_cortinaSobreElMaximo_400() {
        assertThatThrownBy(() -> servicio.actualizar(
                        "u_1", new PreferenciasUiRequest("default", new BigDecimal("0.90"), null)))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("400");
    }

    @Test
    void obtener_fondoGuardadoQueSalioDeLaGaleria_degradaAlDefault() {
        when(usuarioRepository.findById("u_1"))
                .thenReturn(Optional.of(
                        usuario("{\"fondo\":\"retirado\",\"intensidadCortina\":0.95}")));

        var prefs = servicio.obtener("u_1");

        assertThat(prefs.fondo()).isEqualTo("default");
        // El alfa fuera de rango se acota al máximo permitido en lugar de romper la carga.
        assertThat(prefs.intensidadCortina()).isEqualByComparingTo("0.85");
    }

    @Test
    void obtener_personalizadaGuardadaSinImagen_degradaAlDefault() {
        when(usuarioRepository.findById("u_1"))
                .thenReturn(Optional.of(
                        usuario("{\"fondo\":\"personalizada\",\"intensidadCortina\":0.5}")));

        var prefs = servicio.obtener("u_1");

        assertThat(prefs.fondo()).isEqualTo("default");
        assertThat(prefs.imagenPersonalizada()).isNull();
    }

    @Test
    void obtener_jsonCorrupto_devuelveDefaults() {
        when(usuarioRepository.findById("u_1"))
                .thenReturn(Optional.of(usuario("{no-es-json")));

        var prefs = servicio.obtener("u_1");

        assertThat(prefs.fondo()).isEqualTo("default");
        assertThat(prefs.intensidadCortina()).isEqualByComparingTo("0.55");
    }
}
