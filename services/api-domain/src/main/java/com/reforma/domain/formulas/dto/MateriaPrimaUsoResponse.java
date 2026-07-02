package com.reforma.domain.formulas.dto;

/**
 * Uso agregado de una materia prima en el conjunto de fórmulas activas de la granja:
 * suma de kilos formulados de esa MP a lo largo de todas las fórmulas. Alimenta el
 * gráfico "materias primas más usadas en fórmulas".
 */
public record MateriaPrimaUsoResponse(
        String codigoMateriaPrima, String nombreMateriaPrima, double totalKg) {}
