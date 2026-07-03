package com.reforma.domain.archivos.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
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
import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Módulo Archivos: crea y consulta snapshots inmutables de Inventario/Compras/Fórmulas.
 * Un archivo nunca se edita ni se borra (registro histórico); si el usuario se equivoca,
 * crea otro. La captura la hace el {@link ArchivoSnapshotProvider} del tipo pedido, dentro
 * de la transacción de {@link #crear}.
 */
@Service
public class ArchivoService {

    private final ArchivoRepository archivoRepository;
    private final GranjaAccesoService granjaAccesoService;
    private final PlanService planService;
    private final AuditoriaService auditoriaService;
    private final ObjectMapper objectMapper;
    private final Map<TipoModuloArchivo, ArchivoSnapshotProvider> providers;

    public ArchivoService(
            ArchivoRepository archivoRepository,
            GranjaAccesoService granjaAccesoService,
            PlanService planService,
            AuditoriaService auditoriaService,
            ObjectMapper objectMapper,
            List<ArchivoSnapshotProvider> providers) {
        this.archivoRepository = archivoRepository;
        this.granjaAccesoService = granjaAccesoService;
        this.planService = planService;
        this.auditoriaService = auditoriaService;
        this.objectMapper = objectMapper;
        this.providers = new EnumMap<>(TipoModuloArchivo.class);
        for (ArchivoSnapshotProvider provider : providers) {
            this.providers.put(provider.tipo(), provider);
        }
    }

    @Transactional
    public ArchivoResumenResponse crear(
            String idTenant,
            String idUsuarioCreador,
            String emailCreador,
            String idGranja,
            ArchivoCrearRequest request) {
        granjaAccesoService.validarAcceso(idTenant, idGranja);
        validarLimitePlan(idTenant, idGranja);

        String codigo = request.codigoArchivo().trim();
        if (archivoRepository.existsByIdGranjaAndTipoModuloAndCodigoArchivoIgnoreCase(
                idGranja, request.tipo(), codigo)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Ya existe un archivo de " + request.tipo() + " con el código " + codigo);
        }

        ArchivoSnapshotProvider provider = providers.get(request.tipo());
        if (provider == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Módulo no archivable: " + request.tipo());
        }
        ArchivoSnapshot snapshot = provider.capturar(idTenant, idGranja);

        Archivo archivo = archivoRepository.save(Archivo.builder()
                .idGranja(idGranja)
                .tipoModulo(request.tipo())
                .codigoArchivo(codigo)
                .descripcion(normalizar(request.descripcion()))
                .fechaCreacion(Instant.now())
                .creadoPor(idUsuarioCreador)
                .creadoPorEmail(emailCreador)
                .totalRegistros(snapshot.totalRegistros())
                .datos(serializar(snapshot.datos()))
                .build());

        ArchivoResumenResponse response = ArchivoResumenResponse.from(archivo);
        auditoriaService.registrar(AuditoriaEvento.builder()
                .idUsuario(idUsuarioCreador)
                .idGranja(idGranja)
                .tablaOrigen("t_archivo")
                .idRegistro(String.valueOf(archivo.getId()))
                .accion(AccionAuditoria.ARCHIVO_CREADO)
                .descripcion("Archivo de " + request.tipo() + " '" + codigo + "' creado ("
                        + snapshot.totalRegistros() + " registros)")
                .datosNuevos(response)
                .build());
        return response;
    }

    @Transactional(readOnly = true)
    public List<ArchivoResumenResponse> listar(
            String idTenant, String idGranja, TipoModuloArchivo tipo) {
        granjaAccesoService.validarAcceso(idTenant, idGranja);
        List<Archivo> archivos = tipo == null
                ? archivoRepository.findByIdGranjaOrderByFechaCreacionDesc(idGranja)
                : archivoRepository.findByIdGranjaAndTipoModuloOrderByFechaCreacionDesc(idGranja, tipo);
        return archivos.stream().map(ArchivoResumenResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public ArchivoDetalleResponse obtener(String idTenant, String idGranja, Long idArchivo) {
        granjaAccesoService.validarAcceso(idTenant, idGranja);
        Archivo archivo = archivoRepository
                .findByIdAndIdGranja(idArchivo, idGranja)
                .orElseThrow(() ->
                        new ResponseStatusException(HttpStatus.NOT_FOUND, "Archivo no encontrado"));
        return ArchivoDetalleResponse.from(archivo, parsear(archivo.getDatos()));
    }

    private void validarLimitePlan(String idTenant, String idGranja) {
        PlanSuscripcion plan = planService.obtenerPlanEfectivo(idTenant);
        int limite = planService.limiteArchivos(plan);
        long actuales = archivoRepository.countByIdGranja(idGranja);
        if (actuales >= limite) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Plan " + plan.name() + " permite hasta " + limite
                            + " archivos por granja. Actualice su plan para crear más.");
        }
    }

    private String serializar(Object datos) {
        try {
            return objectMapper.writeValueAsString(datos);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("No se pudo serializar el snapshot del archivo", e);
        }
    }

    private JsonNode parsear(String datos) {
        try {
            return objectMapper.readTree(datos);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("No se pudo leer el snapshot del archivo", e);
        }
    }

    private static String normalizar(String value) {
        if (value == null) return null;
        String t = value.trim();
        return t.isEmpty() ? null : t;
    }
}
