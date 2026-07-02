package com.reforma.domain.prediccion.service;

import com.reforma.domain.common.domain.PlanSuscripcion;
import com.reforma.domain.common.util.IdGenerator;
import com.reforma.domain.compras.domain.EstadoCompra;
import com.reforma.domain.compras.repository.CompraDetalleRepository;
import com.reforma.domain.fabricaciones.repository.FabricacionDetalleRepository;
import com.reforma.domain.granjas.repository.GranjaRepository;
import com.reforma.domain.granjas.service.GranjaAccesoService;
import com.reforma.domain.inventario.entity.Inventario;
import com.reforma.domain.inventario.repository.InventarioRepository;
import com.reforma.domain.materiasprimas.entity.MateriaPrima;
import com.reforma.domain.materiasprimas.repository.MateriaPrimaRepository;
import com.reforma.domain.ml.MlClient;
import com.reforma.domain.ml.dto.ItemPrediccionMl;
import com.reforma.domain.ml.dto.PrediccionItemMl;
import com.reforma.domain.ml.dto.PrediccionStockMlRequest;
import com.reforma.domain.ml.dto.PuntoMensualMl;
import com.reforma.domain.ml.dto.PuntoSerieMl;
import com.reforma.domain.prediccion.domain.NivelAlertaStock;
import com.reforma.domain.prediccion.dto.PrediccionStockDetalleResponse;
import com.reforma.domain.prediccion.dto.PrediccionStockResponse;
import com.reforma.domain.prediccion.dto.PuntoSerieResponse;
import com.reforma.domain.prediccion.entity.PrediccionStock;
import com.reforma.domain.prediccion.repository.PrediccionStockRepository;
import com.reforma.domain.prediccion.support.AgregadoMensualMateria;
import com.reforma.domain.suscripciones.service.PlanService;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Predicción de agotamiento de stock (RF-IA-PRED). Arma la serie mensual de ingresos (compras) vs
 * consumo (fabricaciones) por materia prima desde la BD, delega el cálculo del promedio neto y la
 * proyección en api-ml ({@link MlClient}) y persiste el resultado en {@code t_ia_prediccion_stock}.
 *
 * <p>Gateado por plan (RD-03: solo BUSINESS/ENTERPRISE). Fail-open: si api-ml no responde, no rompe.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PrediccionStockService {

    /** Ventana de historial (meses hacia atrás, incluido el mes actual). */
    private static final int MESES_VENTANA = 12;
    /** Meses a proyectar hacia adelante cuando la tendencia es creciente/estable. */
    private static final int MESES_PROYECCION = 6;

    private final MlClient mlClient;
    private final CompraDetalleRepository compraDetalleRepository;
    private final FabricacionDetalleRepository fabricacionDetalleRepository;
    private final InventarioRepository inventarioRepository;
    private final MateriaPrimaRepository materiaPrimaRepository;
    private final GranjaRepository granjaRepository;
    private final PrediccionStockRepository prediccionStockRepository;
    private final GranjaAccesoService granjaAccesoService;
    private final PlanService planService;

    /** Predicción de todas las MPs activas de la granja (resumen, para el indicador de la tabla). */
    @Transactional
    public List<PrediccionStockResponse> predecirGranja(String idTenant, String idGranja) {
        validar(idTenant, idGranja);
        List<MateriaPrima> materias =
                materiaPrimaRepository.findByGranjaIdAndActivaTrueOrderByNombreMateriaPrimaAsc(idGranja);
        if (materias.isEmpty()) {
            return List.of();
        }
        Map<Long, List<PuntoMensualMl>> series = construirSeries(idGranja);
        Map<Long, Double> stock = stockPorMateria(idGranja);

        List<ItemPrediccionMl> items = materias.stream()
                .map(mp -> new ItemPrediccionMl(
                        mp.getId(),
                        stock.getOrDefault(mp.getId(), 0.0),
                        series.getOrDefault(mp.getId(), List.of())))
                .toList();

        Map<Long, PrediccionItemMl> pred = evaluar(items, false);
        Instant ahora = Instant.now();
        List<PrediccionStockResponse> salida = new ArrayList<>();
        for (MateriaPrima mp : materias) {
            PrediccionItemMl p = pred.get(mp.getId());
            if (p == null) {
                continue; // fail-open: api-ml no devolvió esta MP
            }
            LocalDate fecha = fechaAgotamiento(p);
            persistir(idGranja, mp.getId(), p, fecha, ahora);
            salida.add(aResumen(mp, p, fecha));
        }
        return salida;
    }

    /** Predicción de una MP con las series para el gráfico del popup. */
    @Transactional
    public PrediccionStockDetalleResponse predecirMateriaPrima(
            String idTenant, String idGranja, Long idMateriaPrima) {
        validar(idTenant, idGranja);
        MateriaPrima mp = materiaPrimaRepository
                .findByIdAndGranjaId(idMateriaPrima, idGranja)
                .orElseThrow(() ->
                        new ResponseStatusException(HttpStatus.NOT_FOUND, "Materia prima no encontrada"));

        List<PuntoMensualMl> serie = construirSeries(idGranja).getOrDefault(idMateriaPrima, List.of());
        double stock = stockPorMateria(idGranja).getOrDefault(idMateriaPrima, 0.0);

        ItemPrediccionMl item = new ItemPrediccionMl(idMateriaPrima, stock, serie);
        PrediccionItemMl p = evaluar(List.of(item), true).get(idMateriaPrima);
        if (p == null) {
            // fail-open: api-ml no respondió -> SIN_DATOS, sin series.
            PrediccionStockResponse resumen = new PrediccionStockResponse(
                    idMateriaPrima, mp.getCodigoMateriaPrima(), mp.getNombreMateriaPrima(),
                    NivelAlertaStock.SIN_DATOS.name(), "ESTABLE", stock,
                    null, null, 0, 0, 0, serie.size(), null);
            return new PrediccionStockDetalleResponse(resumen, List.of(), List.of());
        }
        LocalDate fecha = fechaAgotamiento(p);
        persistir(idGranja, idMateriaPrima, p, fecha, Instant.now());
        return new PrediccionStockDetalleResponse(
                aResumen(mp, p, fecha), aPuntos(p.serieHistorica()), aPuntos(p.serieProyeccion()));
    }

    // ---- interno ----

    private void validar(String idTenant, String idGranja) {
        granjaAccesoService.validarAcceso(idTenant, idGranja);
        PlanSuscripcion plan = planService.obtenerPlanEfectivo(idTenant);
        if (!planService.permitePrediccionStock(plan)) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "La predicción de agotamiento de stock requiere un plan BUSINESS o ENTERPRISE");
        }
    }

    private Map<Long, PrediccionItemMl> evaluar(List<ItemPrediccionMl> items, boolean incluirSeries) {
        return mlClient
                .predecirStock(new PrediccionStockMlRequest(items, incluirSeries, MESES_PROYECCION))
                .map(r -> r.predicciones().stream()
                        .collect(Collectors.toMap(PrediccionItemMl::idMateriaPrima, p -> p, (a, b) -> a)))
                .orElseGet(Map::of);
    }

    /**
     * Serie mensual contigua por MP: desde su primer mes con actividad (dentro de la ventana de 12
     * meses) hasta el mes actual, rellenando con 0 los meses sin movimiento. Las MPs sin actividad no
     * aparecen (se les envía serie vacía → api-ml responde SIN_DATOS).
     */
    private Map<Long, List<PuntoMensualMl>> construirSeries(String idGranja) {
        YearMonth actual = YearMonth.now(ZoneOffset.UTC);
        Instant desde = actual
                .minusMonths(MESES_VENTANA - 1L)
                .atDay(1)
                .atStartOfDay(ZoneOffset.UTC)
                .toInstant();

        Map<Long, Map<YearMonth, Double>> ingresos = agrupar(
                compraDetalleRepository.ingresosMensualesPorMateria(
                        idGranja, EstadoCompra.REGISTRADA, desde));
        Map<Long, Map<YearMonth, Double>> consumos = agrupar(
                fabricacionDetalleRepository.consumosMensualesPorMateria(idGranja, desde));

        Set<Long> materias = new HashSet<>();
        materias.addAll(ingresos.keySet());
        materias.addAll(consumos.keySet());

        Map<Long, List<PuntoMensualMl>> series = new HashMap<>();
        for (Long idMp : materias) {
            Map<YearMonth, Double> ing = ingresos.getOrDefault(idMp, Map.of());
            Map<YearMonth, Double> con = consumos.getOrDefault(idMp, Map.of());
            YearMonth primera = Stream.concat(ing.keySet().stream(), con.keySet().stream())
                    .min(Comparator.naturalOrder())
                    .orElse(null);
            if (primera == null) {
                continue;
            }
            List<PuntoMensualMl> serie = new ArrayList<>();
            for (YearMonth m = primera; !m.isAfter(actual); m = m.plusMonths(1)) {
                serie.add(new PuntoMensualMl(
                        m.toString(), ing.getOrDefault(m, 0.0), con.getOrDefault(m, 0.0)));
            }
            series.put(idMp, serie);
        }
        return series;
    }

    private static Map<Long, Map<YearMonth, Double>> agrupar(List<AgregadoMensualMateria> filas) {
        Map<Long, Map<YearMonth, Double>> mapa = new HashMap<>();
        for (AgregadoMensualMateria f : filas) {
            mapa.computeIfAbsent(f.idMateriaPrima(), k -> new HashMap<>())
                    .merge(YearMonth.parse(f.mes()), f.kilos() == null ? 0.0 : f.kilos(), Double::sum);
        }
        return mapa;
    }

    private Map<Long, Double> stockPorMateria(String idGranja) {
        Map<Long, Double> mapa = new HashMap<>();
        for (Inventario inv :
                inventarioRepository.findByGranjaIdOrderByMateriaPrimaCodigoMateriaPrimaAsc(idGranja)) {
            Double real = inv.getCantidadReal();
            mapa.put(inv.getMateriaPrima().getId(), real == null ? 0.0 : real);
        }
        return mapa;
    }

    private static LocalDate fechaAgotamiento(PrediccionItemMl p) {
        Integer offset = p.fechaAgotamientoOffsetDias();
        return offset == null ? null : LocalDate.now(ZoneOffset.UTC).plusDays(offset);
    }

    private void persistir(
            String idGranja, Long idMp, PrediccionItemMl p, LocalDate fecha, Instant ahora) {
        try {
            PrediccionStock fila = prediccionStockRepository
                    .findByGranjaIdAndMateriaPrimaId(idGranja, idMp)
                    .orElseGet(() -> PrediccionStock.builder()
                            .id(IdGenerator.newId())
                            .granja(granjaRepository.getReferenceById(idGranja))
                            .materiaPrima(materiaPrimaRepository.getReferenceById(idMp))
                            .build());
            fila.setNivelAlerta(NivelAlertaStock.desde(p.nivelAlerta()));
            fila.setDiasRestantes(p.diasRestantes());
            fila.setFechaAgotamiento(fecha);
            fila.setModeloUsado(p.modeloUsado());
            fila.setCalculadoEn(ahora);
            prediccionStockRepository.save(fila);
        } catch (Exception e) {
            // Fail-open: persistir la predicción nunca debe romper la consulta.
            log.warn(
                    "No se pudo persistir la predicción de la MP {} (granja {}): {}",
                    idMp, idGranja, e.getMessage());
        }
    }

    private static PrediccionStockResponse aResumen(
            MateriaPrima mp, PrediccionItemMl p, LocalDate fecha) {
        return new PrediccionStockResponse(
                mp.getId(),
                mp.getCodigoMateriaPrima(),
                mp.getNombreMateriaPrima(),
                p.nivelAlerta(),
                p.tendencia(),
                p.stockActual(),
                p.diasRestantes(),
                fecha,
                p.netoPromedio(),
                p.consumoPromedio(),
                p.ingresoPromedio(),
                p.nMeses(),
                p.modeloUsado());
    }

    private static List<PuntoSerieResponse> aPuntos(List<PuntoSerieMl> puntos) {
        if (puntos == null) {
            return List.of();
        }
        return puntos.stream()
                .map(x -> new PuntoSerieResponse(x.mes(), x.existencias()))
                .toList();
    }
}
