package com.reforma.domain.fabricaciones.dto;

import com.reforma.domain.fabricaciones.entity.FabricacionDetalle;
import com.reforma.domain.fabricaciones.support.FabricacionCalculo;

public record FabricacionDetalleLineResponse(
        String id,
        Long idMateriaPrima,
        String codigoMateriaPrima,
        String nombreMateriaPrima,
        Double cantidadUsada,
        Double precioUnitario,
        Double costoParcial) {

    public static FabricacionDetalleLineResponse from(FabricacionDetalle detalle) {
        return new FabricacionDetalleLineResponse(
                detalle.getId(),
                detalle.getMateriaPrima().getId(),
                detalle.getCodigoMpSnapshot(),
                detalle.getNombreMpSnapshot(),
                FabricacionCalculo.redondear(detalle.getCantidadUsada()),
                FabricacionCalculo.redondear(detalle.getPrecioUnitario()),
                FabricacionCalculo.redondear(detalle.getCostoParcial()));
    }
}
