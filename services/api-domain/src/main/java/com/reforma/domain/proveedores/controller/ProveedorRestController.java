package com.reforma.domain.proveedores.controller;

import com.reforma.domain.auth.SecurityUtils;
import com.reforma.domain.proveedores.dto.ProveedorRequest;
import com.reforma.domain.proveedores.dto.ProveedorResponse;
import com.reforma.domain.proveedores.service.ProveedorService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoints REST del módulo Proveedores (RF-PROV-*). Multi-tenant por path:
 * {@code /api/proveedores/{idGranja}[/{id}]}. El {@code idUsuario} sale del JWT
 * vía {@link SecurityUtils#requireUserId()}, no del cliente.
 */
@RestController
@RequestMapping("/api/proveedores/{idGranja}")
@RequiredArgsConstructor
public class ProveedorRestController {

    private final ProveedorService proveedorService;

    @GetMapping
    public List<ProveedorResponse> listar(
            @PathVariable String idGranja,
            @RequestParam(name = "buscar", required = false) String buscar) {
        return proveedorService.listarPorGranja(SecurityUtils.requireUserId(), idGranja, buscar);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ProveedorResponse crear(
            @PathVariable String idGranja, @Valid @RequestBody ProveedorRequest request) {
        return proveedorService.crear(SecurityUtils.requireUserId(), idGranja, request);
    }

    @PutMapping("/{idProveedor}")
    public ProveedorResponse actualizar(
            @PathVariable String idGranja,
            @PathVariable Long idProveedor,
            @Valid @RequestBody ProveedorRequest request) {
        return proveedorService.actualizar(
                SecurityUtils.requireUserId(), idGranja, idProveedor, request);
    }

    @DeleteMapping("/{idProveedor}")
    public ResponseEntity<Void> desactivar(
            @PathVariable String idGranja, @PathVariable Long idProveedor) {
        proveedorService.desactivar(SecurityUtils.requireUserId(), idGranja, idProveedor);
        return ResponseEntity.noContent().build();
    }
}
