package com.reforma.domain.prediccion.dto;

import java.time.LocalDate;

/**
 * Resumen de la predicción de agotamiento de una materia prima (RF-IA-PRED). Alimenta el indicador
 * de riesgo de la tabla de inventario.
 */
public record PrediccionStockResponse(
        Long idMateriaPrima,
        String codigoMateriaPrima,
        String nombreMateriaPrima,
        String nivelAlerta,
        String tendencia,
        double stockActual,
        Integer diasRestantes,
        LocalDate fechaAgotamiento,
        double netoPromedio,
        double consumoPromedio,
        double ingresoPromedio,
        int nMeses,
        String modeloUsado) {}
