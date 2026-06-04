package com.reforma.domain.compras.controller;

import com.reforma.domain.auth.SecurityUtils;
import com.reforma.domain.compras.dto.CompraCabeceraRequest;
import com.reforma.domain.compras.dto.CompraCompletaResponse;
import com.reforma.domain.compras.dto.CompraResumenResponse;
import com.reforma.domain.compras.dto.GuardarCompraDetalleRequest;
import com.reforma.domain.compras.service.CompraService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.ResponseEntity;

@RestController
@RequestMapping("/api/compras/{idGranja}")
@RequiredArgsConstructor
public class CompraRestController {

    private final CompraService compraService;

    @GetMapping
    public List<CompraResumenResponse> listar(@PathVariable String idGranja) {
        return compraService.listar(SecurityUtils.requireUserId(), idGranja);
    }

    @GetMapping("/{idCompra}")
    public CompraCompletaResponse obtener(
            @PathVariable String idGranja, @PathVariable String idCompra) {
        return compraService.obtener(SecurityUtils.requireUserId(), idGranja, idCompra);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CompraCompletaResponse crearCabecera(
            @PathVariable String idGranja, @Valid @RequestBody CompraCabeceraRequest request) {
        return compraService.crearCabecera(SecurityUtils.requireUserId(), idGranja, request);
    }

    @PutMapping("/{idCompra}")
    public CompraCompletaResponse actualizarCabecera(
            @PathVariable String idGranja,
            @PathVariable String idCompra,
            @Valid @RequestBody CompraCabeceraRequest request) {
        return compraService.actualizarCabecera(
                SecurityUtils.requireUserId(), idGranja, idCompra, request);
    }

    @DeleteMapping("/{idCompra}")
    public ResponseEntity<Void> eliminarCabecera(
            @PathVariable String idGranja, @PathVariable String idCompra) {
        compraService.eliminarCabecera(SecurityUtils.requireUserId(), idGranja, idCompra);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{idCompra}/detalle")
    public CompraCompletaResponse guardarDetalle(
            @PathVariable String idGranja,
            @PathVariable String idCompra,
            @Valid @RequestBody GuardarCompraDetalleRequest request) {
        return compraService.guardarDetalle(
                SecurityUtils.requireUserId(), idGranja, idCompra, request);
    }
}
