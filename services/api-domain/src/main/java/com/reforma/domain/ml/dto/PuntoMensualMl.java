package com.reforma.domain.ml.dto;

/** Actividad mensual de una MP (kilos) enviada a api-ml para la predicción de stock. */
public record PuntoMensualMl(String mes, double ingresos, double consumo) {}
