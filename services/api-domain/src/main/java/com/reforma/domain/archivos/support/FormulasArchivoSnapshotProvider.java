package com.reforma.domain.archivos.support;

import com.reforma.domain.archivos.domain.TipoModuloArchivo;
import com.reforma.domain.formulas.dto.FormulaCompletaResponse;
import com.reforma.domain.formulas.repository.FormulaCabeceraRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Archiva las fórmulas activas con sus ingredientes (costo y kg al momento del archivo). */
@Component
@RequiredArgsConstructor
class FormulasArchivoSnapshotProvider implements ArchivoSnapshotProvider {

    private final FormulaCabeceraRepository formulaCabeceraRepository;

    @Override
    public TipoModuloArchivo tipo() {
        return TipoModuloArchivo.FORMULAS;
    }

    @Override
    public ArchivoSnapshot capturar(String idTenant, String idGranja) {
        List<FormulaCompletaResponse> formulas = formulaCabeceraRepository
                .findByGranjaIdAndActivaTrueOrderByCodigoFormulaAsc(idGranja)
                .stream()
                .map(FormulaCompletaResponse::from)
                .toList();
        return new ArchivoSnapshot(formulas, formulas.size());
    }
}
