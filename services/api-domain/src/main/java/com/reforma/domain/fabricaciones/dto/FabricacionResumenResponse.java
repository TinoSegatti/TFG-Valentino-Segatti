package com.reforma.domain.fabricaciones.dto;

import com.reforma.domain.fabricaciones.domain.EstadoFabricacion;
import com.reforma.domain.fabricaciones.entity.Fabricacion;
import com.reforma.domain.fabricaciones.support.FabricacionCalculo;
import java.time.LocalDate;
import java.time.ZoneOffset;

public record FabricacionResumenResponse(
        String id,
        String codigoFabricacion,
        LocalDate fechaFabricacion,
        String descripcionFabricacion,
        EstadoFabricacion estado,
        String codigoFormula,
        Double veces,
        Double costoTotalFabricacion,
        Boolean sinExistencias) {

    public static FabricacionResumenResponse from(Fabricacion fabricacion) {
        return new FabricacionResumenResponse(
                fabricacion.getId(),
                fabricacion.getCodigoFabricacion(),
                fabricacion.getFechaFabricacion().atZone(ZoneOffset.UTC).toLocalDate(),
                fabricacion.getDescripcionFabricacion(),
                fabricacion.getEstado(),
                fabricacion.getCodigoFormulaSnapshot(),
                FabricacionCalculo.kgToVeces(
                        fabricacion.getCantidadFabricacion() != null ? fabricacion.getCantidadFabricacion() : 0.0),
                FabricacionCalculo.redondear(fabricacion.getCostoTotalFabricacion()),
                fabricacion.getSinExistencias());
    }
}
