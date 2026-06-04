package com.reforma.domain.formulas.dto;

import com.reforma.domain.formulas.entity.FormulaCabecera;
import com.reforma.domain.formulas.support.FormulaCalculo;

public record FormulaResumenResponse(
        String id,
        String codigoFormula,
        String descripcionFormula,
        Long idAnimal,
        String codigoAnimal,
        String descripcionAnimal,
        double costoTotalFormula,
        boolean completa) {

    public static FormulaResumenResponse from(FormulaCabecera cabecera) {
        double sumaKg = cabecera.getDetalles().stream().mapToDouble(d -> d.getCantidadKg()).sum();
        return new FormulaResumenResponse(
                cabecera.getId(),
                cabecera.getCodigoFormula(),
                cabecera.getDescripcionFormula(),
                cabecera.getAnimal().getId(),
                cabecera.getAnimal().getCodigoAnimal(),
                cabecera.getAnimal().getDescripcionAnimal(),
                FormulaCalculo.redondear(cabecera.getCostoTotalFormula()),
                FormulaCalculo.sumaKgCompleta(sumaKg));
    }
}
