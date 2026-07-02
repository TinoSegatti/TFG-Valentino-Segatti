package com.reforma.domain.prediccion.controller;

import com.reforma.domain.auth.SecurityUtils;
import com.reforma.domain.prediccion.dto.PrediccionStockDetalleResponse;
import com.reforma.domain.prediccion.dto.PrediccionStockResponse;
import com.reforma.domain.prediccion.service.PrediccionStockService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Predicción de agotamiento de stock (RF-IA-PRED). Todo scopeado al tenant y gateado por plan
 * (BUSINESS/ENTERPRISE) en el servicio.
 */
@RestController
@RequestMapping("/api/ml/prediccion")
@RequiredArgsConstructor
public class PrediccionStockRestController {

    private final PrediccionStockService prediccionStockService;

    /** Resumen por MP (para el indicador de riesgo de la tabla de inventario). */
    @GetMapping("/{idGranja}")
    public List<PrediccionStockResponse> predecirGranja(@PathVariable String idGranja) {
        return prediccionStockService.predecirGranja(SecurityUtils.requireTenantId(), idGranja);
    }

    /** Detalle con series (histórico + proyección) de una MP para el gráfico del popup. */
    @GetMapping("/{idGranja}/materia-prima/{idMateriaPrima}")
    public PrediccionStockDetalleResponse predecirMateriaPrima(
            @PathVariable String idGranja, @PathVariable Long idMateriaPrima) {
        return prediccionStockService.predecirMateriaPrima(
                SecurityUtils.requireTenantId(), idGranja, idMateriaPrima);
    }
}
