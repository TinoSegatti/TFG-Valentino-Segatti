package com.reforma.domain.prediccion.support;

/**
 * Kilos agregados de una materia prima en un mes ({@code "YYYY-MM"}), para armar la serie mensual de
 * ingresos (compras) o consumo (fabricaciones) de la predicción de agotamiento (RF-IA-PRED).
 */
public record AgregadoMensualMateria(Long idMateriaPrima, String mes, Double kilos) {}
