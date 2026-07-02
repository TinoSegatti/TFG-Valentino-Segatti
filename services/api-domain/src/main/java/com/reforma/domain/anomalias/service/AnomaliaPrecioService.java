package com.reforma.domain.anomalias.service;

import com.reforma.domain.anomalias.domain.ClasificacionAnomalia;
import com.reforma.domain.anomalias.dto.AnomaliaEvaluacionResponse;
import com.reforma.domain.anomalias.dto.AnomaliaHistorialResponse;
import com.reforma.domain.anomalias.dto.EvaluarAnomaliaRequest;
import com.reforma.domain.anomalias.dto.LineaAnomaliaInput;
import com.reforma.domain.anomalias.entity.AnomaliaPrecio;
import com.reforma.domain.anomalias.repository.AnomaliaPrecioRepository;
import com.reforma.domain.common.util.IdGenerator;
import com.reforma.domain.compras.repository.CompraCabeceraRepository;
import com.reforma.domain.granjas.service.GranjaAccesoService;
import com.reforma.domain.materiasprimas.entity.MateriaPrima;
import com.reforma.domain.materiasprimas.entity.RegistroPrecio;
import com.reforma.domain.materiasprimas.repository.MateriaPrimaRepository;
import com.reforma.domain.materiasprimas.repository.RegistroPrecioRepository;
import com.reforma.domain.ml.MlClient;
import com.reforma.domain.ml.dto.AnomaliaMlResponse;
import com.reforma.domain.ml.dto.EvaluarAnomaliaMlRequest;
import com.reforma.domain.ml.dto.PuntoHistorialMl;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.server.ResponseStatusException;

/**
 * Detección de anomalías de precio (RF-IA-ANOM-*). Orquesta: arma el historial desde
 * {@code t_registro_precio}, delega el cálculo del Z-Score en api-ml ({@link MlClient}) y persiste
 * las anomalías relevantes en {@code t_ia_anomalia_precio}.
 *
 * <p>El cómputo estadístico NO vive aquí: api-ml es la única fuente del Z-Score (ADR-0003/0004).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AnomaliaPrecioService {

    private static final String ORIGEN_COMPRA = "COMPRA";

    private final MlClient mlClient;
    private final RegistroPrecioRepository registroPrecioRepository;
    private final MateriaPrimaRepository materiaPrimaRepository;
    private final CompraCabeceraRepository compraCabeceraRepository;
    private final AnomaliaPrecioRepository anomaliaPrecioRepository;
    private final GranjaAccesoService granjaAccesoService;

    /** Evalúa un precio contra el historial de la MP (preview en el formulario, no persiste). */
    @Transactional(readOnly = true)
    public AnomaliaEvaluacionResponse evaluar(
            String idTenant, String idGranja, EvaluarAnomaliaRequest request) {
        granjaAccesoService.validarAcceso(idTenant, idGranja);
        MateriaPrima mp = materiaPrimaRepository
                .findByIdAndGranjaId(request.idMateriaPrima(), idGranja)
                .orElseThrow(() ->
                        new ResponseStatusException(HttpStatus.NOT_FOUND, "Materia prima no encontrada"));

        List<PuntoHistorialMl> historial = aHistorial(
                registroPrecioRepository.findByMateriaPrimaIdAndOrigenOrderByFechaReferenciaAsc(
                        request.idMateriaPrima(), ORIGEN_COMPRA));

        Optional<AnomaliaMlResponse> ml = mlClient.evaluarAnomalia(
                new EvaluarAnomaliaMlRequest(request.precio(), historial, request.mesReferencia()));

        return ml.map(r -> AnomaliaEvaluacionResponse.desde(mp.getNombreMateriaPrima(), request.precio(), r))
                .orElseGet(() -> AnomaliaEvaluacionResponse.sinEvaluar(mp.getNombreMateriaPrima()));
    }

    /**
     * Registra las anomalías de las líneas de una compra recién REGISTRADA. Se difiere a
     * {@code afterCommit}: la cabecera de compra aún no está committeada cuando se invoca, por lo
     * que persistir la anomalía (FK {@code id_compra}) dentro de la misma transacción fallaría la
     * FK desde una tx nueva, y hacerlo en la misma tx arriesgaría la compra. Tras el commit, la
     * compra ya existe y la persistencia es fail-open (sus errores no afectan a la compra).
     */
    public void registrarTrasCompra(
            String idGranja,
            String idCompra,
            Instant fechaCompra,
            List<LineaAnomaliaInput> lineas,
            Map<Long, Boolean> confirmaciones) {
        Runnable tarea = () -> persistirAnomalias(idCompra, fechaCompra, lineas, confirmaciones);
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    tarea.run();
                }
            });
        } else {
            tarea.run();
        }
    }

    private void persistirAnomalias(
            String idCompra,
            Instant fechaCompra,
            List<LineaAnomaliaInput> lineas,
            Map<Long, Boolean> confirmaciones) {
        Integer mes = fechaCompra.atZone(ZoneOffset.UTC).getMonthValue();
        for (LineaAnomaliaInput linea : lineas) {
            try {
                List<PuntoHistorialMl> historial = aHistorial(
                        registroPrecioRepository
                                .findByMateriaPrimaIdAndOrigenAndCompraIdNotOrderByFechaReferenciaAsc(
                                        linea.idMateriaPrima(), ORIGEN_COMPRA, idCompra));

                Optional<AnomaliaMlResponse> mlOpt = mlClient.evaluarAnomalia(
                        new EvaluarAnomaliaMlRequest(linea.precio(), historial, mes));
                if (mlOpt.isEmpty()) {
                    continue;
                }
                AnomaliaMlResponse ml = mlOpt.get();
                ClasificacionAnomalia clasificacion = ClasificacionAnomalia.desde(ml.clasificacion());
                if (!clasificacion.esPersistible()) {
                    continue;
                }
                anomaliaPrecioRepository.save(AnomaliaPrecio.builder()
                        .id(IdGenerator.newId())
                        .compra(compraCabeceraRepository.getReferenceById(idCompra))
                        .materiaPrima(materiaPrimaRepository.getReferenceById(linea.idMateriaPrima()))
                        .precioIngresado(linea.precio())
                        .precioPromedioHistorico(ml.promedioHistorico())
                        .zScore(ml.zScore())
                        .clasificacion(clasificacion)
                        .usuarioConfirmo(confirmaciones.get(linea.idMateriaPrima()))
                        .detectadoEn(Instant.now())
                        .build());
            } catch (Exception e) {
                // Fail-open: registrar una anomalía nunca debe afectar a la compra (ya committeada).
                log.warn(
                        "No se pudo registrar la anomalía de la MP {} (compra {}): {}",
                        linea.idMateriaPrima(),
                        idCompra,
                        e.getMessage());
            }
        }
    }

    @Transactional
    public void confirmar(String idTenant, String idGranja, String idAnomalia, boolean confirmo) {
        granjaAccesoService.validarAcceso(idTenant, idGranja);
        AnomaliaPrecio anomalia = anomaliaPrecioRepository
                .findByIdAndCompra_Granja_Id(idAnomalia, idGranja)
                .orElseThrow(() ->
                        new ResponseStatusException(HttpStatus.NOT_FOUND, "Anomalía no encontrada"));
        anomalia.setUsuarioConfirmo(confirmo);
    }

    @Transactional(readOnly = true)
    public List<AnomaliaHistorialResponse> listar(String idTenant, String idGranja, int limite) {
        granjaAccesoService.validarAcceso(idTenant, idGranja);
        return anomaliaPrecioRepository.listarPorGranja(idGranja, PageRequest.of(0, limite)).stream()
                .map(AnomaliaHistorialResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<AnomaliaHistorialResponse> listarPorProveedor(
            String idTenant, String idGranja, Long idProveedor, int limite) {
        granjaAccesoService.validarAcceso(idTenant, idGranja);
        return anomaliaPrecioRepository
                .listarPorProveedor(idGranja, idProveedor, PageRequest.of(0, limite)).stream()
                .map(AnomaliaHistorialResponse::from)
                .toList();
    }

    private static List<PuntoHistorialMl> aHistorial(List<RegistroPrecio> registros) {
        return registros.stream()
                .map(r -> new PuntoHistorialMl(
                        r.getFechaReferencia().atZone(ZoneOffset.UTC).toLocalDate(), r.getPrecioNuevo()))
                .toList();
    }
}
