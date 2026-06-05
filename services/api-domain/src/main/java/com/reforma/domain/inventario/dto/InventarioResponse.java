package com.reforma.domain.inventario.dto;

import com.reforma.domain.inventario.entity.Inventario;
import com.reforma.domain.inventario.support.InventarioCalculo;
import com.reforma.domain.inventario.support.InventarioValoresCalculados;
import com.reforma.domain.materiasprimas.entity.MateriaPrima;
import java.time.Instant;

public record InventarioResponse(
        String id,
        Long idMateriaPrima,
        String codigoMateriaPrima,
        String nombreMateriaPrima,
        double precioPorKilo,
        double cantidadAcumulada,
        double cantidadSistema,
        double cantidadReal,
        double merma,
        double precioAlmacen,
        double valorStock,
        int version,
        Instant fechaUltimaActualizacion) {

    public static InventarioResponse from(Inventario inv) {
        return new InventarioResponse(
                inv.getId(),
                inv.getMateriaPrima().getId(),
                inv.getMateriaPrima().getCodigoMateriaPrima(),
                inv.getMateriaPrima().getNombreMateriaPrima(),
                InventarioCalculo.redondear(
                        inv.getMateriaPrima().getPrecioPorKilo() != null
                                ? inv.getMateriaPrima().getPrecioPorKilo()
                                : 0.0),
                InventarioCalculo.redondear(inv.getCantidadAcumulada()),
                InventarioCalculo.redondear(inv.getCantidadSistema()),
                InventarioCalculo.redondear(inv.getCantidadReal()),
                InventarioCalculo.redondear(inv.getMerma()),
                InventarioCalculo.redondear(inv.getPrecioAlmacen()),
                InventarioCalculo.redondear(inv.getValorStock()),
                inv.getVersion() != null ? inv.getVersion() : 0,
                inv.getFechaUltimaActualizacion());
    }

    /** Vista calculada para MPs activas sin fila persistida en {@code t_inventario}. */
    public static InventarioResponse vista(MateriaPrima mp, InventarioValoresCalculados valores) {
        return new InventarioResponse(
                null,
                mp.getId(),
                mp.getCodigoMateriaPrima(),
                mp.getNombreMateriaPrima(),
                InventarioCalculo.redondear(
                        mp.getPrecioPorKilo() != null ? mp.getPrecioPorKilo() : 0.0),
                valores.cantidadAcumulada(),
                valores.cantidadSistema(),
                valores.cantidadReal(),
                valores.merma(),
                valores.precioAlmacen(),
                valores.valorStock(),
                0,
                null);
    }
}
