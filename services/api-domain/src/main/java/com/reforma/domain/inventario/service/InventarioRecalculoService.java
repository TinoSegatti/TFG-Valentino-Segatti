package com.reforma.domain.inventario.service;

import com.reforma.domain.common.util.IdGenerator;
import com.reforma.domain.compras.domain.EstadoCompra;
import com.reforma.domain.compras.repository.CompraDetalleRepository;
import com.reforma.domain.compras.support.CompraTotalesMateriaPrima;
import com.reforma.domain.fabricaciones.repository.FabricacionDetalleRepository;
import com.reforma.domain.granjas.entity.Granja;
import com.reforma.domain.granjas.repository.GranjaRepository;
import com.reforma.domain.inventario.entity.Inventario;
import com.reforma.domain.inventario.entity.InventarioInicial;
import com.reforma.domain.inventario.repository.InventarioInicialRepository;
import com.reforma.domain.inventario.repository.InventarioRepository;
import com.reforma.domain.inventario.support.InventarioCalculo;
import com.reforma.domain.inventario.support.InventarioValoresCalculados;
import com.reforma.domain.materiasprimas.entity.MateriaPrima;
import com.reforma.domain.materiasprimas.repository.MateriaPrimaRepository;
import java.time.Instant;
import java.util.Collection;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Recalcula registros de {@code t_inventario} para una granja a partir de compras REGISTRADAS,
 * inventario inicial y fabricaciones REGISTRADAS activas. Mantiene el ajuste manual del usuario en
 * {@code cantidad_real} preservando la diferencia (real - sistema).
 */
@Service
@RequiredArgsConstructor
public class InventarioRecalculoService {

    private final InventarioRepository inventarioRepository;
    private final InventarioInicialRepository inventarioInicialRepository;
    private final CompraDetalleRepository compraDetalleRepository;
    private final FabricacionDetalleRepository fabricacionDetalleRepository;
    private final MateriaPrimaRepository materiaPrimaRepository;
    private final GranjaRepository granjaRepository;

    @Transactional
    public void recalcularPorMaterias(String idGranja, Collection<Long> idsMaterias) {
        for (Long idMateria : idsMaterias) {
            recalcular(idGranja, idMateria);
        }
    }

    /** Calcula valores sin persistir (p. ej. MPs sin fila en {@code t_inventario}). */
    public InventarioValoresCalculados calcularValores(String idGranja, Long idMateriaPrima) {
        MateriaPrima mp = materiaPrimaRepository.findById(idMateriaPrima).orElseThrow();
        Optional<InventarioInicial> inicialOpt =
                inventarioInicialRepository.findByGranjaIdAndMateriaPrimaId(idGranja, idMateriaPrima);
        double cantidadInicial = inicialOpt.map(InventarioInicial::getCantidadInicial).orElse(0.0);
        double precioInicial = inicialOpt.map(InventarioInicial::getPrecioInicial).orElse(0.0);

        // Totales de compras REGISTRADAS y activas: SUM(cantidadComprada) y SUM(subtotal).
        CompraTotalesMateriaPrima totales = compraDetalleRepository.totalPorMateriaPrima(
                idGranja, idMateriaPrima, EstadoCompra.REGISTRADA);
        double kilosCompras = totales != null ? totales.totalKilos() : 0.0;
        double dineroCompras = totales != null ? totales.totalDinero() : 0.0;

        double cantidadAcumulada = InventarioCalculo.redondear(cantidadInicial + kilosCompras);
        double kilosUsadosFabricaciones = Optional.ofNullable(
                        fabricacionDetalleRepository.sumCantidadUsadaPorMateriaPrima(idGranja, idMateriaPrima))
                .orElse(0.0);
        double cantidadSistema = InventarioCalculo.redondear(cantidadAcumulada - kilosUsadosFabricaciones);

        // === PRECIO ALMACÉN: costo promedio ponderado de adquisición ===
        // Se reconstruye desde cero en cada recálculo, por lo que editar o eliminar una factura queda
        // reflejado automáticamente (los acumuladores se re-suman a partir de las líneas vigentes).
        //
        // Acumulador de gasto y de kilos comprados. La PRIMERA "iteración" del acumulador es el valor
        // del stock inicial (cantidad_inicial * precio_inicial); a partir de ahí cada compra suma su
        // subtotal y sus kilos. Las salidas por fabricación NO intervienen en este cálculo.
        double gastoStockInicial = cantidadInicial * precioInicial;
        double gastoAcumulado = gastoStockInicial + dineroCompras;
        double kilosAcumulados = cantidadInicial + kilosCompras;
        double precioAlmacen = InventarioCalculo.precioAlmacenPonderado(gastoAcumulado, kilosAcumulados);
        double precioVigente = mp.getPrecioPorKilo() != null ? mp.getPrecioPorKilo() : 0.0;

        Optional<Inventario> existenteOpt =
                inventarioRepository.findByGranjaIdAndMateriaPrimaId(idGranja, idMateriaPrima);
        double cantidadReal;
        if (existenteOpt.isPresent()) {
            Inventario existente = existenteOpt.get();
            double diferenciaManual = existente.getCantidadReal() - existente.getCantidadSistema();
            cantidadReal = InventarioCalculo.redondear(cantidadSistema + diferenciaManual);
        } else {
            cantidadReal = cantidadSistema;
        }

        return new InventarioValoresCalculados(
                cantidadAcumulada,
                cantidadSistema,
                cantidadReal,
                InventarioCalculo.merma(cantidadSistema, cantidadReal),
                precioAlmacen,
                InventarioCalculo.valorStock(cantidadReal, precioVigente));
    }

    @Transactional
    public Inventario recalcular(String idGranja, Long idMateriaPrima) {
        MateriaPrima mp = materiaPrimaRepository.findById(idMateriaPrima).orElseThrow();
        InventarioValoresCalculados valores = calcularValores(idGranja, idMateriaPrima);

        Inventario inv = inventarioRepository
                .findByGranjaIdAndMateriaPrimaId(idGranja, idMateriaPrima)
                .orElse(null);
        if (inv == null) {
            Granja granja = granjaRepository.findById(idGranja).orElseThrow();
            inv = Inventario.builder()
                    .id(IdGenerator.newId())
                    .granja(granja)
                    .materiaPrima(mp)
                    .cantidadAcumulada(valores.cantidadAcumulada())
                    .cantidadSistema(valores.cantidadSistema())
                    .cantidadReal(valores.cantidadReal())
                    .merma(valores.merma())
                    .precioAlmacen(valores.precioAlmacen())
                    .valorStock(valores.valorStock())
                    .fechaUltimaActualizacion(Instant.now())
                    .build();
        } else {
            inv.setCantidadAcumulada(valores.cantidadAcumulada());
            inv.setCantidadSistema(valores.cantidadSistema());
            inv.setCantidadReal(valores.cantidadReal());
            inv.setMerma(valores.merma());
            inv.setPrecioAlmacen(valores.precioAlmacen());
            inv.setValorStock(valores.valorStock());
            inv.setFechaUltimaActualizacion(Instant.now());
        }
        return inventarioRepository.save(inv);
    }
}
