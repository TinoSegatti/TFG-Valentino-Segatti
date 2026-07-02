package com.reforma.domain.prediccion.dto;

import java.util.List;

/** Predicción de una MP con las series para el gráfico del popup (RF-IA-PRED). */
public record PrediccionStockDetalleResponse(
        PrediccionStockResponse resumen,
        List<PuntoSerieResponse> serieHistorica,
        List<PuntoSerieResponse> serieProyeccion) {}
