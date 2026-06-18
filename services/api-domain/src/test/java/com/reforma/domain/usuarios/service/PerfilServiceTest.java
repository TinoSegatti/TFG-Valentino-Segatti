package com.reforma.domain.usuarios.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.reforma.domain.common.domain.PlanSuscripcion;
import com.reforma.domain.common.domain.RolEmpleado;
import com.reforma.domain.common.domain.TipoUsuario;
import com.reforma.domain.usuarios.entity.Usuario;
import com.reforma.domain.usuarios.repository.UsuarioRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PerfilServiceTest {

    @Mock private UsuarioRepository usuarioRepository;
    @InjectMocks private PerfilService servicio;

    private Usuario base() {
        return Usuario.builder()
                .id("u_1")
                .email("ana@reforma.com")
                .nombreUsuario("Ana")
                .apellidoUsuario("Pérez")
                .tipoUsuario(TipoUsuario.CLIENTE)
                .planSuscripcion(PlanSuscripcion.BUSINESS)
                .maxGranjas(3)
                .esUsuarioEmpleado(false)
                .build();
    }

    @Test
    void dueno_rolOwnerConPermisosCompletos() {
        when(usuarioRepository.findById("u_1")).thenReturn(Optional.of(base()));

        var p = servicio.obtenerPerfil("u_1");

        assertThat(p.rol()).isEqualTo("OWNER");
        assertThat(p.esEmpleado()).isFalse();
        assertThat(p.idDueno()).isNull();
        assertThat(p.nombre()).isEqualTo("Ana");
        assertThat(p.apellido()).isEqualTo("Pérez");
        assertThat(p.permisos()).contains("Crear granjas", "Designar administradores", "Ver auditoría");
    }

    @Test
    void empleadoLector_soloLectura() {
        var dueno = base();
        var lector = Usuario.builder()
                .id("u_2")
                .email("leo@reforma.com")
                .nombreUsuario("Leo")
                .apellidoUsuario("R")
                .tipoUsuario(TipoUsuario.CLIENTE)
                .planSuscripcion(PlanSuscripcion.BUSINESS)
                .maxGranjas(3)
                .esUsuarioEmpleado(true)
                .usuarioDueno(dueno)
                .rolEmpleado(RolEmpleado.LECTOR)
                .build();
        when(usuarioRepository.findById("u_2")).thenReturn(Optional.of(lector));

        var p = servicio.obtenerPerfil("u_2");

        assertThat(p.rol()).isEqualTo("LECTOR");
        assertThat(p.esEmpleado()).isTrue();
        assertThat(p.idDueno()).isEqualTo("u_1");
        assertThat(p.permisos()).containsExactly("Ver todos los datos (solo lectura)");
    }

    @Test
    void empleadoEditor_noGestionaEquipoNiCreaGranjas() {
        var editor = Usuario.builder()
                .id("u_3")
                .email("edi@reforma.com")
                .nombreUsuario("Edi")
                .apellidoUsuario("T")
                .tipoUsuario(TipoUsuario.CLIENTE)
                .planSuscripcion(PlanSuscripcion.BUSINESS)
                .maxGranjas(3)
                .esUsuarioEmpleado(true)
                .usuarioDueno(base())
                .rolEmpleado(RolEmpleado.EDITOR)
                .build();
        when(usuarioRepository.findById("u_3")).thenReturn(Optional.of(editor));

        var p = servicio.obtenerPerfil("u_3");

        assertThat(p.rol()).isEqualTo("EDITOR");
        assertThat(p.permisos()).containsExactly("Ver todos los datos", "Crear y editar datos");
    }
}
