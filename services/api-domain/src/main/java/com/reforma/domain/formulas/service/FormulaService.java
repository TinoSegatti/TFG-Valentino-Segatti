package com.reforma.domain.formulas.service;

import com.reforma.domain.animales.entity.Animal;
import com.reforma.domain.animales.repository.AnimalRepository;
import com.reforma.domain.common.util.IdGenerator;
import com.reforma.domain.formulas.dto.FormulaCabeceraRequest;
import com.reforma.domain.formulas.dto.FormulaCompletaResponse;
import com.reforma.domain.formulas.dto.FormulaDetalleLineRequest;
import com.reforma.domain.formulas.dto.FormulaResumenResponse;
import com.reforma.domain.formulas.dto.GuardarFormulaDetalleRequest;
import com.reforma.domain.formulas.entity.FormulaCabecera;
import com.reforma.domain.formulas.entity.FormulaDetalle;
import com.reforma.domain.formulas.repository.FormulaCabeceraRepository;
import com.reforma.domain.formulas.support.FormulaCalculo;
import com.reforma.domain.granjas.entity.Granja;
import com.reforma.domain.granjas.repository.GranjaRepository;
import com.reforma.domain.granjas.service.GranjaAccesoService;
import com.reforma.domain.materiasprimas.entity.MateriaPrima;
import com.reforma.domain.materiasprimas.repository.MateriaPrimaRepository;
import com.reforma.domain.suscripciones.service.PlanService;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class FormulaService {

    private final FormulaCabeceraRepository formulaCabeceraRepository;
    private final AnimalRepository animalRepository;
    private final MateriaPrimaRepository materiaPrimaRepository;
    private final GranjaRepository granjaRepository;
    private final GranjaAccesoService granjaAccesoService;
    private final PlanService planService;

    @Transactional(readOnly = true)
    public List<FormulaResumenResponse> listar(String idUsuario, String idGranja) {
        granjaAccesoService.validarAcceso(idUsuario, idGranja);
        return formulaCabeceraRepository.findByGranjaIdAndActivaTrueOrderByCodigoFormulaAsc(idGranja).stream()
                .map(FormulaResumenResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public FormulaCompletaResponse obtener(String idUsuario, String idGranja, String idFormula) {
        granjaAccesoService.validarAcceso(idUsuario, idGranja);
        return FormulaCompletaResponse.from(obtenerCabecera(idGranja, idFormula));
    }

    @Transactional
    public FormulaCompletaResponse crearCabecera(
            String idUsuario, String idGranja, FormulaCabeceraRequest request) {
        granjaAccesoService.validarAcceso(idUsuario, idGranja);
        validarLimitePlan(idUsuario, idGranja);

        String codigo = request.codigoFormula().trim();
        if (formulaCabeceraRepository.existsByGranjaIdAndCodigoFormulaIgnoreCaseAndActivaTrue(idGranja, codigo)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "Ya existe una formula activa con codigo " + codigo);
        }

        Animal animal = animalRepository
                .findByIdAndGranjaId(request.idAnimal(), idGranja)
                .filter(Animal::getActivo)
                .orElseThrow(() ->
                        new ResponseStatusException(HttpStatus.BAD_REQUEST, "Animal no valido o inactivo"));

        Granja granja = granjaRepository.findById(idGranja).orElseThrow();
        FormulaCabecera cabecera = FormulaCabecera.builder()
                .id(IdGenerator.newId())
                .granja(granja)
                .animal(animal)
                .codigoFormula(codigo)
                .descripcionFormula(request.descripcionFormula().trim())
                .pesoTotalFormula(FormulaCalculo.PESO_LOTE_KG)
                .costoTotalFormula(0.0)
                .activa(true)
                .build();

        return FormulaCompletaResponse.from(formulaCabeceraRepository.save(cabecera));
    }

    @Transactional
    public FormulaCompletaResponse actualizarCabecera(
            String idUsuario, String idGranja, String idFormula, FormulaCabeceraRequest request) {
        granjaAccesoService.validarAcceso(idUsuario, idGranja);
        FormulaCabecera cabecera = obtenerCabecera(idGranja, idFormula);

        String codigo = request.codigoFormula().trim();
        if (!cabecera.getCodigoFormula().equalsIgnoreCase(codigo)
                && formulaCabeceraRepository.existsByGranjaIdAndCodigoFormulaIgnoreCaseAndActivaTrueAndIdNot(
                        idGranja, codigo, idFormula)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "Codigo duplicado en la granja: " + codigo);
        }

        Animal animal = animalRepository
                .findByIdAndGranjaId(request.idAnimal(), idGranja)
                .filter(Animal::getActivo)
                .orElseThrow(() ->
                        new ResponseStatusException(HttpStatus.BAD_REQUEST, "Animal no valido o inactivo"));

        cabecera.setCodigoFormula(codigo);
        cabecera.setDescripcionFormula(request.descripcionFormula().trim());
        cabecera.setAnimal(animal);
        return FormulaCompletaResponse.from(formulaCabeceraRepository.save(cabecera));
    }

    @Transactional
    public FormulaCompletaResponse guardarDetalle(
            String idUsuario, String idGranja, String idFormula, GuardarFormulaDetalleRequest request) {
        granjaAccesoService.validarAcceso(idUsuario, idGranja);
        FormulaCabecera cabecera = obtenerCabecera(idGranja, idFormula);

        List<FormulaDetalle> nuevasLineas = construirLineasValidadas(idGranja, cabecera, request.lineas());
        double sumaKg = FormulaCalculo.redondear(
                nuevasLineas.stream().mapToDouble(FormulaDetalle::getCantidadKg).sum());

        if (!FormulaCalculo.sumaKgCompleta(sumaKg)) {
            throw new ResponseStatusException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "La formula debe sumar exactamente "
                            + FormulaCalculo.PESO_LOTE_KG
                            + " kg. Suma actual: "
                            + sumaKg
                            + ". Faltan "
                            + FormulaCalculo.kilosFaltantes(sumaKg)
                            + " kg.");
        }

        cabecera.getDetalles().clear();
        cabecera.getDetalles().addAll(nuevasLineas);
        aplicarCostoTotal(cabecera);
        return FormulaCompletaResponse.from(cabecera);
    }

    @Transactional
    public void desactivar(String idUsuario, String idGranja, String idFormula) {
        granjaAccesoService.validarAcceso(idUsuario, idGranja);
        FormulaCabecera cabecera = obtenerCabecera(idGranja, idFormula);
        cabecera.setActiva(false);
    }

    private List<FormulaDetalle> construirLineasValidadas(
            String idGranja, FormulaCabecera cabecera, List<FormulaDetalleLineRequest> lineasRequest) {
        List<FormulaDetalle> nuevasLineas = new ArrayList<>();
        Set<Long> materiasUsadas = new HashSet<>();
        double pesoTotal = cabecera.getPesoTotalFormula();

        for (FormulaDetalleLineRequest linea : lineasRequest) {
            if (linea.cantidadKg() == null || linea.cantidadKg() <= 0) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST, "Cantidad en kg debe ser mayor a cero");
            }
            if (!materiasUsadas.add(linea.idMateriaPrima())) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST, "Materia prima repetida en la formula");
            }

            MateriaPrima mp = materiaPrimaRepository
                    .findByIdAndGranjaId(linea.idMateriaPrima(), idGranja)
                    .filter(MateriaPrima::getActiva)
                    .orElseThrow(() -> new ResponseStatusException(
                            HttpStatus.BAD_REQUEST, "Materia prima no valida o inactiva"));

            double cantidad = FormulaCalculo.redondear(linea.cantidadKg());
            double precio = FormulaCalculo.redondear(mp.getPrecioPorKilo());
            double costoParcial = FormulaCalculo.calcularCostoParcial(cantidad, precio);

            nuevasLineas.add(FormulaDetalle.builder()
                    .id(IdGenerator.newId())
                    .formula(cabecera)
                    .materiaPrima(mp)
                    .cantidadKg(cantidad)
                    .porcentajeFormula(FormulaCalculo.calcularPorcentaje(cantidad, pesoTotal))
                    .precioUnitarioMomentoCreacion(precio)
                    .costoParcial(costoParcial)
                    .build());
        }
        return nuevasLineas;
    }

    private void aplicarCostoTotal(FormulaCabecera cabecera) {
        double total = FormulaCalculo.redondear(
                cabecera.getDetalles().stream().mapToDouble(FormulaDetalle::getCostoParcial).sum());
        cabecera.setCostoTotalFormula(total);
    }

    private FormulaCabecera obtenerCabecera(String idGranja, String idFormula) {
        return formulaCabeceraRepository
                .findByIdAndGranjaIdAndActivaTrue(idFormula, idGranja)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Formula no encontrada"));
    }

    private void validarLimitePlan(String idUsuario, String idGranja) {
        int limite = planService.limiteFormulas(planService.obtenerPlanEfectivo(idUsuario));
        long actuales = formulaCabeceraRepository.countByGranjaIdAndActivaTrue(idGranja);
        if (actuales >= limite) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Limite de formulas del plan alcanzado (" + limite + ")");
        }
    }
}
