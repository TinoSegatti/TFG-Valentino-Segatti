package com.reforma.domain.anomalias.dto;

/** Datos mínimos de una línea de compra para evaluar/registrar su anomalía de precio. */
public record LineaAnomaliaInput(Long idMateriaPrima, double precio) {}
