package com.reforma.domain.proveedores.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.reforma.domain.common.domain.PlanSuscripcion;
import com.reforma.domain.granjas.entity.Granja;
import com.reforma.domain.granjas.repository.GranjaRepository;
import com.reforma.domain.granjas.service.GranjaAccesoService;
import com.reforma.domain.proveedores.dto.ProveedorRequest;
import com.reforma.domain.proveedores.dto.ProveedorResponse;
import com.reforma.domain.proveedores.entity.Proveedor;
import com.reforma.domain.proveedores.repository.ProveedorRepository;
import com.reforma.domain.testsupport.EntidadConIdMocks;
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
 * Tests unitarios del servicio de Proveedores (RF-PROV-001 / 002).
 * Espeja la estructura de {@code MateriaPrimaServiceTest}.
 */
@ExtendWith(MockitoExtension.class)
class ProveedorServiceTest {

    private static final String ID_USUARIO = "u_demo";
    private static final String ID_GRANJA = "g_demo";

    @Mock private ProveedorRepository proveedorRepository;
    @Mock private GranjaRepository granjaRepository;
    @Mock private GranjaAccesoService granjaAccesoService;
    @Mock private PlanService planService;

    @InjectMocks private ProveedorService proveedorService;

    private Granja granjaDemo;

    @BeforeEach
    void setUp() {
        EntidadConIdMocks.reiniciarSecuencia();
        granjaDemo = Granja.builder().id(ID_GRANJA).nombreGranja("Granja Demo").activa(true).build();
    }

    // ---------- crear() ----------

    @Test
    @DisplayName("crear: happy path inserta fila nueva con campos normalizados")
    void crear_happyPath() {
        var request = new ProveedorRequest(
                "  P001  ", "  Acopio del Sur SA  ",
                " 351-555-0001 ", "ventas@acopio.com ", "30-12345678-9",
                "Ruta 9 km 600", "Córdoba", "  ");
        configurarPlanBusinessSinUsoActual();
        when(proveedorRepository
                        .existsByGranjaIdAndCodigoProveedorIgnoreCaseAndActivoTrue(ID_GRANJA, "P001"))
                .thenReturn(false);
        when(granjaRepository.findById(ID_GRANJA)).thenReturn(Optional.of(granjaDemo));
        when(proveedorRepository.save(any(Proveedor.class)))
                .thenAnswer(EntidadConIdMocks.asignarIdAlGuardar(Proveedor.class, Proveedor::setId));

        ProveedorResponse response = proveedorService.crear(ID_USUARIO, ID_GRANJA, request);

        assertThat(response.id()).isEqualTo(1L);
        assertThat(response.codigoProveedor()).isEqualTo("P001");
        assertThat(response.nombreProveedor()).isEqualTo("Acopio del Sur SA");
        assertThat(response.telefono()).isEqualTo("351-555-0001");
        assertThat(response.email()).isEqualTo("ventas@acopio.com");
        assertThat(response.cuit()).isEqualTo("30-12345678-9");
        assertThat(response.notas()).isNull();
        assertThat(response.activo()).isTrue();
        verify(granjaAccesoService).validarAcceso(ID_USUARIO, ID_GRANJA);
    }

    @Test
    @DisplayName("crear: si existe ACTIVO con el mismo código → 409")
    void crear_codigoDuplicadoActivo() {
        var request = nuevoRequestMinimo("P001", "Acopio del Sur SA");
        configurarPlanBusinessSinUsoActual();
        when(proveedorRepository
                        .existsByGranjaIdAndCodigoProveedorIgnoreCaseAndActivoTrue(ID_GRANJA, "P001"))
                .thenReturn(true);

        assertThatThrownBy(() -> proveedorService.crear(ID_USUARIO, ID_GRANJA, request))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.CONFLICT));

        verify(proveedorRepository, never()).save(any());
    }

    @Test
    @DisplayName("crear: ADR 0005 — si solo hay INACTIVOS con ese código, inserta fila nueva")
    void crear_reusaCodigoDeBajaLogica() {
        configurarPlanBusinessSinUsoActual();
        when(proveedorRepository
                        .existsByGranjaIdAndCodigoProveedorIgnoreCaseAndActivoTrue(ID_GRANJA, "P001"))
                .thenReturn(false);
        when(granjaRepository.findById(ID_GRANJA)).thenReturn(Optional.of(granjaDemo));
        when(proveedorRepository.save(any(Proveedor.class)))
                .thenAnswer(inv -> {
                    Proveedor p = inv.getArgument(0);
                    p.setId(2L);
                    return p;
                });
        var request = new ProveedorRequest(
                "P001", "Acopio Renovado SA", "351-555-9999", "nuevo@acopio.com",
                null, null, "Río Cuarto", null);

        ProveedorResponse response = proveedorService.crear(ID_USUARIO, ID_GRANJA, request);

        assertThat(response.nombreProveedor()).isEqualTo("Acopio Renovado SA");
        assertThat(response.activo()).isTrue();
        assertThat(response.id()).isEqualTo(2L);
        verify(proveedorRepository).save(any(Proveedor.class));
    }

    @Test
    @DisplayName("crear: usuario sin acceso a la granja → 403 propagado")
    void crear_sinAccesoGranja() {
        var request = nuevoRequestMinimo("P001", "Acopio del Sur SA");
        doThrow(new ResponseStatusException(HttpStatus.FORBIDDEN, "Sin acceso"))
                .when(granjaAccesoService)
                .validarAcceso(ID_USUARIO, ID_GRANJA);

        assertThatThrownBy(() -> proveedorService.crear(ID_USUARIO, ID_GRANJA, request))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.FORBIDDEN));

        verify(planService, never()).obtenerPlanEfectivo(anyString());
        verify(proveedorRepository, never()).save(any());
    }

    @Test
    @DisplayName("crear: alcanzado el límite DEMO (5) la siguiente alta da 403")
    void crear_limitePlanDemoExcedido() {
        var request = nuevoRequestMinimo("P006", "Sexto proveedor");
        when(planService.obtenerPlanEfectivo(ID_USUARIO)).thenReturn(PlanSuscripcion.DEMO);
        when(planService.limiteProveedores(PlanSuscripcion.DEMO)).thenReturn(5);
        when(proveedorRepository.countByGranjaIdAndActivoTrue(ID_GRANJA)).thenReturn(5L);

        assertThatThrownBy(() -> proveedorService.crear(ID_USUARIO, ID_GRANJA, request))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("DEMO")
                .hasMessageContaining("5")
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.FORBIDDEN));

        verify(proveedorRepository, never()).save(any());
    }

    // ---------- actualizar() ----------

    @Test
    @DisplayName("actualizar: mismo código (case-insensitive) no chequea unicidad")
    void actualizar_mismoCodigo() {
        Proveedor existente = proveedorExistente("P001", "Acopio");
        when(proveedorRepository.findByIdAndGranjaId(existente.getId(), ID_GRANJA))
                .thenReturn(Optional.of(existente));
        // Mismo código pero diferente casing.
        var request = nuevoRequestMinimo("p001", "Acopio Nuevo");

        ProveedorResponse response =
                proveedorService.actualizar(ID_USUARIO, ID_GRANJA, existente.getId(), request);

        assertThat(response.nombreProveedor()).isEqualTo("Acopio Nuevo");
        verify(proveedorRepository, never())
                .existsByGranjaIdAndCodigoProveedorIgnoreCaseAndActivoTrue(anyString(), anyString());
    }

    @Test
    @DisplayName("actualizar: cambiar a código ya usado por otro ACTIVO → 409")
    void actualizar_codigoDuplicado() {
        Proveedor existente = proveedorExistente("P001", "Acopio");
        when(proveedorRepository.findByIdAndGranjaId(existente.getId(), ID_GRANJA))
                .thenReturn(Optional.of(existente));
        when(proveedorRepository
                        .existsByGranjaIdAndCodigoProveedorIgnoreCaseAndActivoTrue(ID_GRANJA, "P002"))
                .thenReturn(true);
        var request = nuevoRequestMinimo("P002", "Acopio");

        assertThatThrownBy(() -> proveedorService.actualizar(
                        ID_USUARIO, ID_GRANJA, existente.getId(), request))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.CONFLICT));
    }

    @Test
    @DisplayName("actualizar: proveedor inexistente → 404")
    void actualizar_inexistente() {
        when(proveedorRepository.findByIdAndGranjaId(eq(999L), eq(ID_GRANJA)))
                .thenReturn(Optional.empty());
        var request = nuevoRequestMinimo("P001", "Acopio");

        assertThatThrownBy(() -> proveedorService.actualizar(ID_USUARIO, ID_GRANJA, 999L, request))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.NOT_FOUND));
    }

    // ---------- desactivar() ----------

    @Test
    @DisplayName("desactivar: marca activo=false sin borrar (baja lógica)")
    void desactivar_marcaInactivo() {
        Proveedor existente = proveedorExistente("P001", "Acopio");
        when(proveedorRepository.findByIdAndGranjaId(existente.getId(), ID_GRANJA))
                .thenReturn(Optional.of(existente));

        proveedorService.desactivar(ID_USUARIO, ID_GRANJA, existente.getId());

        assertThat(existente.getActivo()).isFalse();
        verify(proveedorRepository, never()).delete(any(Proveedor.class));
    }

    // ---------- listarPorGranja() ----------

    @Test
    @DisplayName("listar sin buscar: invoca el método sin filtro de nombre")
    void listar_sinBuscar() {
        when(proveedorRepository.findByGranjaIdAndActivoTrueOrderByNombreProveedorAsc(ID_GRANJA))
                .thenReturn(List.of());

        proveedorService.listarPorGranja(ID_USUARIO, ID_GRANJA, null);

        verify(granjaAccesoService).validarAcceso(ID_USUARIO, ID_GRANJA);
        verify(proveedorRepository).findByGranjaIdAndActivoTrueOrderByNombreProveedorAsc(ID_GRANJA);
    }

    @Test
    @DisplayName("listar con buscar: invoca el método con filtro case-insensitive")
    void listar_conBuscar() {
        when(proveedorRepository
                        .findByGranjaIdAndActivoTrueAndNombreProveedorContainingIgnoreCaseOrderByNombreProveedorAsc(
                                ID_GRANJA, "aco"))
                .thenReturn(List.of());

        proveedorService.listarPorGranja(ID_USUARIO, ID_GRANJA, "  aco  ");

        verify(proveedorRepository)
                .findByGranjaIdAndActivoTrueAndNombreProveedorContainingIgnoreCaseOrderByNombreProveedorAsc(
                        ID_GRANJA, "aco");
        verify(proveedorRepository, never())
                .findByGranjaIdAndActivoTrueOrderByNombreProveedorAsc(anyString());
    }

    // ---------- helpers ----------

    private void configurarPlanBusinessSinUsoActual() {
        when(planService.obtenerPlanEfectivo(ID_USUARIO)).thenReturn(PlanSuscripcion.BUSINESS);
        when(planService.limiteProveedores(PlanSuscripcion.BUSINESS)).thenReturn(50);
        when(proveedorRepository.countByGranjaIdAndActivoTrue(ID_GRANJA)).thenReturn(0L);
    }

    private static ProveedorRequest nuevoRequestMinimo(String codigo, String nombre) {
        return new ProveedorRequest(codigo, nombre, null, null, null, null, null, null);
    }

    private Proveedor proveedorExistente(String codigo, String nombre) {
        Instant now = Instant.now();
        return Proveedor.builder()
                .id(1L)
                .granja(granjaDemo)
                .codigoProveedor(codigo)
                .nombreProveedor(nombre)
                .activo(true)
                .fechaCreacion(now)
                .fechaUltimaActualizacion(now)
                .build();
    }
}
