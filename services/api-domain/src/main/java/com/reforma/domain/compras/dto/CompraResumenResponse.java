package com.reforma.domain.compras.dto;

import com.reforma.domain.compras.entity.CompraCabecera;
import com.reforma.domain.compras.entity.CompraDetalle;
import com.reforma.domain.compras.domain.EstadoCompra;
import com.reforma.domain.compras.support.CompraCalculo;
import java.time.LocalDate;
import java.time.ZoneOffset;

public record CompraResumenResponse(
        String id,
        String numeroFactura,
        LocalDate fechaCompra,
        Double totalFactura,
        Long idProveedor,
        String codigoProveedor,
        String nombreProveedor,
        EstadoCompra estado,
        int cantidadLineas,
        Double sumaSubtotales) {

    public static CompraResumenResponse from(CompraCabecera cabecera) {
        double suma =
                cabecera.getDetalles().stream().mapToDouble(CompraDetalle::getSubtotal).sum();
        return new CompraResumenResponse(
                cabecera.getId(),
                cabecera.getNumeroFactura(),
                cabecera.getFechaCompra().atZone(ZoneOffset.UTC).toLocalDate(),
                CompraCalculo.redondear(cabecera.getTotalFactura()),
                cabecera.getProveedor().getId(),
                cabecera.getCodigoProveedorSnapshot(),
                cabecera.getNombreProveedorSnapshot(),
                cabecera.getEstado(),
                cabecera.getDetalles().size(),
                CompraCalculo.redondear(suma));
    }
}
