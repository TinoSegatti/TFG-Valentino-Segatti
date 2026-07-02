package com.reforma.domain.fabricaciones.controller;

import com.reforma.domain.auth.SecurityUtils;
import com.reforma.domain.fabricaciones.dto.FabricacionCabeceraRequest;
import com.reforma.domain.fabricaciones.dto.FabricacionCompletaResponse;
import com.reforma.domain.fabricaciones.dto.FabricacionResumenResponse;
import com.reforma.domain.fabricaciones.dto.GuardarFabricacionDetalleRequest;
import com.reforma.domain.fabricaciones.dto.MateriaPrimaConsumoResponse;
import com.reforma.domain.fabricaciones.service.FabricacionService;
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
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/fabricaciones/{idGranja}")
@RequiredArgsConstructor
public class FabricacionRestController {

    private final FabricacionService fabricacionService;

    @GetMapping
    public List<FabricacionResumenResponse> listar(@PathVariable String idGranja) {
        return fabricacionService.listar(SecurityUtils.requireTenantId(), idGranja);
    }

    /**
     * Consumo agregado de materias primas en todas las fabricaciones registradas (kilos por MP).
     * Ruta literal: Spring la prioriza sobre la variable {@code /{idFabricacion}}.
     */
    @GetMapping("/consumo-materias")
    public List<MateriaPrimaConsumoResponse> consumoMaterias(@PathVariable String idGranja) {
        return fabricacionService.consumoMaterias(SecurityUtils.requireTenantId(), idGranja);
    }

    @GetMapping("/{idFabricacion}")
    public FabricacionCompletaResponse obtener(
            @PathVariable String idGranja, @PathVariable String idFabricacion) {
        return fabricacionService.obtener(SecurityUtils.requireTenantId(), idGranja, idFabricacion);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public FabricacionCompletaResponse crearCabecera(
            @PathVariable String idGranja, @Valid @RequestBody FabricacionCabeceraRequest request) {
        return fabricacionService.crearCabecera(SecurityUtils.requireTenantId(), idGranja, request);
    }

    @PutMapping("/{idFabricacion}")
    public FabricacionCompletaResponse actualizarCabecera(
            @PathVariable String idGranja,
            @PathVariable String idFabricacion,
            @Valid @RequestBody FabricacionCabeceraRequest request) {
        return fabricacionService.actualizarCabecera(
                SecurityUtils.requireTenantId(), idGranja, idFabricacion, request);
    }

    @DeleteMapping("/{idFabricacion}")
    public ResponseEntity<Void> eliminar(
            @PathVariable String idGranja, @PathVariable String idFabricacion) {
        fabricacionService.eliminar(SecurityUtils.requireTenantId(), idGranja, idFabricacion);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{idFabricacion}/detalle")
    public FabricacionCompletaResponse guardarDetalle(
            @PathVariable String idGranja,
            @PathVariable String idFabricacion,
            @Valid @RequestBody GuardarFabricacionDetalleRequest request) {
        return fabricacionService.guardarDetalle(
                SecurityUtils.requireTenantId(), idGranja, idFabricacion, request);
    }
}
