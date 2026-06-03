package com.reforma.domain.proveedores.dto;

import com.reforma.domain.proveedores.entity.Proveedor;
import java.time.Instant;

public record ProveedorResponse(
        Long id,
        String idGranja,
        String codigoProveedor,
        String nombreProveedor,
        String telefono,
        String email,
        String cuit,
        String direccion,
        String localidad,
        String notas,
        boolean activo,
        Instant fechaCreacion,
        Instant fechaUltimaActualizacion) {

    public static ProveedorResponse from(Proveedor p) {
        return new ProveedorResponse(
                p.getId(),
                p.getGranja().getId(),
                p.getCodigoProveedor(),
                p.getNombreProveedor(),
                p.getTelefono(),
                p.getEmail(),
                p.getCuit(),
                p.getDireccion(),
                p.getLocalidad(),
                p.getNotas(),
                Boolean.TRUE.equals(p.getActivo()),
                p.getFechaCreacion(),
                p.getFechaUltimaActualizacion());
    }
}
