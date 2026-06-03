package com.reforma.domain.animales.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.reforma.domain.animales.dto.AnimalRequest;
import com.reforma.domain.animales.dto.AnimalResponse;
import com.reforma.domain.animales.entity.Animal;
import com.reforma.domain.animales.repository.AnimalRepository;
import com.reforma.domain.testsupport.EntidadConIdMocks;
import com.reforma.domain.common.domain.PlanSuscripcion;
import com.reforma.domain.granjas.entity.Granja;
import com.reforma.domain.granjas.repository.GranjaRepository;
import com.reforma.domain.granjas.service.GranjaAccesoService;
import com.reforma.domain.suscripciones.service.PlanService;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/**
 * Tests unitarios del catálogo de Animales (RF-ANI-001/002). Espeja el patrón de
 * {@code ProveedorServiceTest} y aplica la política ADR 0005 de soft-delete.
 */
@ExtendWith(MockitoExtension.class)
class AnimalServiceTest {

    private static final String ID_USUARIO = "u_demo";
    private static final String ID_GRANJA = "g_demo";

    @Mock private AnimalRepository animalRepository;
    @Mock private GranjaRepository granjaRepository;
    @Mock private GranjaAccesoService granjaAccesoService;
    @Mock private PlanService planService;

    @InjectMocks private AnimalService animalService;

    private Granja granjaDemo;

    @BeforeEach
    void setUp() {
        EntidadConIdMocks.reiniciarSecuencia();
        granjaDemo = Granja.builder().id(ID_GRANJA).nombreGranja("Granja Demo").activa(true).build();
    }

    // ---------- crear() ----------

    @Test
    @DisplayName("crear: happy path inserta fila nueva trimando y normalizando opcionales")
    void crear_happyPath() {
        var request = new AnimalRequest(
                "  CAT01  ", "  Cerda gestante  ", "  Reproductora  ", "  ");
        configurarPlanBusinessSinUsoActual();
        when(animalRepository.existsByGranjaIdAndCodigoAnimalIgnoreCaseAndActivoTrue(ID_GRANJA, "CAT01"))
                .thenReturn(false);
        when(granjaRepository.findById(ID_GRANJA)).thenReturn(Optional.of(granjaDemo));
        when(animalRepository.save(any(Animal.class)))
                .thenAnswer(EntidadConIdMocks.asignarIdAlGuardar(Animal.class, Animal::setId));

        AnimalResponse response = animalService.crear(ID_USUARIO, ID_GRANJA, request);

        assertThat(response.id()).isEqualTo(1L);
        assertThat(response.codigoAnimal()).isEqualTo("CAT01");
        assertThat(response.descripcionAnimal()).isEqualTo("Cerda gestante");
        assertThat(response.categoriaAnimal()).isEqualTo("Reproductora");
        assertThat(response.observaciones()).isNull(); // string vacío → null
        assertThat(response.activo()).isTrue();
        verify(granjaAccesoService).validarAcceso(ID_USUARIO, ID_GRANJA);
    }

    @Test
    @DisplayName("crear: si existe ACTIVO con el mismo código → 409 sin save")
    void crear_codigoDuplicadoActivo() {
        var request = nuevoRequestMinimo("CAT01", "Cerda gestante");
        configurarPlanBusinessSinUsoActual();
        when(animalRepository.existsByGranjaIdAndCodigoAnimalIgnoreCaseAndActivoTrue(ID_GRANJA, "CAT01"))
                .thenReturn(true);

        assertThatThrownBy(() -> animalService.crear(ID_USUARIO, ID_GRANJA, request))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.CONFLICT));

        verify(animalRepository, never()).save(any());
    }

    @Test
    @DisplayName("crear: ADR 0005 — si solo hay INACTIVOS con ese código, inserta fila nueva")
    void crear_reusaCodigoDeBajaLogica() {
        configurarPlanBusinessSinUsoActual();
        when(animalRepository.existsByGranjaIdAndCodigoAnimalIgnoreCaseAndActivoTrue(ID_GRANJA, "CAT01"))
                .thenReturn(false);
        when(granjaRepository.findById(ID_GRANJA)).thenReturn(Optional.of(granjaDemo));
        when(animalRepository.save(any(Animal.class)))
                .thenAnswer(inv -> {
                    Animal a = inv.getArgument(0);
                    a.setId(2L);
                    return a;
                });
        var request = new AnimalRequest(
                "CAT01", "Cerda gestante v2", "Reproductora", "Notas nuevas");

        AnimalResponse response = animalService.crear(ID_USUARIO, ID_GRANJA, request);

        assertThat(response.descripcionAnimal()).isEqualTo("Cerda gestante v2");
        assertThat(response.observaciones()).isEqualTo("Notas nuevas");
        assertThat(response.activo()).isTrue();
        assertThat(response.id()).isEqualTo(2L);
        verify(animalRepository).save(any(Animal.class));
    }

    @Test
    @DisplayName("crear: usuario sin acceso a la granja → 403 propagado sin tocar repo")
    void crear_sinAccesoGranja() {
        var request = nuevoRequestMinimo("CAT01", "Cerda gestante");
        doThrow(new ResponseStatusException(HttpStatus.FORBIDDEN, "Sin acceso"))
                .when(granjaAccesoService)
                .validarAcceso(ID_USUARIO, ID_GRANJA);

        assertThatThrownBy(() -> animalService.crear(ID_USUARIO, ID_GRANJA, request))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.FORBIDDEN));

        verify(planService, never()).obtenerPlanEfectivo(anyString());
        verify(animalRepository, never()).save(any());
    }

    @Test
    @DisplayName("crear: alcanzado el límite DEMO (5) la siguiente alta da 403")
    void crear_limitePlanDemoExcedido() {
        var request = nuevoRequestMinimo("CAT06", "Sexto animal");
        when(planService.obtenerPlanEfectivo(ID_USUARIO)).thenReturn(PlanSuscripcion.DEMO);
        when(planService.limiteAnimales(PlanSuscripcion.DEMO)).thenReturn(5);
        when(animalRepository.countByGranjaIdAndActivoTrue(ID_GRANJA)).thenReturn(5L);

        assertThatThrownBy(() -> animalService.crear(ID_USUARIO, ID_GRANJA, request))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("DEMO")
                .hasMessageContaining("5")
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.FORBIDDEN));

        verify(animalRepository, never()).save(any());
    }

    // ---------- actualizar() ----------

    @Test
    @DisplayName("actualizar: mismo código (case-insensitive) no chequea unicidad")
    void actualizar_mismoCodigo() {
        Animal existente = animalExistente("CAT01", "Cerda gestante");
        when(animalRepository.findByIdAndGranjaId(existente.getId(), ID_GRANJA))
                .thenReturn(Optional.of(existente));
        var request = new AnimalRequest("cat01", "Descripción nueva", null, null);

        AnimalResponse response = animalService.actualizar(
                ID_USUARIO, ID_GRANJA, existente.getId(), request);

        assertThat(response.descripcionAnimal()).isEqualTo("Descripción nueva");
        verify(animalRepository, never())
                .existsByGranjaIdAndCodigoAnimalIgnoreCaseAndActivoTrue(anyString(), anyString());
    }

    @Test
    @DisplayName("actualizar: cambiar a código ya usado por otro ACTIVO → 409")
    void actualizar_codigoDuplicado() {
        Animal existente = animalExistente("CAT01", "Cerda gestante");
        when(animalRepository.findByIdAndGranjaId(existente.getId(), ID_GRANJA))
                .thenReturn(Optional.of(existente));
        when(animalRepository.existsByGranjaIdAndCodigoAnimalIgnoreCaseAndActivoTrue(ID_GRANJA, "CAT02"))
                .thenReturn(true);
        var request = nuevoRequestMinimo("CAT02", "Renombrado");

        assertThatThrownBy(() -> animalService.actualizar(
                        ID_USUARIO, ID_GRANJA, existente.getId(), request))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.CONFLICT));
    }

    @Test
    @DisplayName("actualizar: animal inexistente → 404")
    void actualizar_inexistente() {
        when(animalRepository.findByIdAndGranjaId(eq(999L), eq(ID_GRANJA)))
                .thenReturn(Optional.empty());
        var request = nuevoRequestMinimo("CAT01", "Cerda gestante");

        assertThatThrownBy(() -> animalService.actualizar(ID_USUARIO, ID_GRANJA, 999L, request))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.NOT_FOUND));
    }

    // ---------- desactivar() ----------

    @Test
    @DisplayName("desactivar: marca activo=false sin borrar (baja lógica)")
    void desactivar_marcaInactivo() {
        Animal existente = animalExistente("CAT01", "Cerda gestante");
        when(animalRepository.findByIdAndGranjaId(existente.getId(), ID_GRANJA))
                .thenReturn(Optional.of(existente));

        animalService.desactivar(ID_USUARIO, ID_GRANJA, existente.getId());

        assertThat(existente.getActivo()).isFalse();
        verify(animalRepository, never()).delete(any(Animal.class));
    }

    // ---------- listar() ----------

    @Test
    @DisplayName("listar sin buscar: invoca el método sin filtro de descripción")
    void listar_sinBuscar() {
        when(animalRepository.findByGranjaIdAndActivoTrueOrderByDescripcionAnimalAsc(ID_GRANJA))
                .thenReturn(List.of());

        animalService.listarPorGranja(ID_USUARIO, ID_GRANJA, null);

        verify(granjaAccesoService).validarAcceso(ID_USUARIO, ID_GRANJA);
        verify(animalRepository).findByGranjaIdAndActivoTrueOrderByDescripcionAnimalAsc(ID_GRANJA);
    }

    @Test
    @DisplayName("listar con buscar: invoca el método con filtro case-insensitive (trimado)")
    void listar_conBuscar() {
        when(animalRepository
                        .findByGranjaIdAndActivoTrueAndDescripcionAnimalContainingIgnoreCaseOrderByDescripcionAnimalAsc(
                                ID_GRANJA, "cerda"))
                .thenReturn(List.of());

        animalService.listarPorGranja(ID_USUARIO, ID_GRANJA, "  cerda  ");

        verify(animalRepository)
                .findByGranjaIdAndActivoTrueAndDescripcionAnimalContainingIgnoreCaseOrderByDescripcionAnimalAsc(
                        ID_GRANJA, "cerda");
        verify(animalRepository, never())
                .findByGranjaIdAndActivoTrueOrderByDescripcionAnimalAsc(anyString());
    }

    // ---------- helpers ----------

    private void configurarPlanBusinessSinUsoActual() {
        when(planService.obtenerPlanEfectivo(ID_USUARIO)).thenReturn(PlanSuscripcion.BUSINESS);
        when(planService.limiteAnimales(PlanSuscripcion.BUSINESS)).thenReturn(100);
        when(animalRepository.countByGranjaIdAndActivoTrue(ID_GRANJA)).thenReturn(0L);
    }

    private static AnimalRequest nuevoRequestMinimo(String codigo, String descripcion) {
        return new AnimalRequest(codigo, descripcion, null, null);
    }

    private Animal animalExistente(String codigo, String descripcion) {
        Instant now = Instant.now();
        return Animal.builder()
                .id(1L)
                .granja(granjaDemo)
                .codigoAnimal(codigo)
                .descripcionAnimal(descripcion)
                .activo(true)
                .fechaCreacion(now)
                .fechaUltimaActualizacion(now)
                .build();
    }
}
