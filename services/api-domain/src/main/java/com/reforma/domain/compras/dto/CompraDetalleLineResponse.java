package com.reforma.domain.compras.dto;

import com.reforma.domain.compras.entity.CompraDetalle;
import com.reforma.domain.compras.support.CompraCalculo;

public record CompraDetalleLineResponse(
        String id,
        Long idMateriaPrima,
        String codigoMateriaPrima,
        String nombreMateriaPrima,
        Double cantidadKg,
        Double precioPorKilo,
        Double subtotal,
        Double precioAnteriorMateriaPrima,
        String codigoMpSnapshot,
        String nombreMpSnapshot) {

    public static CompraDetalleLineResponse from(CompraDetalle detalle) {
        return new CompraDetalleLineResponse(
                detalle.getId(),
                detalle.getMateriaPrima().getId(),
                detalle.getMateriaPrima().getCodigoMateriaPrima(),
                detalle.getMateriaPrima().getNombreMateriaPrima(),
                CompraCalculo.redondear(detalle.getCantidadComprada()),
                CompraCalculo.redondear(detalle.getPrecioUnitario()),
                CompraCalculo.redondear(detalle.getSubtotal()),
                CompraCalculo.redondear(detalle.getPrecioAnteriorMateriaPrima()),
                detalle.getCodigoMpSnapshot(),
                detalle.getNombreMpSnapshot());
    }
}
