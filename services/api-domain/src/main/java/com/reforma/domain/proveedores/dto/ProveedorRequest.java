package com.reforma.domain.proveedores.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Payload para alta/edición de Proveedor (RF-PROV-001).
 * El único campo obligatorio del negocio es el nombre; el código se usa
 * como identificador legible único dentro de la granja.
 */
public record ProveedorRequest(
        @NotBlank @Size(max = 50) String codigoProveedor,
        @NotBlank @Size(max = 200) String nombreProveedor,
        @Size(max = 50) String telefono,
        @Email @Size(max = 200) String email,
        @Size(max = 20) String cuit,
        @Size(max = 2000) String direccion,
        @Size(max = 100) String localidad,
        @Size(max = 2000) String notas) {}
