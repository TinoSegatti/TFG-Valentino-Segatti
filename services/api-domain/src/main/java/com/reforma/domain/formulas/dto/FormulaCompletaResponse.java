package com.reforma.domain.formulas.dto;

import com.reforma.domain.formulas.entity.FormulaCabecera;
import com.reforma.domain.formulas.support.FormulaCalculo;
import java.util.List;

public record FormulaCompletaResponse(
        String id,
        String codigoFormula,
        String descripcionFormula,
        Long idAnimal,
        String codigoAnimal,
        String descripcionAnimal,
        double pesoTotalFormula,
        double costoTotalFormula,
        double sumaKg,
        double kilosFaltantes,
        boolean completa,
        List<FormulaDetalleLineResponse> lineas) {

    public static FormulaCompletaResponse from(FormulaCabecera cabecera) {
        double sumaKg = FormulaCalculo.redondear(
                cabecera.getDetalles().stream().mapToDouble(d -> d.getCantidadKg()).sum());
        return new FormulaCompletaResponse(
                cabecera.getId(),
                cabecera.getCodigoFormula(),
                cabecera.getDescripcionFormula(),
                cabecera.getAnimal().getId(),
                cabecera.getAnimal().getCodigoAnimal(),
                cabecera.getAnimal().getDescripcionAnimal(),
                FormulaCalculo.redondear(cabecera.getPesoTotalFormula()),
                FormulaCalculo.redondear(cabecera.getCostoTotalFormula()),
                sumaKg,
                FormulaCalculo.kilosFaltantes(sumaKg),
                FormulaCalculo.sumaKgCompleta(sumaKg),
                cabecera.getDetalles().stream().map(FormulaDetalleLineResponse::from).toList());
    }
}
