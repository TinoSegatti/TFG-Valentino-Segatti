package com.reforma.domain.archivos.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.reforma.domain.archivos.domain.TipoModuloArchivo;
import com.reforma.domain.archivos.dto.ArchivoCrearRequest;
import com.reforma.domain.archivos.dto.ArchivoDetalleResponse;
import com.reforma.domain.archivos.dto.ArchivoResumenResponse;
import com.reforma.domain.archivos.entity.Archivo;
import com.reforma.domain.archivos.repository.ArchivoRepository;
import com.reforma.domain.archivos.support.ArchivoSnapshot;
import com.reforma.domain.archivos.support.ArchivoSnapshotProvider;
import com.reforma.domain.auditoria.domain.AccionAuditoria;
import com.reforma.domain.auditoria.dto.AuditoriaEvento;
import com.reforma.domain.auditoria.service.AuditoriaService;
import com.reforma.domain.common.domain.PlanSuscripcion;
import com.reforma.domain.granjas.service.GranjaAccesoService;
import com.reforma.domain.suscripciones.service.PlanService;
import com.reforma.domain.testsupport.EntidadConIdMocks;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/**
 * Tests unitarios del módulo Archivos: creación de snapshots inmutables (plan-gating,
 * unicidad de código por tipo, auditoría, multi-tenancy) y consulta (listado + detalle).
 */
@ExtendWith(MockitoExtension.class)
class ArchivoServiceTest {

    private static final String ID_TENANT = "u_demo";
    private static final String ID_CREADOR = "u_empleado";
    private static final String EMAIL_CREADOR = "empleado@reforma.local";
    private static final String ID_GRANJA = "g_demo";

    @Mock private ArchivoRepository archivoRepository;
    @Mock private GranjaAccesoService granjaAccesoService;
    @Mock private PlanService planService;
    @Mock private AuditoriaService auditoriaService;
    @Mock private ArchivoSnapshotProvider inventarioProvider;
    @Mock private ArchivoSnapshotProvider comprasProvider;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private ArchivoService archivoService;

    @BeforeEach
    void setUp() {
        EntidadConIdMocks.reiniciarSecuencia();
        when(inventarioProvider.tipo()).thenReturn(TipoModuloArchivo.INVENTARIO);
        when(comprasProvider.tipo()).thenReturn(TipoModuloArchivo.COMPRAS);
        archivoService = new ArchivoService(
                archivoRepository,
                granjaAccesoService,
                planService,
                auditoriaService,
                objectMapper,
                List.of(inventarioProvider, comprasProvider));
    }

    private void configurarPlanBusinessSinUsoActual() {
        when(planService.obtenerPlanEfectivo(ID_TENANT)).thenReturn(PlanSuscripcion.BUSINESS);
        when(planService.limiteArchivos(PlanSuscripcion.BUSINESS)).thenReturn(50);
        when(archivoRepository.countByIdGranja(ID_GRANJA)).thenReturn(0L);
    }

    private ArchivoResumenResponse crear(ArchivoCrearRequest request) {
        return archivoService.crear(ID_TENANT, ID_CREADOR, EMAIL_CREADOR, ID_GRANJA, request);
    }

    // ---------- crear() ----------

    @Test
    @DisplayName("crear: happy path captura el snapshot, persiste JSON y audita ARCHIVO_CREADO")
    void crear_happyPath() {
        var request = new ArchivoCrearRequest(
                TipoModuloArchivo.INVENTARIO, "INV-2026-07-02", "Cierre de mes");
        configurarPlanBusinessSinUsoActual();
        when(archivoRepository.existsByIdGranjaAndTipoModuloAndCodigoArchivoIgnoreCase(
                        ID_GRANJA, TipoModuloArchivo.INVENTARIO, "INV-2026-07-02"))
                .thenReturn(false);
        when(inventarioProvider.capturar(ID_TENANT, ID_GRANJA))
                .thenReturn(new ArchivoSnapshot(Map.of("items", List.of("MAIZ")), 1));
        when(archivoRepository.save(any(Archivo.class)))
                .thenAnswer(EntidadConIdMocks.asignarIdAlGuardar(Archivo.class, Archivo::setId));

        ArchivoResumenResponse response = crear(request);

        assertThat(response.id()).isEqualTo(1L);
        assertThat(response.tipo()).isEqualTo(TipoModuloArchivo.INVENTARIO);
        assertThat(response.codigoArchivo()).isEqualTo("INV-2026-07-02");
        assertThat(response.descripcion()).isEqualTo("Cierre de mes");
        assertThat(response.creadoPorEmail()).isEqualTo(EMAIL_CREADOR);
        assertThat(response.totalRegistros()).isEqualTo(1);

        var archivoCaptor = ArgumentCaptor.forClass(Archivo.class);
        verify(archivoRepository).save(archivoCaptor.capture());
        Archivo guardado = archivoCaptor.getValue();
        assertThat(guardado.getIdGranja()).isEqualTo(ID_GRANJA);
        assertThat(guardado.getCreadoPor()).isEqualTo(ID_CREADOR);
        assertThat(guardado.getDatos()).contains("MAIZ");

        var eventoCaptor = ArgumentCaptor.forClass(AuditoriaEvento.class);
        verify(auditoriaService).registrar(eventoCaptor.capture());
        AuditoriaEvento evento = eventoCaptor.getValue();
        assertThat(evento.accion()).isEqualTo(AccionAuditoria.ARCHIVO_CREADO);
        assertThat(evento.idUsuario()).isEqualTo(ID_CREADOR);
        assertThat(evento.idGranja()).isEqualTo(ID_GRANJA);
        assertThat(evento.tablaOrigen()).isEqualTo("t_archivo");

        verify(granjaAccesoService).validarAcceso(ID_TENANT, ID_GRANJA);
    }

    @Test
    @DisplayName("crear: trimea el código y normaliza descripción vacía a null")
    void crear_aplicaTrimYNormaliza() {
        var request = new ArchivoCrearRequest(TipoModuloArchivo.COMPRAS, "  CMP-01  ", "   ");
        configurarPlanBusinessSinUsoActual();
        when(archivoRepository.existsByIdGranjaAndTipoModuloAndCodigoArchivoIgnoreCase(
                        ID_GRANJA, TipoModuloArchivo.COMPRAS, "CMP-01"))
                .thenReturn(false);
        when(comprasProvider.capturar(ID_TENANT, ID_GRANJA))
                .thenReturn(new ArchivoSnapshot(List.of(), 0));
        when(archivoRepository.save(any(Archivo.class)))
                .thenAnswer(EntidadConIdMocks.asignarIdAlGuardar(Archivo.class, Archivo::setId));

        ArchivoResumenResponse response = crear(request);

        assertThat(response.codigoArchivo()).isEqualTo("CMP-01");
        assertThat(response.descripcion()).isNull();
        assertThat(response.totalRegistros()).isZero();
    }

    @Test
    @DisplayName("crear: 409 si ya existe un archivo del mismo tipo con ese código")
    void crear_codigoDuplicado() {
        var request = new ArchivoCrearRequest(TipoModuloArchivo.INVENTARIO, "INV-01", null);
        configurarPlanBusinessSinUsoActual();
        when(archivoRepository.existsByIdGranjaAndTipoModuloAndCodigoArchivoIgnoreCase(
                        ID_GRANJA, TipoModuloArchivo.INVENTARIO, "INV-01"))
                .thenReturn(true);

        assertThatThrownBy(() -> crear(request))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT);
        verify(archivoRepository, never()).save(any());
        verify(inventarioProvider, never()).capturar(any(), any());
    }

    @Test
    @DisplayName("crear: 409 al alcanzar el límite de archivos del plan")
    void crear_limiteDePlan() {
        var request = new ArchivoCrearRequest(TipoModuloArchivo.INVENTARIO, "INV-04", null);
        when(planService.obtenerPlanEfectivo(ID_TENANT)).thenReturn(PlanSuscripcion.DEMO);
        when(planService.limiteArchivos(PlanSuscripcion.DEMO)).thenReturn(3);
        when(archivoRepository.countByIdGranja(ID_GRANJA)).thenReturn(3L);

        assertThatThrownBy(() -> crear(request))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("DEMO")
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT);
        verify(archivoRepository, never()).save(any());
    }

    @Test
    @DisplayName("crear: 403 si el tenant no tiene acceso a la granja")
    void crear_sinAccesoAGranja() {
        var request = new ArchivoCrearRequest(TipoModuloArchivo.INVENTARIO, "INV-01", null);
        doThrow(new ResponseStatusException(HttpStatus.FORBIDDEN, "Sin acceso a esta granja"))
                .when(granjaAccesoService)
                .validarAcceso(ID_TENANT, ID_GRANJA);

        assertThatThrownBy(() -> crear(request))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
        verify(archivoRepository, never()).save(any());
    }

    // ---------- listar() ----------

    @Test
    @DisplayName("listar: sin tipo devuelve todos los archivos de la granja, más nuevos primero")
    void listar_sinFiltro() {
        when(archivoRepository.findByIdGranjaOrderByFechaCreacionDesc(ID_GRANJA))
                .thenReturn(List.of(archivoPersistido(2L), archivoPersistido(1L)));

        List<ArchivoResumenResponse> resultado = archivoService.listar(ID_TENANT, ID_GRANJA, null);

        assertThat(resultado).extracting(ArchivoResumenResponse::id).containsExactly(2L, 1L);
        verify(granjaAccesoService).validarAcceso(ID_TENANT, ID_GRANJA);
    }

    @Test
    @DisplayName("listar: con tipo filtra por módulo")
    void listar_conFiltroDeTipo() {
        when(archivoRepository.findByIdGranjaAndTipoModuloOrderByFechaCreacionDesc(
                        ID_GRANJA, TipoModuloArchivo.COMPRAS))
                .thenReturn(List.of(archivoPersistido(5L)));

        List<ArchivoResumenResponse> resultado =
                archivoService.listar(ID_TENANT, ID_GRANJA, TipoModuloArchivo.COMPRAS);

        assertThat(resultado).hasSize(1);
        assertThat(resultado.getFirst().id()).isEqualTo(5L);
    }

    // ---------- obtener() ----------

    @Test
    @DisplayName("obtener: devuelve la cabecera con el snapshot parseado como JSON")
    void obtener_happyPath() {
        Archivo archivo = archivoPersistido(7L);
        when(archivoRepository.findByIdAndIdGranja(7L, ID_GRANJA)).thenReturn(Optional.of(archivo));

        ArchivoDetalleResponse detalle = archivoService.obtener(ID_TENANT, ID_GRANJA, 7L);

        assertThat(detalle.id()).isEqualTo(7L);
        assertThat(detalle.datos().get("inicializado").asBoolean()).isTrue();
        assertThat(detalle.datos().get("items").size()).isEqualTo(1);
    }

    @Test
    @DisplayName("obtener: 404 si el archivo no existe o pertenece a otra granja")
    void obtener_noEncontrado() {
        when(archivoRepository.findByIdAndIdGranja(99L, ID_GRANJA)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> archivoService.obtener(ID_TENANT, ID_GRANJA, 99L))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    private static Archivo archivoPersistido(Long id) {
        return Archivo.builder()
                .id(id)
                .idGranja(ID_GRANJA)
                .tipoModulo(TipoModuloArchivo.INVENTARIO)
                .codigoArchivo("INV-" + id)
                .descripcion("Snapshot " + id)
                .fechaCreacion(Instant.now())
                .creadoPor(ID_CREADOR)
                .creadoPorEmail(EMAIL_CREADOR)
                .totalRegistros(1)
                .datos("{\"inicializado\":true,\"items\":[{\"codigoMateriaPrima\":\"MAIZ\"}]}")
                .build();
    }
}
