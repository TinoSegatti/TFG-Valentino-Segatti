package com.reforma.domain.reporte.service;

import com.reforma.domain.anomalias.entity.AnomaliaPrecio;
import com.reforma.domain.anomalias.repository.AnomaliaPrecioRepository;
import com.reforma.domain.auditoria.domain.AccionAuditoria;
import com.reforma.domain.auditoria.dto.AuditoriaEvento;
import com.reforma.domain.auditoria.service.AuditoriaService;
import com.reforma.domain.compras.domain.EstadoCompra;
import com.reforma.domain.compras.entity.CompraCabecera;
import com.reforma.domain.compras.entity.CompraDetalle;
import com.reforma.domain.compras.repository.CompraCabeceraRepository;
import com.reforma.domain.fabricaciones.domain.EstadoFabricacion;
import com.reforma.domain.fabricaciones.entity.Fabricacion;
import com.reforma.domain.fabricaciones.entity.FabricacionDetalle;
import com.reforma.domain.fabricaciones.repository.FabricacionRepository;
import com.reforma.domain.granjas.service.GranjaAccesoService;
import com.reforma.domain.inventario.dto.InventarioResponse;
import com.reforma.domain.inventario.service.InventarioService;
import com.reforma.domain.prediccion.repository.PrediccionStockRepository;
import com.reforma.domain.reporte.dto.InformeEstadoResponse;
import com.reforma.domain.reporte.dto.InformeEstadoResponse.AnomaliaInforme;
import com.reforma.domain.reporte.dto.InformeEstadoResponse.FormulaConsumoInforme;
import com.reforma.domain.reporte.dto.InformeEstadoResponse.MateriaCompradaInforme;
import com.reforma.domain.reporte.dto.InformeEstadoResponse.MateriaConsumidaInforme;
import com.reforma.domain.reporte.dto.InformeEstadoResponse.PrediccionInforme;
import com.reforma.domain.reporte.dto.InformeEstadoResponse.ProveedorInforme;
import com.reforma.domain.reporte.dto.InformeEstadoResponse.PuntoMensual;
import com.reforma.domain.reporte.dto.InformeEstadoResponse.ResumenGeneral;
import com.reforma.domain.reporte.dto.InformeEstadoResponse.SeccionCompras;
import com.reforma.domain.reporte.dto.InformeEstadoResponse.SeccionConsumos;
import com.reforma.domain.reporte.dto.InformeEstadoResponse.SeccionIa;
import com.reforma.domain.reporte.dto.InformeEstadoResponse.SeccionInventario;
import com.reforma.domain.reporte.dto.InformeEstadoResponse.SeccionProveedores;
import com.reforma.domain.suscripciones.service.PlanService;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Arma el informe de estado (RF-REP-001/003) agregando compras, inventario, fabricaciones
 * y datos IA del período. Las agregaciones se hacen en memoria sobre los repositorios
 * existentes (volúmenes acotados por granja) usando los snapshots de las transacciones
 * (ADR 0005), nunca el catálogo vivo.
 */
@Service
@RequiredArgsConstructor
public class InformeEstadoService {

    static final int PERIODO_DEFAULT_DIAS = 90;
    static final int PERIODO_MAXIMO_MESES = 24;
    private static final DateTimeFormatter MES = DateTimeFormatter.ofPattern("yyyy-MM");

    private final CompraCabeceraRepository compraCabeceraRepository;
    private final FabricacionRepository fabricacionRepository;
    private final AnomaliaPrecioRepository anomaliaPrecioRepository;
    private final PrediccionStockRepository prediccionStockRepository;
    private final InventarioService inventarioService;
    private final GranjaAccesoService granjaAccesoService;
    private final PlanService planService;
    private final AuditoriaService auditoriaService;

    @Transactional
    public InformeEstadoResponse generar(
            String idTenant, String idUsuario, String idGranja, LocalDate desde, LocalDate hasta) {
        granjaAccesoService.validarAcceso(idTenant, idGranja);

        LocalDate hastaEfectivo = hasta != null ? hasta : LocalDate.now(ZoneOffset.UTC);
        LocalDate desdeEfectivo = desde != null ? desde : hastaEfectivo.minusDays(PERIODO_DEFAULT_DIAS);
        validarPeriodo(desdeEfectivo, hastaEfectivo);

        Instant desdeInstant = desdeEfectivo.atStartOfDay(ZoneOffset.UTC).toInstant();
        // Fin de período inclusivo: hasta el último instante del día.
        Instant hastaInstant = hastaEfectivo.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant().minusNanos(1);

        List<CompraCabecera> compras = comprasDelPeriodo(idGranja, desdeInstant, hastaInstant);
        List<Fabricacion> fabricaciones = fabricacionesDelPeriodo(idGranja, desdeInstant, hastaInstant);
        List<InventarioResponse> inventario = inventarioService.listar(idTenant, idGranja).items();

        InformeEstadoResponse informe = new InformeEstadoResponse(
                idGranja,
                desdeEfectivo,
                hastaEfectivo,
                resumen(compras, fabricaciones, inventario),
                seccionProveedores(compras),
                seccionInventario(inventario),
                seccionCompras(compras),
                seccionConsumos(fabricaciones),
                seccionIa(idTenant, idGranja, desdeInstant, hastaInstant));

        auditoriaService.registrar(AuditoriaEvento.builder()
                .idUsuario(idUsuario)
                .idGranja(idGranja)
                .tablaOrigen("t_granja")
                .idRegistro(idGranja)
                .accion(AccionAuditoria.INFORME_GENERADO)
                .descripcion("Informe de estado generado (" + desdeEfectivo + " a " + hastaEfectivo + ")")
                .build());
        return informe;
    }

    private static void validarPeriodo(LocalDate desde, LocalDate hasta) {
        if (desde.isAfter(hasta)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "El inicio del período no puede ser posterior al fin");
        }
        if (desde.plusMonths(PERIODO_MAXIMO_MESES).isBefore(hasta)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "El período no puede superar " + PERIODO_MAXIMO_MESES + " meses");
        }
    }

    /** Solo compras REGISTRADAS: los borradores no tienen líneas confirmadas. */
    private List<CompraCabecera> comprasDelPeriodo(String idGranja, Instant desde, Instant hasta) {
        return compraCabeceraRepository.findByGranjaIdAndActivoTrueOrderByFechaCompraDesc(idGranja)
                .stream()
                .filter(c -> c.getEstado() == EstadoCompra.REGISTRADA)
                .filter(c -> !c.getFechaCompra().isBefore(desde) && !c.getFechaCompra().isAfter(hasta))
                .toList();
    }

    private List<Fabricacion> fabricacionesDelPeriodo(String idGranja, Instant desde, Instant hasta) {
        return fabricacionRepository.findByGranjaIdAndActivoTrueOrderByFechaFabricacionDesc(idGranja)
                .stream()
                .filter(f -> f.getEstado() == EstadoFabricacion.REGISTRADA)
                .filter(f -> !f.getFechaFabricacion().isBefore(desde)
                        && !f.getFechaFabricacion().isAfter(hasta))
                .toList();
    }

    private static ResumenGeneral resumen(
            List<CompraCabecera> compras,
            List<Fabricacion> fabricaciones,
            List<InventarioResponse> inventario) {
        double gasto = compras.stream().mapToDouble(CompraCabecera::getTotalFactura).sum();
        double kgProducidos =
                fabricaciones.stream().mapToDouble(Fabricacion::getCantidadFabricacion).sum();
        double valorStock = inventario.stream().mapToDouble(InventarioResponse::valorStock).sum();
        double merma = inventario.stream().mapToDouble(InventarioResponse::merma).sum();
        return new ResumenGeneral(
                compras.size(),
                redondear(gasto),
                redondear(valorStock),
                fabricaciones.size(),
                redondear(kgProducidos),
                redondear(merma));
    }

    private static SeccionProveedores seccionProveedores(List<CompraCabecera> compras) {
        // Clave = código de proveedor snapshot; acumula compras, monto, kg y gasto por MP.
        Map<String, AcumuladorProveedor> porProveedor = new LinkedHashMap<>();
        for (CompraCabecera compra : compras) {
            AcumuladorProveedor acc = porProveedor.computeIfAbsent(
                    compra.getCodigoProveedorSnapshot(),
                    k -> new AcumuladorProveedor(compra.getNombreProveedorSnapshot()));
            acc.compras++;
            acc.monto += compra.getTotalFactura();
            for (CompraDetalle linea : compra.getDetalles()) {
                acc.kg += linea.getCantidadComprada();
                acc.gastoPorMp.merge(linea.getNombreMpSnapshot(), linea.getSubtotal(), Double::sum);
            }
        }
        List<ProveedorInforme> proveedores = porProveedor.entrySet().stream()
                .map(e -> new ProveedorInforme(
                        e.getKey(),
                        e.getValue().nombre,
                        e.getValue().compras,
                        redondear(e.getValue().monto),
                        redondear(e.getValue().kg),
                        e.getValue().materiaPrincipal()))
                .sorted(Comparator.comparingDouble(ProveedorInforme::monto).reversed())
                .toList();
        return new SeccionProveedores(proveedores);
    }

    private static SeccionInventario seccionInventario(List<InventarioResponse> items) {
        double valor = items.stream().mapToDouble(InventarioResponse::valorStock).sum();
        double merma = items.stream().mapToDouble(InventarioResponse::merma).sum();
        return new SeccionInventario(items, redondear(valor), redondear(merma));
    }

    private static SeccionCompras seccionCompras(List<CompraCabecera> compras) {
        Map<String, double[]> porMes = new TreeMap<>(); // mes -> [monto, kg]
        Map<String, AcumuladorMateria> porMateria = new LinkedHashMap<>();
        for (CompraCabecera compra : compras) {
            String mes = MES.format(compra.getFechaCompra().atZone(ZoneOffset.UTC));
            double[] totalesMes = porMes.computeIfAbsent(mes, k -> new double[2]);
            totalesMes[0] += compra.getTotalFactura();
            for (CompraDetalle linea : compra.getDetalles()) {
                totalesMes[1] += linea.getCantidadComprada();
                AcumuladorMateria acc = porMateria.computeIfAbsent(
                        linea.getCodigoMpSnapshot(),
                        k -> new AcumuladorMateria(linea.getNombreMpSnapshot()));
                acc.kg += linea.getCantidadComprada();
                acc.monto += linea.getSubtotal();
                acc.precioMin = Math.min(acc.precioMin, linea.getPrecioUnitario());
                acc.precioMax = Math.max(acc.precioMax, linea.getPrecioUnitario());
            }
        }
        List<PuntoMensual> evolucion = porMes.entrySet().stream()
                .map(e -> new PuntoMensual(
                        e.getKey(), redondear(e.getValue()[0]), redondear(e.getValue()[1])))
                .toList();
        List<MateriaCompradaInforme> materias = porMateria.entrySet().stream()
                .map(e -> new MateriaCompradaInforme(
                        e.getKey(),
                        e.getValue().nombre,
                        redondear(e.getValue().kg),
                        redondear(e.getValue().monto),
                        redondear(e.getValue().precioMin),
                        redondear(e.getValue().precioMax),
                        // Promedio ponderado por kilos comprados.
                        e.getValue().kg > 0 ? redondear(e.getValue().monto / e.getValue().kg) : 0))
                .sorted(Comparator.comparingDouble(MateriaCompradaInforme::monto).reversed())
                .toList();
        return new SeccionCompras(evolucion, materias);
    }

    private static SeccionConsumos seccionConsumos(List<Fabricacion> fabricaciones) {
        Map<String, AcumuladorFormula> porFormula = new LinkedHashMap<>();
        Map<String, AcumuladorMateria> porMateria = new LinkedHashMap<>();
        for (Fabricacion fabricacion : fabricaciones) {
            // Fabricaciones sin fórmula (consumo directo sembrado/manual): bucket explícito
            // en lugar de una clave null que renderiza vacía en la UI y el CSV.
            String codigoFormula = fabricacion.getCodigoFormulaSnapshot() != null
                    ? fabricacion.getCodigoFormulaSnapshot()
                    : "SIN_FORMULA";
            String descripcionFormula = fabricacion.getDescripcionFormulaSnapshot() != null
                    ? fabricacion.getDescripcionFormulaSnapshot()
                    : "Consumo sin fórmula";
            AcumuladorFormula accFormula = porFormula.computeIfAbsent(
                    codigoFormula, k -> new AcumuladorFormula(descripcionFormula));
            accFormula.fabricaciones++;
            accFormula.kg += fabricacion.getCantidadFabricacion();
            accFormula.costo += fabricacion.getCostoTotalFabricacion();
            for (FabricacionDetalle linea : fabricacion.getDetalles()) {
                AcumuladorMateria accMp = porMateria.computeIfAbsent(
                        linea.getCodigoMpSnapshot(),
                        k -> new AcumuladorMateria(linea.getNombreMpSnapshot()));
                accMp.kg += linea.getCantidadUsada();
                accMp.monto += linea.getCostoParcial();
            }
        }
        List<FormulaConsumoInforme> formulas = porFormula.entrySet().stream()
                .map(e -> new FormulaConsumoInforme(
                        e.getKey(),
                        e.getValue().descripcion,
                        e.getValue().fabricaciones,
                        redondear(e.getValue().kg),
                        redondear(e.getValue().costo)))
                .sorted(Comparator.comparingDouble(FormulaConsumoInforme::kgProducidos).reversed())
                .toList();
        List<MateriaConsumidaInforme> materias = porMateria.entrySet().stream()
                .map(e -> new MateriaConsumidaInforme(
                        e.getKey(),
                        e.getValue().nombre,
                        redondear(e.getValue().kg),
                        redondear(e.getValue().monto)))
                .sorted(Comparator.comparingDouble(MateriaConsumidaInforme::kgConsumidos).reversed())
                .toList();
        return new SeccionConsumos(formulas, materias);
    }

    private SeccionIa seccionIa(String idTenant, String idGranja, Instant desde, Instant hasta) {
        List<AnomaliaInforme> anomalias =
                anomaliaPrecioRepository.listarPorGranjaYPeriodo(idGranja, desde, hasta).stream()
                        .map(InformeEstadoService::aAnomaliaInforme)
                        .toList();
        boolean prediccionesDisponibles =
                planService.permitePrediccionStock(planService.obtenerPlanEfectivo(idTenant));
        List<PrediccionInforme> predicciones = prediccionesDisponibles
                ? prediccionStockRepository.findByGranjaId(idGranja).stream()
                        .map(p -> new PrediccionInforme(
                                p.getMateriaPrima().getCodigoMateriaPrima(),
                                p.getMateriaPrima().getNombreMateriaPrima(),
                                p.getDiasRestantes(),
                                p.getFechaAgotamiento(),
                                p.getNivelAlerta() != null ? p.getNivelAlerta().name() : null))
                        .sorted(Comparator.comparing(
                                PrediccionInforme::diasRestantes,
                                Comparator.nullsLast(Comparator.naturalOrder())))
                        .toList()
                : List.of();
        return new SeccionIa(anomalias, prediccionesDisponibles, predicciones);
    }

    private static AnomaliaInforme aAnomaliaInforme(AnomaliaPrecio a) {
        return new AnomaliaInforme(
                a.getCompra().getNumeroFactura(),
                a.getCompra().getFechaCompra().atZone(ZoneOffset.UTC).toLocalDate(),
                a.getMateriaPrima().getCodigoMateriaPrima(),
                a.getMateriaPrima().getNombreMateriaPrima(),
                a.getPrecioIngresado(),
                a.getPrecioPromedioHistorico(),
                a.getZScore(),
                a.getClasificacion() != null ? a.getClasificacion().name() : null,
                a.getUsuarioConfirmo());
    }

    private static double redondear(double valor) {
        return Math.round(valor * 100.0) / 100.0;
    }

    // Acumuladores mutables locales a la agregación (no salen del servicio).
    private static final class AcumuladorProveedor {
        final String nombre;
        int compras;
        double monto;
        double kg;
        final Map<String, Double> gastoPorMp = new LinkedHashMap<>();

        AcumuladorProveedor(String nombre) {
            this.nombre = nombre;
        }

        String materiaPrincipal() {
            return gastoPorMp.entrySet().stream()
                    .max(Map.Entry.comparingByValue())
                    .map(Map.Entry::getKey)
                    .orElse(null);
        }
    }

    private static final class AcumuladorMateria {
        final String nombre;
        double kg;
        double monto;
        double precioMin = Double.MAX_VALUE;
        double precioMax;

        AcumuladorMateria(String nombre) {
            this.nombre = nombre;
        }
    }

    private static final class AcumuladorFormula {
        final String descripcion;
        int fabricaciones;
        double kg;
        double costo;

        AcumuladorFormula(String descripcion) {
            this.descripcion = descripcion;
        }
    }
}
