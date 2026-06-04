package com.reforma.domain.formulas.dto;

import com.reforma.domain.formulas.entity.FormulaDetalle;
import com.reforma.domain.formulas.support.FormulaCalculo;

public record FormulaDetalleLineResponse(
        String id,
        Long idMateriaPrima,
        String codigoMateriaPrima,
        String nombreMateriaPrima,
        double cantidadKg,
        double porcentajeFormula,
        double precioPorKilo,
        double costoParcial) {

    public static FormulaDetalleLineResponse from(FormulaDetalle linea) {
        return new FormulaDetalleLineResponse(
                linea.getId(),
                linea.getMateriaPrima().getId(),
                linea.getMateriaPrima().getCodigoMateriaPrima(),
                linea.getMateriaPrima().getNombreMateriaPrima(),
                FormulaCalculo.redondear(linea.getCantidadKg()),
                FormulaCalculo.redondear(linea.getPorcentajeFormula()),
                FormulaCalculo.redondear(linea.getPrecioUnitarioMomentoCreacion()),
                FormulaCalculo.redondear(linea.getCostoParcial()));
    }
}
