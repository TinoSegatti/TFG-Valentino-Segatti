package com.reforma.domain.fabricaciones.dto;

/**
 * Consumo agregado de una materia prima en el conjunto de fabricaciones REGISTRADAS y activas
 * de la granja: suma de kilos usados de esa MP. Alimenta el gráfico "consumo de materias primas".
 */
public record MateriaPrimaConsumoResponse(
        String codigoMateriaPrima, String nombreMateriaPrima, double totalKg) {}
