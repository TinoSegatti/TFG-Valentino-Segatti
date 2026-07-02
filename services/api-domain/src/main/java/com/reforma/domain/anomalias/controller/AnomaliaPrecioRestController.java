package com.reforma.domain.anomalias.controller;

import com.reforma.domain.anomalias.dto.AnomaliaEvaluacionResponse;
import com.reforma.domain.anomalias.dto.AnomaliaHistorialResponse;
import com.reforma.domain.anomalias.dto.ConfirmarAnomaliaRequest;
import com.reforma.domain.anomalias.dto.EvaluarAnomaliaRequest;
import com.reforma.domain.anomalias.service.AnomaliaPrecioService;
import com.reforma.domain.auth.SecurityUtils;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Detección y consulta de anomalías de precio (RF-IA-ANOM-*). Todo scopeado al tenant. */
@RestController
@RequestMapping("/api/ml/anomalias")
@RequiredArgsConstructor
public class AnomaliaPrecioRestController {

    private static final int LIMITE_MAX = 200;

    private final AnomaliaPrecioService anomaliaPrecioService;

    /** Evalúa un precio en el formulario de compra (preview, RF-IA-ANOM-001). */
    @PostMapping("/{idGranja}/evaluar")
    public AnomaliaEvaluacionResponse evaluar(
            @PathVariable String idGranja, @Valid @RequestBody EvaluarAnomaliaRequest request) {
        return anomaliaPrecioService.evaluar(SecurityUtils.requireTenantId(), idGranja, request);
    }

    /** Historial de anomalías del tenant (RF-IA-ANOM-006). */
    @GetMapping("/{idGranja}")
    public List<AnomaliaHistorialResponse> listar(
            @PathVariable String idGranja,
            @RequestParam(defaultValue = "100") int limite) {
        return anomaliaPrecioService.listar(
                SecurityUtils.requireTenantId(), idGranja, Math.min(limite, LIMITE_MAX));
    }

    /** Anomalías de las compras de un proveedor (ficha de proveedor, RF-IA-ANOM-006). */
    @GetMapping("/{idGranja}/proveedor/{idProveedor}")
    public List<AnomaliaHistorialResponse> listarPorProveedor(
            @PathVariable String idGranja,
            @PathVariable Long idProveedor,
            @RequestParam(defaultValue = "100") int limite) {
        return anomaliaPrecioService.listarPorProveedor(
                SecurityUtils.requireTenantId(), idGranja, idProveedor, Math.min(limite, LIMITE_MAX));
    }

    /** Confirma o rechaza una anomalía detectada (RF-IA-ANOM-004). */
    @PutMapping("/{idGranja}/{idAnomalia}/confirmar")
    public ResponseEntity<Void> confirmar(
            @PathVariable String idGranja,
            @PathVariable String idAnomalia,
            @Valid @RequestBody ConfirmarAnomaliaRequest request) {
        anomaliaPrecioService.confirmar(
                SecurityUtils.requireTenantId(), idGranja, idAnomalia, request.confirmo());
        return ResponseEntity.noContent().build();
    }
}
