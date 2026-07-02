package com.reforma.domain.compras.dto;

/**
 * Total comprado de una materia prima en el conjunto de compras REGISTRADAS y activas de la granja:
 * suma de kilos comprados de esa MP. Alimenta el gráfico "materias primas más compradas".
 */
public record MateriaPrimaCompradaResponse(
        String codigoMateriaPrima, String nombreMateriaPrima, double totalKg) {}
