package com.reforma.domain.archivos.controller;

import com.reforma.domain.archivos.domain.TipoModuloArchivo;
import com.reforma.domain.archivos.dto.ArchivoCrearRequest;
import com.reforma.domain.archivos.dto.ArchivoDetalleResponse;
import com.reforma.domain.archivos.dto.ArchivoResumenResponse;
import com.reforma.domain.archivos.service.ArchivoService;
import com.reforma.domain.auth.SecurityUtils;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Módulo Archivos. Sin PUT/DELETE a propósito: un archivo es inmutable. El POST queda
 * vedado al rol LECTOR por la regla GRANJA_SCOPED de {@code SecurityConfig}. El creador
 * auditado es el usuario real (empleado o dueño), no el tenant.
 */
@RestController
@RequestMapping("/api/archivos/{idGranja}")
@RequiredArgsConstructor
public class ArchivoRestController {

    private final ArchivoService archivoService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ArchivoResumenResponse crear(
            @PathVariable String idGranja, @Valid @RequestBody ArchivoCrearRequest request) {
        var principal = SecurityUtils.requirePrincipal();
        return archivoService.crear(
                principal.tenantId(), principal.id(), principal.email(), idGranja, request);
    }

    @GetMapping
    public List<ArchivoResumenResponse> listar(
            @PathVariable String idGranja,
            @RequestParam(required = false) TipoModuloArchivo tipo) {
        return archivoService.listar(SecurityUtils.requireTenantId(), idGranja, tipo);
    }

    @GetMapping("/{idArchivo}")
    public ArchivoDetalleResponse obtener(
            @PathVariable String idGranja, @PathVariable Long idArchivo) {
        return archivoService.obtener(SecurityUtils.requireTenantId(), idGranja, idArchivo);
    }
}
