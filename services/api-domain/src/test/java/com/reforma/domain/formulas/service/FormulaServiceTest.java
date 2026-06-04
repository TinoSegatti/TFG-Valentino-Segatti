package com.reforma.domain.formulas.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.reforma.domain.animales.entity.Animal;
import com.reforma.domain.animales.repository.AnimalRepository;
import com.reforma.domain.common.domain.PlanSuscripcion;
import com.reforma.domain.formulas.dto.FormulaCabeceraRequest;
import com.reforma.domain.formulas.dto.FormulaDetalleLineRequest;
import com.reforma.domain.formulas.dto.GuardarFormulaDetalleRequest;
import com.reforma.domain.formulas.entity.FormulaCabecera;
import com.reforma.domain.formulas.repository.FormulaCabeceraRepository;
import com.reforma.domain.granjas.entity.Granja;
import com.reforma.domain.granjas.repository.GranjaRepository;
import com.reforma.domain.granjas.service.GranjaAccesoService;
import com.reforma.domain.materiasprimas.entity.MateriaPrima;
import com.reforma.domain.materiasprimas.repository.MateriaPrimaRepository;
import com.reforma.domain.suscripciones.service.PlanService;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class FormulaServiceTest {

    private static final String ID_USUARIO = "u_demo";
    private static final String ID_GRANJA = "g_demo";

    @Mock private FormulaCabeceraRepository formulaCabeceraRepository;
    @Mock private AnimalRepository animalRepository;
    @Mock private MateriaPrimaRepository materiaPrimaRepository;
    @Mock private GranjaRepository granjaRepository;
    @Mock private GranjaAccesoService granjaAccesoService;
    @Mock private PlanService planService;

    @InjectMocks private FormulaService formulaService;

    private Granja granja;
    private Animal animal;
    private MateriaPrima trigo;

    @BeforeEach
    void setUp() {
        granja = Granja.builder().id(ID_GRANJA).nombreGranja("Demo").activa(true).build();
        animal = Animal.builder().id(1L).granja(granja).codigoAnimal("CERDA").descripcionAnimal("Cerda").activo(true).build();
        trigo = MateriaPrima.builder()
                .id(10L)
                .granja(granja)
                .codigoMateriaPrima("TRIGO")
                .nombreMateriaPrima("Trigo")
                .precioPorKilo(100.0)
                .activa(true)
                .build();
    }

    @Test
    @DisplayName("guardarDetalle: rechaza si no suma 1000 kg")
    void guardarDetalle_rechazaSumaIncompleta() {
        FormulaCabecera cabecera = FormulaCabecera.builder()
                .id("f1")
                .granja(granja)
                .animal(animal)
                .codigoFormula("F-01")
                .descripcionFormula("Test")
                .pesoTotalFormula(1000.0)
                .costoTotalFormula(0.0)
                .activa(true)
                .build();
        when(formulaCabeceraRepository.findByIdAndGranjaIdAndActivaTrue("f1", ID_GRANJA))
                .thenReturn(Optional.of(cabecera));
        when(materiaPrimaRepository.findByIdAndGranjaId(10L, ID_GRANJA)).thenReturn(Optional.of(trigo));

        assertThatThrownBy(() -> formulaService.guardarDetalle(
                        ID_USUARIO,
                        ID_GRANJA,
                        "f1",
                        new GuardarFormulaDetalleRequest(
                                List.of(new FormulaDetalleLineRequest(10L, 500.0)))))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
    }

    @Test
    @DisplayName("guardarDetalle: happy path 1000 kg y costo total")
    void guardarDetalle_happyPath() {
        FormulaCabecera cabecera = FormulaCabecera.builder()
                .id("f1")
                .granja(granja)
                .animal(animal)
                .codigoFormula("F-01")
                .descripcionFormula("Test")
                .pesoTotalFormula(1000.0)
                .costoTotalFormula(0.0)
                .activa(true)
                .build();
        when(formulaCabeceraRepository.findByIdAndGranjaIdAndActivaTrue("f1", ID_GRANJA))
                .thenReturn(Optional.of(cabecera));
        when(materiaPrimaRepository.findByIdAndGranjaId(10L, ID_GRANJA)).thenReturn(Optional.of(trigo));

        var response = formulaService.guardarDetalle(
                ID_USUARIO,
                ID_GRANJA,
                "f1",
                new GuardarFormulaDetalleRequest(
                        List.of(new FormulaDetalleLineRequest(10L, 1000.0))));

        assertThat(response.completa()).isTrue();
        assertThat(response.costoTotalFormula()).isEqualTo(100_000.0);
        assertThat(cabecera.getCostoTotalFormula()).isEqualTo(100_000.0);
    }
}
