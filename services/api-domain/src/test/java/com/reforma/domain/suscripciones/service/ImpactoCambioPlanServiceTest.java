package com.reforma.domain.suscripciones.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import com.reforma.domain.animales.repository.AnimalRepository;
import com.reforma.domain.archivos.repository.ArchivoRepository;
import com.reforma.domain.common.domain.PlanSuscripcion;
import com.reforma.domain.fabricaciones.repository.FabricacionRepository;
import com.reforma.domain.formulas.repository.FormulaCabeceraRepository;
import com.reforma.domain.granjas.entity.Granja;
import com.reforma.domain.granjas.repository.GranjaRepository;
import com.reforma.domain.materiasprimas.repository.MateriaPrimaRepository;
import com.reforma.domain.proveedores.repository.ProveedorRepository;
import com.reforma.domain.suscripciones.domain.EstadoSuscripcion;
import com.reforma.domain.suscripciones.dto.CambioPlanImpactoResponse.TipoCambio;
import com.reforma.domain.suscripciones.entity.Suscripcion;
import com.reforma.domain.suscripciones.repository.SuscripcionRepository;
import com.reforma.domain.usuarios.entity.Usuario;
import com.reforma.domain.usuarios.repository.UsuarioRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** Preview de impacto de cambio de plan (RD-P6.c): bloqueantes vs. advertencias. */
@ExtendWith(MockitoExtension.class)
class ImpactoCambioPlanServiceTest {

    private static final String ID_DUENO = "u_1";
    private static final Instant FIN_CICLO = Instant.parse("2026-08-01T00:00:00Z");

    @Mock private UsuarioRepository usuarioRepository;
    @Mock private SuscripcionRepository suscripcionRepository;
    @Mock private GranjaRepository granjaRepository;
    @Mock private MateriaPrimaRepository materiaPrimaRepository;
    @Mock private ProveedorRepository proveedorRepository;
    @Mock private AnimalRepository animalRepository;
    @Mock private FormulaCabeceraRepository formulaCabeceraRepository;
    @Mock private FabricacionRepository fabricacionRepository;
    @Mock private ArchivoRepository archivoRepository;

    private ImpactoCambioPlanService servicio;

    @BeforeEach
    void configurar() {
        servicio = new ImpactoCambioPlanService(
                usuarioRepository,
                suscripcionRepository,
                new PlanService(usuarioRepository),
                granjaRepository,
                materiaPrimaRepository,
                proveedorRepository,
                animalRepository,
                formulaCabeceraRepository,
                fabricacionRepository,
                archivoRepository);
    }

    private void duenoConPlan(PlanSuscripcion plan) {
        when(usuarioRepository.findById(ID_DUENO)).thenReturn(Optional.of(Usuario.builder()
                .id(ID_DUENO)
                .email("ana@reforma.com")
                .planSuscripcion(plan)
                .esUsuarioEmpleado(false)
                .build()));
    }

    private Granja granja(String id, String nombre) {
        var g = new Granja();
        g.setId(id);
        g.setNombreGranja(nombre);
        return g;
    }

    /** Todos los conteos por granja en cero salvo que el test los pise. */
    private void granjaSinExcesos(String id) {
        lenient().when(materiaPrimaRepository.countByGranjaIdAndActivaTrue(id)).thenReturn(0L);
        lenient().when(proveedorRepository.countByGranjaIdAndActivoTrue(id)).thenReturn(0L);
        lenient().when(animalRepository.countByGranjaIdAndActivoTrue(id)).thenReturn(0L);
        lenient().when(formulaCabeceraRepository.countByGranjaIdAndActivaTrue(id)).thenReturn(0L);
        lenient().when(fabricacionRepository.countByGranjaIdAndActivoTrue(id)).thenReturn(0L);
        lenient().when(archivoRepository.countByIdGranja(id)).thenReturn(0L);
    }

    @Test
    @DisplayName("downgrade con empleados sobre el límite destino → bloqueante y aplicaDesde = fin de ciclo")
    void downgrade_empleadosBloquean() {
        duenoConPlan(PlanSuscripcion.BUSINESS);
        when(usuarioRepository.countByUsuarioDuenoIdAndActivoComoEmpleadoTrue(ID_DUENO))
                .thenReturn(5L); // límite STARTER = 2
        when(granjaRepository.findByUsuarioIdAndActivaTrueOrderByNombreGranjaAsc(ID_DUENO))
                .thenReturn(List.of());
        when(suscripcionRepository.findByIdUsuario(ID_DUENO)).thenReturn(Optional.of(
                Suscripcion.builder()
                        .id(10L)
                        .idUsuario(ID_DUENO)
                        .estado(EstadoSuscripcion.ACTIVA)
                        .fechaFinPeriodo(FIN_CICLO)
                        .build()));

        var r = servicio.impacto(ID_DUENO, PlanSuscripcion.STARTER);

        assertThat(r.tipoCambio()).isEqualTo(TipoCambio.DOWNGRADE);
        assertThat(r.aplicaDesde()).isEqualTo(FIN_CICLO);
        assertThat(r.bloqueantes()).hasSize(1);
        var bloqueante = r.bloqueantes().get(0);
        assertThat(bloqueante.recurso()).isEqualTo("empleados");
        assertThat(bloqueante.granja()).isNull();
        assertThat(bloqueante.excedente()).isEqualTo(3);
    }

    @Test
    @DisplayName("destino DEMO: los empleados excedentes son advertencia, no bloqueante (RD-P6.b.4)")
    void destinoDemo_empleadosSonAdvertencia() {
        duenoConPlan(PlanSuscripcion.BUSINESS);
        when(usuarioRepository.countByUsuarioDuenoIdAndActivoComoEmpleadoTrue(ID_DUENO))
                .thenReturn(5L); // límite DEMO = 2
        when(granjaRepository.findByUsuarioIdAndActivaTrueOrderByNombreGranjaAsc(ID_DUENO))
                .thenReturn(List.of());
        when(suscripcionRepository.findByIdUsuario(ID_DUENO)).thenReturn(Optional.empty());

        var r = servicio.impacto(ID_DUENO, PlanSuscripcion.DEMO);

        assertThat(r.bloqueantes()).isEmpty();
        assertThat(r.advertencias())
                .anySatisfy(a -> {
                    assertThat(a.recurso()).isEqualTo("empleados");
                    assertThat(a.excedente()).isEqualTo(3);
                });
    }

    @Test
    @DisplayName("datos por granja en sobre-límite → advertencias con nombre de granja (RD-P6.a)")
    void datosEnSobreLimite_sonAdvertenciasPorGranja() {
        duenoConPlan(PlanSuscripcion.BUSINESS);
        when(usuarioRepository.countByUsuarioDuenoIdAndActivoComoEmpleadoTrue(ID_DUENO))
                .thenReturn(1L);
        when(granjaRepository.findByUsuarioIdAndActivaTrueOrderByNombreGranjaAsc(ID_DUENO))
                .thenReturn(List.of(granja("g1", "Granja Norte"), granja("g2", "Granja Sur")));
        when(suscripcionRepository.findByIdUsuario(ID_DUENO)).thenReturn(Optional.empty());
        granjaSinExcesos("g1");
        granjaSinExcesos("g2");
        // Solo Granja Sur excede materias primas para STARTER (límite 30).
        when(materiaPrimaRepository.countByGranjaIdAndActivaTrue("g2")).thenReturn(45L);

        var r = servicio.impacto(ID_DUENO, PlanSuscripcion.STARTER);

        assertThat(r.bloqueantes()).isEmpty();
        assertThat(r.advertencias()).hasSize(1);
        var advertencia = r.advertencias().get(0);
        assertThat(advertencia.recurso()).isEqualTo("materiasPrimas");
        assertThat(advertencia.granja()).isEqualTo("Granja Sur");
        assertThat(advertencia.cantidadActual()).isEqualTo(45);
        assertThat(advertencia.limiteDestino()).isEqualTo(30);
        assertThat(advertencia.excedente()).isEqualTo(15);
    }

    @Test
    @DisplayName("upgrade sin excesos: sin bloqueantes ni advertencias, aplica desde ahora")
    void upgrade_limpioAplicaYa() {
        duenoConPlan(PlanSuscripcion.STARTER);
        when(usuarioRepository.countByUsuarioDuenoIdAndActivoComoEmpleadoTrue(ID_DUENO))
                .thenReturn(2L); // dentro del límite BUSINESS (10)
        when(granjaRepository.findByUsuarioIdAndActivaTrueOrderByNombreGranjaAsc(ID_DUENO))
                .thenReturn(List.of());

        var antes = Instant.now();
        var r = servicio.impacto(ID_DUENO, PlanSuscripcion.BUSINESS);

        assertThat(r.tipoCambio()).isEqualTo(TipoCambio.UPGRADE);
        assertThat(r.aplicaDesde()).isBetween(antes, Instant.now());
        assertThat(r.bloqueantes()).isEmpty();
        assertThat(r.advertencias()).isEmpty();
    }
}
