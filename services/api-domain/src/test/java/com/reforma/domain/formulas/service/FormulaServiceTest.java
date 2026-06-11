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

    // ---------- import / export CSV (formulas con detalle en un único archivo) ----------

    @Test
    @DisplayName("importarCsv: agrupa filas por codigo_formula, valida 1000 kg y persiste cada fórmula independiente")
    void importarCsv_mezcla() {
        MateriaPrima soja = MateriaPrima.builder()
                .id(11L)
                .granja(granja)
                .codigoMateriaPrima("SOJA")
                .nombreMateriaPrima("Soja")
                .precioPorKilo(120.0)
                .activa(true)
                .build();
        when(planService.obtenerPlanEfectivo(ID_USUARIO)).thenReturn(PlanSuscripcion.BUSINESS);
        when(planService.limiteFormulas(PlanSuscripcion.BUSINESS)).thenReturn(100);
        when(formulaCabeceraRepository.countByGranjaIdAndActivaTrue(ID_GRANJA)).thenReturn(0L);
        when(granjaRepository.findById(ID_GRANJA)).thenReturn(Optional.of(granja));
        when(animalRepository.findByGranjaIdAndCodigoAnimalIgnoreCaseAndActivoTrue(ID_GRANJA, "CERDA"))
                .thenReturn(Optional.of(animal));
        when(materiaPrimaRepository.findByGranjaIdAndCodigoMateriaPrimaIgnoreCaseAndActivaTrue(
                        ID_GRANJA, "TRIGO"))
                .thenReturn(Optional.of(trigo));
        when(materiaPrimaRepository.findByGranjaIdAndCodigoMateriaPrimaIgnoreCaseAndActivaTrue(
                        ID_GRANJA, "SOJA"))
                .thenReturn(Optional.of(soja));
        when(formulaCabeceraRepository.existsByGranjaIdAndCodigoFormulaIgnoreCaseAndActivaTrue(
                        eq(ID_GRANJA), anyString()))
                .thenReturn(false);
        when(formulaCabeceraRepository.save(any(FormulaCabecera.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        // F-OK: 500+500=1000 ✓
        // F-INC: 500+400=900 → falla suma
        // F-MP: TRIGO 1000 + ZZZ 0  (ZZZ no existe) → falla MP
        String csv =
                "codigo_formula,descripcion_formula,codigo_animal,codigo_materia_prima,cantidad_kg\n"
                        + "F-OK,Engorde,CERDA,TRIGO,500\n"
                        + "F-OK,Engorde,CERDA,SOJA,500\n"
                        + "F-INC,Lechones,CERDA,TRIGO,500\n"
                        + "F-INC,Lechones,CERDA,SOJA,400\n"
                        + "F-MP,Mala,CERDA,TRIGO,500\n"
                        + "F-MP,Mala,CERDA,ZZZ,500\n";

        com.reforma.domain.common.csv.CsvImportResult resultado =
                formulaService.importarCsv(
                        ID_USUARIO,
                        ID_GRANJA,
                        new java.io.ByteArrayInputStream(
                                csv.getBytes(java.nio.charset.StandardCharsets.UTF_8)));

        assertThat(resultado.filasOk()).isEqualTo(1);
        assertThat(resultado.filasError()).isEqualTo(2);
        assertThat(resultado.errores())
                .extracting(com.reforma.domain.common.csv.CsvImportError::codigo)
                .containsExactlyInAnyOrder("F-INC", "F-MP");
        verify(formulaCabeceraRepository).save(any(FormulaCabecera.class));
    }

    @Test
    @DisplayName("importarCsv: descripcion_formula inconsistente entre filas del mismo grupo → error")
    void importarCsv_descripcionInconsistente() {
        // La inconsistencia de descripcion se detecta al agrupar, antes del plan-gating:
        // no se stubea plan/limite/count porque nunca se invocan (Mockito strict).
        String csv =
                "codigo_formula,descripcion_formula,codigo_animal,codigo_materia_prima,cantidad_kg\n"
                        + "F-X,Engorde,CERDA,TRIGO,500\n"
                        + "F-X,Engorde 2,CERDA,SOJA,500\n";

        com.reforma.domain.common.csv.CsvImportResult resultado =
                formulaService.importarCsv(
                        ID_USUARIO,
                        ID_GRANJA,
                        new java.io.ByteArrayInputStream(
                                csv.getBytes(java.nio.charset.StandardCharsets.UTF_8)));

        assertThat(resultado.filasOk()).isZero();
        assertThat(resultado.filasError()).isEqualTo(1);
        assertThat(resultado.errores().get(0).mensaje()).contains("descripcion_formula");
    }

    @Test
    @DisplayName("exportarCsv: serializa cabecera + cada ingrediente como fila")
    void exportarCsv_serializa() {
        FormulaCabecera cab = FormulaCabecera.builder()
                .id("f1")
                .granja(granja)
                .animal(animal)
                .codigoFormula("F-01")
                .descripcionFormula("Engorde")
                .pesoTotalFormula(1000.0)
                .costoTotalFormula(120_000.0)
                .activa(true)
                .build();
        cab.getDetalles().add(com.reforma.domain.formulas.entity.FormulaDetalle.builder()
                .id("d1")
                .formula(cab)
                .materiaPrima(trigo)
                .cantidadKg(600.0)
                .porcentajeFormula(60.0)
                .precioUnitarioMomentoCreacion(100.0)
                .costoParcial(60_000.0)
                .build());
        cab.getDetalles().add(com.reforma.domain.formulas.entity.FormulaDetalle.builder()
                .id("d2")
                .formula(cab)
                .materiaPrima(MateriaPrima.builder()
                        .id(11L)
                        .granja(granja)
                        .codigoMateriaPrima("SOJA")
                        .nombreMateriaPrima("Soja")
                        .precioPorKilo(150.0)
                        .activa(true)
                        .build())
                .cantidadKg(400.0)
                .porcentajeFormula(40.0)
                .precioUnitarioMomentoCreacion(150.0)
                .costoParcial(60_000.0)
                .build());
        when(formulaCabeceraRepository.findByGranjaIdAndActivaTrueOrderByCodigoFormulaAsc(ID_GRANJA))
                .thenReturn(List.of(cab));

        String csv = formulaService.exportarCsv(ID_USUARIO, ID_GRANJA);

        assertThat(csv).startsWith(
                "codigo_formula,descripcion_formula,codigo_animal,codigo_materia_prima,cantidad_kg\r\n");
        assertThat(csv).contains("F-01,Engorde,CERDA,TRIGO,600.0\r\n");
        assertThat(csv).contains("F-01,Engorde,CERDA,SOJA,400.0\r\n");
    }
}
