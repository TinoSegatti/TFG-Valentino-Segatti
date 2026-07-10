package com.reforma.domain.mantenimiento.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.reforma.domain.common.domain.PlanSuscripcion;
import com.reforma.domain.mantenimiento.repository.PurgaCuentaDemoRepository;
import com.reforma.domain.usuarios.entity.Usuario;
import com.reforma.domain.usuarios.repository.UsuarioRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LimpiezaCuentasDemoServiceTest {

    @Mock
    private UsuarioRepository usuarioRepository;

    @Mock
    private PurgaCuentaDemoRepository purgaRepository;

    private Usuario demo(String id, String email) {
        return Usuario.builder()
                .id(id)
                .email(email)
                .fechaRegistro(Instant.now().minus(90, ChronoUnit.DAYS))
                .build();
    }

    private LimpiezaCuentasDemoService servicio(boolean habilitada, String exentos) {
        return new LimpiezaCuentasDemoService(usuarioRepository, purgaRepository, habilitada, 60, exentos);
    }

    @Test
    void purgaVencidasYOmiteExentas() {
        when(usuarioRepository.findCuentasDemoVencidas(eq(PlanSuscripcion.DEMO), any()))
                .thenReturn(List.of(demo("u1", "viejo@prueba.local"), demo("u2", "Autor@Reforma.com")));

        // El exento se compara sin distinción de mayúsculas/minúsculas.
        servicio(true, "autor@reforma.com").purgarCuentasDemoVencidas();

        verify(purgaRepository).purgarTenant("u1");
        verify(purgaRepository, never()).purgarTenant("u2");
    }

    @Test
    void noHaceNadaSiEstaDeshabilitada() {
        servicio(false, "").purgarCuentasDemoVencidas();
        verifyNoInteractions(usuarioRepository, purgaRepository);
    }

    @Test
    void usaElCorteSegunLaRetencion() {
        when(usuarioRepository.findCuentasDemoVencidas(eq(PlanSuscripcion.DEMO), any()))
                .thenReturn(List.of());

        Instant antes = Instant.now().minus(60, ChronoUnit.DAYS);
        servicio(true, "").purgarCuentasDemoVencidas();
        Instant despues = Instant.now().minus(60, ChronoUnit.DAYS);

        ArgumentCaptor<Instant> corte = ArgumentCaptor.forClass(Instant.class);
        verify(usuarioRepository)
                .findCuentasDemoVencidas(eq(PlanSuscripcion.DEMO), corte.capture());
        assertThat(corte.getValue()).isBetween(antes, despues);
    }

    @Test
    void unFalloAisladoNoDetieneAlResto() {
        when(usuarioRepository.findCuentasDemoVencidas(eq(PlanSuscripcion.DEMO), any()))
                .thenReturn(List.of(demo("u1", "a@prueba.local"), demo("u2", "b@prueba.local")));
        doThrow(new RuntimeException("FK boom")).when(purgaRepository).purgarTenant("u1");

        servicio(true, "").purgarCuentasDemoVencidas();

        verify(purgaRepository, times(1)).purgarTenant("u1");
        verify(purgaRepository, times(1)).purgarTenant("u2");
    }
}
