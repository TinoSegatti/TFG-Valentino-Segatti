package com.reforma.domain.prediccion.dto;

/** Un punto de la serie de existencias (histórico o proyección) para el gráfico. */
public record PuntoSerieResponse(String mes, double existencias) {}
