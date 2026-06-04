package com.reforma.domain.compras.dto;

import com.reforma.domain.compras.domain.EstadoCompra;
import com.reforma.domain.compras.entity.CompraCabecera;
import com.reforma.domain.compras.entity.CompraDetalle;
import com.reforma.domain.compras.support.CompraCalculo;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

public record CompraCompletaResponse(
        String id,
        String numeroFactura,
        LocalDate fechaCompra,
        Double totalFactura,
        String observaciones,
        EstadoCompra estado,
        Long idProveedor,
        String codigoProveedor,
        String nombreProveedor,
        Double sumaSubtotales,
        List<CompraDetalleLineResponse> lineas,
        boolean requiereAjusteDetalle) {

    public static CompraCompletaResponse from(CompraCabecera cabecera) {
        return from(cabecera, false);
    }

    public static CompraCompletaResponse from(CompraCabecera cabecera, boolean requiereAjusteDetalle) {
        double suma =
                cabecera.getDetalles().stream().mapToDouble(CompraDetalle::getSubtotal).sum();
        List<CompraDetalleLineResponse> lineas =
                cabecera.getDetalles().stream().map(CompraDetalleLineResponse::from).toList();
        return new CompraCompletaResponse(
                cabecera.getId(),
                cabecera.getNumeroFactura(),
                cabecera.getFechaCompra().atZone(ZoneOffset.UTC).toLocalDate(),
                CompraCalculo.redondear(cabecera.getTotalFactura()),
                cabecera.getObservaciones(),
                cabecera.getEstado(),
                cabecera.getProveedor().getId(),
                cabecera.getCodigoProveedorSnapshot(),
                cabecera.getNombreProveedorSnapshot(),
                CompraCalculo.redondear(suma),
                lineas,
                requiereAjusteDetalle);
    }
}
