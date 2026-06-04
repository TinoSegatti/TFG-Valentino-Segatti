package com.reforma.domain.formulas.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.reforma.domain.formulas.entity.FormulaCabecera;
import com.reforma.domain.formulas.entity.FormulaDetalle;
import com.reforma.domain.formulas.repository.FormulaCabeceraRepository;
import com.reforma.domain.granjas.entity.Granja;
import com.reforma.domain.materiasprimas.entity.MateriaPrima;
import com.reforma.domain.materiasprimas.repository.MateriaPrimaRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FormulaCostoSyncServiceTest {

    @Mock private FormulaCabeceraRepository formulaCabeceraRepository;
    @Mock private MateriaPrimaRepository materiaPrimaRepository;

    @InjectMocks private FormulaCostoSyncService service;

    @Test
    void recalcularPorMateriasPrimas_actualizaCostoTrasSubidaPrecio() {
        Granja granja = Granja.builder().id("g_demo").build();
        MateriaPrima trigo = MateriaPrima.builder()
                .id(10L)
                .precioPorKilo(105.0)
                .build();
        FormulaDetalle linea = FormulaDetalle.builder()
                .cantidadKg(100.0)
                .precioUnitarioMomentoCreacion(100.0)
                .costoParcial(10_000.0)
                .materiaPrima(trigo)
                .build();
        FormulaCabecera formula = FormulaCabecera.builder()
                .id("f1")
                .granja(granja)
                .pesoTotalFormula(1000.0)
                .costoTotalFormula(10_000.0)
                .detalles(new java.util.ArrayList<>(List.of(linea)))
                .build();
        linea.setFormula(formula);

        when(formulaCabeceraRepository.findActivasConMateriaPrima("g_demo", 10L))
                .thenReturn(List.of(formula));
        when(materiaPrimaRepository.findById(10L)).thenReturn(Optional.of(trigo));

        service.recalcularPorMateriasPrimas("g_demo", List.of(10L));

        assertThat(linea.getPrecioUnitarioMomentoCreacion()).isEqualTo(105.0);
        assertThat(linea.getCostoParcial()).isEqualTo(10_500.0);
        assertThat(formula.getCostoTotalFormula()).isEqualTo(10_500.0);
    }
}
