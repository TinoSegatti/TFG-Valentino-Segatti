package com.reforma.domain.materiasprimas.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.reforma.domain.common.domain.PlanSuscripcion;
import com.reforma.domain.granjas.entity.Granja;
import com.reforma.domain.granjas.repository.GranjaRepository;
import com.reforma.domain.granjas.service.GranjaAccesoService;
import com.reforma.domain.materiasprimas.dto.MateriaPrimaRequest;
import com.reforma.domain.materiasprimas.dto.MateriaPrimaResponse;
import com.reforma.domain.materiasprimas.entity.MateriaPrima;
import com.reforma.domain.materiasprimas.repository.MateriaPrimaRepository;
import com.reforma.domain.testsupport.EntidadConIdMocks;
import com.reforma.domain.suscripciones.service.PlanService;
import java.time.Instant;
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
 * Tests unitarios del servicio de Materias Primas con repositorios y servicios colaboradores
 * mockeados. Cubre RF-MP-001 (alta), RF-MP-002 (baja lógica), RF-MP-003 (plan-gating),
 * RF-GRN-005 (multi-tenancy: el usuario debe tener acceso a la granja).
 */
@ExtendWith(MockitoExtension.class)
class MateriaPrimaServiceTest {

    private static final String ID_USUARIO = "u_demo";
    private static final String ID_GRANJA = "g_demo";

    @Mock private MateriaPrimaRepository materiaPrimaRepository;
    @Mock private GranjaRepository granjaRepository;
    @Mock private GranjaAccesoService granjaAccesoService;
    @Mock private PlanService planService;

    @InjectMocks private MateriaPrimaService materiaPrimaService;

    private Granja granjaDemo;

    @BeforeEach
    void setUp() {
        EntidadConIdMocks.reiniciarSecuencia();
        granjaDemo = Granja.builder().id(ID_GRANJA).nombreGranja("Granja Demo").activa(true).build();
    }

    // ---------- crear() ----------

    @Test
    @DisplayName("crear: happy path inserta fila nueva y devuelve el response")
    void crear_happyPath() {
        var request = new MateriaPrimaRequest("MAIZ", "Maíz molido", 35.50);
        configurarPlanBusinessSinUsoActual();
        when(materiaPrimaRepository
                        .existsByGranjaIdAndCodigoMateriaPrimaIgnoreCaseAndActivaTrue(ID_GRANJA, "MAIZ"))
                .thenReturn(false);
        when(granjaRepository.findById(ID_GRANJA)).thenReturn(Optional.of(granjaDemo));
        when(materiaPrimaRepository.save(any(MateriaPrima.class)))
                .thenAnswer(EntidadConIdMocks.asignarIdAlGuardar(MateriaPrima.class, MateriaPrima::setId));

        MateriaPrimaResponse response = materiaPrimaService.crear(ID_USUARIO, ID_GRANJA, request);

        assertThat(response.id()).isEqualTo(1L);
        assertThat(response.codigoMateriaPrima()).isEqualTo("MAIZ");
        assertThat(response.nombreMateriaPrima()).isEqualTo("Maíz molido");
        assertThat(response.precioPorKilo()).isEqualTo(35.50);
        assertThat(response.activa()).isTrue();
        assertThat(response.idGranja()).isEqualTo(ID_GRANJA);
        verify(granjaAccesoService).validarAcceso(ID_USUARIO, ID_GRANJA);
        verify(materiaPrimaRepository).save(any(MateriaPrima.class));
    }

    @Test
    @DisplayName("crear: trimea código y nombre antes de validar/persistir")
    void crear_aplicaTrim() {
        var request = new MateriaPrimaRequest("  MAIZ  ", "  Maíz molido  ", 35.50);
        configurarPlanBusinessSinUsoActual();
        when(materiaPrimaRepository
                        .existsByGranjaIdAndCodigoMateriaPrimaIgnoreCaseAndActivaTrue(ID_GRANJA, "MAIZ"))
                .thenReturn(false);
        when(granjaRepository.findById(ID_GRANJA)).thenReturn(Optional.of(granjaDemo));
        when(materiaPrimaRepository.save(any(MateriaPrima.class)))
                .thenAnswer(EntidadConIdMocks.asignarIdAlGuardar(MateriaPrima.class, MateriaPrima::setId));

        MateriaPrimaResponse response = materiaPrimaService.crear(ID_USUARIO, ID_GRANJA, request);

        assertThat(response.id()).isNotNull();
        assertThat(response.codigoMateriaPrima()).isEqualTo("MAIZ");
        assertThat(response.nombreMateriaPrima()).isEqualTo("Maíz molido");
    }

    @Test
    @DisplayName("crear: si existe ACTIVA con el mismo código → 409 CONFLICT y no persiste")
    void crear_codigoDuplicadoActiva() {
        var request = new MateriaPrimaRequest("MAIZ", "Maíz molido", 35.50);
        configurarPlanBusinessSinUsoActual();
        when(materiaPrimaRepository
                        .existsByGranjaIdAndCodigoMateriaPrimaIgnoreCaseAndActivaTrue(ID_GRANJA, "MAIZ"))
                .thenReturn(true);

        assertThatThrownBy(() -> materiaPrimaService.crear(ID_USUARIO, ID_GRANJA, request))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("MAIZ")
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.CONFLICT));

        verify(materiaPrimaRepository, never()).save(any());
    }

    @Test
    @DisplayName("crear: ADR 0005 — si solo hay INACTIVAS con ese código, inserta fila nueva y no toca la vieja")
    void crear_reusaCodigoDeBajaLogica() {
        var request = new MateriaPrimaRequest("MAIZ", "Maíz molido v2", 40.0);
        configurarPlanBusinessSinUsoActual();
        // No hay activas con ese código → exists devuelve false aunque haya inactivas.
        when(materiaPrimaRepository
                        .existsByGranjaIdAndCodigoMateriaPrimaIgnoreCaseAndActivaTrue(ID_GRANJA, "MAIZ"))
                .thenReturn(false);
        when(granjaRepository.findById(ID_GRANJA)).thenReturn(Optional.of(granjaDemo));
        when(materiaPrimaRepository.save(any(MateriaPrima.class)))
                .thenAnswer(inv -> {
                    MateriaPrima mp = inv.getArgument(0);
                    mp.setId(2L);
                    return mp;
                });

        MateriaPrimaResponse response = materiaPrimaService.crear(ID_USUARIO, ID_GRANJA, request);

        // Se persiste una entidad nueva; el id lo asigna la BD (mock: 2L).
        assertThat(response.codigoMateriaPrima()).isEqualTo("MAIZ");
        assertThat(response.nombreMateriaPrima()).isEqualTo("Maíz molido v2");
        assertThat(response.precioPorKilo()).isEqualTo(40.0);
        assertThat(response.activa()).isTrue();
        assertThat(response.id()).isEqualTo(2L);
        verify(materiaPrimaRepository).save(any(MateriaPrima.class));
    }

    @Test
    @DisplayName("crear: usuario sin acceso a la granja → 403 propagado por GranjaAccesoService")
    void crear_sinAccesoGranja() {
        var request = new MateriaPrimaRequest("MAIZ", "Maíz molido", 35.50);
        doThrow(new ResponseStatusException(HttpStatus.FORBIDDEN, "Sin acceso"))
                .when(granjaAccesoService)
                .validarAcceso(ID_USUARIO, ID_GRANJA);

        assertThatThrownBy(() -> materiaPrimaService.crear(ID_USUARIO, ID_GRANJA, request))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.FORBIDDEN));

        verify(planService, never()).obtenerPlanEfectivo(anyString());
        verify(materiaPrimaRepository, never()).save(any());
    }

    @Test
    @DisplayName("crear: al alcanzar el límite del plan DEMO (10) la siguiente alta da 403")
    void crear_limitePlanDemoExcedido() {
        var request = new MateriaPrimaRequest("MAIZ", "Maíz molido", 35.50);
        when(planService.obtenerPlanEfectivo(ID_USUARIO)).thenReturn(PlanSuscripcion.DEMO);
        when(planService.limiteMateriasPrimas(PlanSuscripcion.DEMO)).thenReturn(10);
        when(materiaPrimaRepository.countByGranjaIdAndActivaTrue(ID_GRANJA)).thenReturn(10L);

        assertThatThrownBy(() -> materiaPrimaService.crear(ID_USUARIO, ID_GRANJA, request))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("DEMO")
                .hasMessageContaining("10")
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.FORBIDDEN));

        verify(materiaPrimaRepository, never()).save(any());
    }

    @Test
    @DisplayName("crear: plan ENTERPRISE no impone límite aunque ya haya muchas MPs")
    void crear_planEnterpriseSinLimite() {
        var request = new MateriaPrimaRequest("NEW", "Nueva MP", 10.0);
        when(planService.obtenerPlanEfectivo(ID_USUARIO)).thenReturn(PlanSuscripcion.ENTERPRISE);
        when(planService.limiteMateriasPrimas(PlanSuscripcion.ENTERPRISE)).thenReturn(Integer.MAX_VALUE);
        when(materiaPrimaRepository.countByGranjaIdAndActivaTrue(ID_GRANJA)).thenReturn(5_000L);
        when(materiaPrimaRepository
                        .existsByGranjaIdAndCodigoMateriaPrimaIgnoreCaseAndActivaTrue(ID_GRANJA, "NEW"))
                .thenReturn(false);
        when(granjaRepository.findById(ID_GRANJA)).thenReturn(Optional.of(granjaDemo));
        when(materiaPrimaRepository.save(any(MateriaPrima.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        var response = materiaPrimaService.crear(ID_USUARIO, ID_GRANJA, request);

        assertThat(response.codigoMateriaPrima()).isEqualTo("NEW");
    }

    // ---------- actualizar() ----------

    @Test
    @DisplayName("actualizar: cambiar precio sin tocar código no chequea unicidad")
    void actualizar_soloPrecio() {
        MateriaPrima existente = materiaPrimaExistente("MAIZ", "Maíz molido", 30.0);
        when(materiaPrimaRepository.findByIdAndGranjaId(existente.getId(), ID_GRANJA))
                .thenReturn(Optional.of(existente));
        var request = new MateriaPrimaRequest("MAIZ", "Maíz molido", 42.0);

        MateriaPrimaResponse response =
                materiaPrimaService.actualizar(ID_USUARIO, ID_GRANJA, existente.getId(), request);

        assertThat(response.precioPorKilo()).isEqualTo(42.0);
        verify(materiaPrimaRepository, never())
                .existsByGranjaIdAndCodigoMateriaPrimaIgnoreCaseAndActivaTrue(anyString(), anyString());
    }

    @Test
    @DisplayName("actualizar: cambiar código a uno ya usado por otra MP ACTIVA → 409 CONFLICT")
    void actualizar_codigoDuplicado() {
        MateriaPrima existente = materiaPrimaExistente("MAIZ", "Maíz molido", 30.0);
        when(materiaPrimaRepository.findByIdAndGranjaId(existente.getId(), ID_GRANJA))
                .thenReturn(Optional.of(existente));
        when(materiaPrimaRepository
                        .existsByGranjaIdAndCodigoMateriaPrimaIgnoreCaseAndActivaTrue(ID_GRANJA, "SOJA"))
                .thenReturn(true);
        var request = new MateriaPrimaRequest("SOJA", "Cambio nombre", 50.0);

        assertThatThrownBy(() -> materiaPrimaService.actualizar(
                        ID_USUARIO, ID_GRANJA, existente.getId(), request))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.CONFLICT));
    }

    @Test
    @DisplayName("actualizar: si la MP no existe en la granja → 404 NOT_FOUND")
    void actualizar_inexistente() {
        when(materiaPrimaRepository.findByIdAndGranjaId(eq(999L), eq(ID_GRANJA)))
                .thenReturn(Optional.empty());
        var request = new MateriaPrimaRequest("MAIZ", "Maíz molido", 35.50);

        assertThatThrownBy(() -> materiaPrimaService.actualizar(ID_USUARIO, ID_GRANJA, 999L, request))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.NOT_FOUND));
    }

    // ---------- desactivar() ----------

    @Test
    @DisplayName("desactivar: marca activa=false sin borrar la entidad (baja lógica)")
    void desactivar_marcaInactiva() {
        MateriaPrima existente = materiaPrimaExistente("MAIZ", "Maíz molido", 30.0);
        when(materiaPrimaRepository.findByIdAndGranjaId(existente.getId(), ID_GRANJA))
                .thenReturn(Optional.of(existente));

        materiaPrimaService.desactivar(ID_USUARIO, ID_GRANJA, existente.getId());

        assertThat(existente.getActiva()).isFalse();
        verify(materiaPrimaRepository, never()).delete(any(MateriaPrima.class));
        verify(materiaPrimaRepository, times(1)).findByIdAndGranjaId(existente.getId(), ID_GRANJA);
    }

    @Test
    @DisplayName("desactivar: MP inexistente → 404 NOT_FOUND")
    void desactivar_inexistente() {
        when(materiaPrimaRepository.findByIdAndGranjaId(eq(999L), eq(ID_GRANJA)))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> materiaPrimaService.desactivar(ID_USUARIO, ID_GRANJA, 999L))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.NOT_FOUND));
    }

    // ---------- listarPorGranja() ----------

    @Test
    @DisplayName("listar: aplica multi-tenant (valida acceso) y delega al repo")
    void listar_validaAcceso() {
        when(materiaPrimaRepository.findByGranjaIdAndActivaTrueOrderByNombreMateriaPrimaAsc(ID_GRANJA))
                .thenReturn(java.util.List.of());

        materiaPrimaService.listarPorGranja(ID_USUARIO, ID_GRANJA);

        verify(granjaAccesoService).validarAcceso(ID_USUARIO, ID_GRANJA);
        verify(materiaPrimaRepository).findByGranjaIdAndActivaTrueOrderByNombreMateriaPrimaAsc(ID_GRANJA);
    }

    // ---------- exportar / importar CSV (RF-MP-004) ----------

    @Test
    @DisplayName("exportarCsv: serializa header + filas activas en orden alfabético")
    void exportarCsv_serializaFilas() {
        when(materiaPrimaRepository.findByGranjaIdAndActivaTrueOrderByNombreMateriaPrimaAsc(ID_GRANJA))
                .thenReturn(java.util.List.of(
                        materiaPrimaExistente("MAIZ", "Maíz molido", 35.5),
                        materiaPrimaExistente("SOJA", "Soja", 40.0)));

        String csv = materiaPrimaService.exportarCsv(ID_USUARIO, ID_GRANJA);

        assertThat(csv).startsWith("codigo,nombre,precio_por_kilo\r\n");
        assertThat(csv).contains("MAIZ,Maíz molido,35.5\r\n");
        assertThat(csv).contains("SOJA,Soja,40.0\r\n");
        verify(granjaAccesoService).validarAcceso(ID_USUARIO, ID_GRANJA);
    }

    @Test
    @DisplayName("importarCsv: filas válidas se persisten, las inválidas se reportan sin abortar")
    void importarCsv_mezcla() {
        configurarPlanBusinessSinUsoActual();
        when(granjaRepository.findById(ID_GRANJA)).thenReturn(Optional.of(granjaDemo));
        when(materiaPrimaRepository
                        .existsByGranjaIdAndCodigoMateriaPrimaIgnoreCaseAndActivaTrue(
                                eq(ID_GRANJA), anyString()))
                .thenAnswer(inv -> "DUP".equalsIgnoreCase(inv.getArgument(1, String.class)));
        when(materiaPrimaRepository.save(any(MateriaPrima.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        String csv =
                "codigo,nombre,precio_por_kilo\n"
                        + "MAIZ,Maíz molido,35.5\n"
                        + ",Falta código,10\n"
                        + "DUP,Duplicado activo,20\n"
                        + "SOJA,Soja,40\n";

        com.reforma.domain.common.csv.CsvImportResult resultado =
                materiaPrimaService.importarCsv(
                        ID_USUARIO,
                        ID_GRANJA,
                        new java.io.ByteArrayInputStream(
                                csv.getBytes(java.nio.charset.StandardCharsets.UTF_8)));

        assertThat(resultado.filasOk()).isEqualTo(2);
        assertThat(resultado.filasError()).isEqualTo(2);
        assertThat(resultado.errores()).extracting("linea").containsExactly(3, 4);
        assertThat(resultado.errores().get(0).mensaje()).contains("codigo");
        assertThat(resultado.errores().get(1).mensaje()).contains("DUP");
        verify(materiaPrimaRepository, times(2)).save(any(MateriaPrima.class));
    }

    // ---------- helpers ----------

    /**
     * Configura el plan-gating como BUSINESS con cero materias primas activas,
     * útil para los happy-paths donde no se quiere chequear el límite.
     */
    private void configurarPlanBusinessSinUsoActual() {
        when(planService.obtenerPlanEfectivo(ID_USUARIO)).thenReturn(PlanSuscripcion.BUSINESS);
        when(planService.limiteMateriasPrimas(PlanSuscripcion.BUSINESS)).thenReturn(100);
        when(materiaPrimaRepository.countByGranjaIdAndActivaTrue(ID_GRANJA)).thenReturn(0L);
    }

    private MateriaPrima materiaPrimaExistente(String codigo, String nombre, double precio) {
        Instant now = Instant.now();
        return MateriaPrima.builder()
                .id(1L)
                .granja(granjaDemo)
                .codigoMateriaPrima(codigo)
                .nombreMateriaPrima(nombre)
                .precioPorKilo(precio)
                .activa(true)
                .fechaCreacion(now)
                .fechaUltimaActualizacion(now)
                .build();
    }
}
