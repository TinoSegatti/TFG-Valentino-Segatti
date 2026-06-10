package com.reforma.domain.fabricaciones.dto;

import com.reforma.domain.fabricaciones.domain.EstadoFabricacion;
import com.reforma.domain.fabricaciones.entity.Fabricacion;
import com.reforma.domain.fabricaciones.support.FabricacionCalculo;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

public record FabricacionCompletaResponse(
        String id,
        String codigoFabricacion,
        LocalDate fechaFabricacion,
        String descripcionFabricacion,
        String observaciones,
        EstadoFabricacion estado,
        String idFormula,
        String codigoFormula,
        String descripcionFormula,
        Double veces,
        Double kilosProducidos,
        Double costoUnitarioFormula,
        Double costoTotalFabricacion,
        Double costoPorKilo,
        Boolean sinExistencias,
        List<FabricacionDetalleLineResponse> lineas) {

    public static FabricacionCompletaResponse from(Fabricacion fabricacion) {
        double kilos = fabricacion.getCantidadFabricacion() != null ? fabricacion.getCantidadFabricacion() : 0.0;
        List<FabricacionDetalleLineResponse> lineas =
                fabricacion.getDetalles().stream().map(FabricacionDetalleLineResponse::from).toList();
        return new FabricacionCompletaResponse(
                fabricacion.getId(),
                fabricacion.getCodigoFabricacion(),
                fabricacion.getFechaFabricacion().atZone(ZoneOffset.UTC).toLocalDate(),
                fabricacion.getDescripcionFabricacion(),
                fabricacion.getObservaciones(),
                fabricacion.getEstado(),
                fabricacion.getFormula() != null ? fabricacion.getFormula().getId() : null,
                fabricacion.getCodigoFormulaSnapshot(),
                fabricacion.getDescripcionFormulaSnapshot(),
                FabricacionCalculo.kgToVeces(kilos),
                FabricacionCalculo.redondear(kilos),
                fabricacion.getCostoUnitarioFormulaSnapshot() != null
                        ? FabricacionCalculo.redondear(fabricacion.getCostoUnitarioFormulaSnapshot())
                        : null,
                FabricacionCalculo.redondear(fabricacion.getCostoTotalFabricacion()),
                FabricacionCalculo.redondear(fabricacion.getCostoPorKilo()),
                fabricacion.getSinExistencias(),
                lineas);
    }
}
