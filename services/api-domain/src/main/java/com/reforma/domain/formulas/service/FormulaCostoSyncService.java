package com.reforma.domain.formulas.service;

import com.reforma.domain.formulas.entity.FormulaCabecera;
import com.reforma.domain.formulas.entity.FormulaDetalle;
import com.reforma.domain.formulas.repository.FormulaCabeceraRepository;
import com.reforma.domain.formulas.support.FormulaCalculo;
import com.reforma.domain.materiasprimas.repository.MateriaPrimaRepository;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Recalcula costos de formulas activas cuando cambia el precio x kilo de una materia prima
 * (p. ej. tras registrar o ajustar una compra).
 */
@Service
@RequiredArgsConstructor
public class FormulaCostoSyncService {

    private final FormulaCabeceraRepository formulaCabeceraRepository;
    private final MateriaPrimaRepository materiaPrimaRepository;

    @Transactional
    public void recalcularPorMateriasPrimas(String idGranja, Collection<Long> idsMaterias) {
        Set<String> formulasProcesadas = new LinkedHashSet<>();
        for (Long idMateria : idsMaterias) {
            for (FormulaCabecera formula :
                    formulaCabeceraRepository.findActivasConMateriaPrima(idGranja, idMateria)) {
                if (formulasProcesadas.add(formula.getId())) {
                    recalcularCostosFormula(formula);
                }
            }
        }
    }

    void recalcularCostosFormula(FormulaCabecera formula) {
        if (formula.getDetalles().isEmpty()) {
            formula.setCostoTotalFormula(0.0);
            return;
        }
        double pesoTotal = formula.getPesoTotalFormula();
        double costoTotal = 0.0;
        for (FormulaDetalle linea : formula.getDetalles()) {
            Long idMp = linea.getMateriaPrima().getId();
            double precio = materiaPrimaRepository
                    .findById(idMp)
                    .map(mp -> FormulaCalculo.redondear(mp.getPrecioPorKilo()))
                    .orElse(linea.getPrecioUnitarioMomentoCreacion());
            linea.setPrecioUnitarioMomentoCreacion(precio);
            linea.setPorcentajeFormula(FormulaCalculo.calcularPorcentaje(linea.getCantidadKg(), pesoTotal));
            linea.setCostoParcial(FormulaCalculo.calcularCostoParcial(linea.getCantidadKg(), precio));
            costoTotal += linea.getCostoParcial();
        }
        formula.setCostoTotalFormula(FormulaCalculo.redondear(costoTotal));
    }
}
