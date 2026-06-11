package com.reforma.domain.formulas.service;

import com.reforma.domain.animales.entity.Animal;
import com.reforma.domain.animales.repository.AnimalRepository;
import com.reforma.domain.common.csv.CsvFields;
import com.reforma.domain.common.csv.CsvImportError;
import com.reforma.domain.common.csv.CsvImportResult;
import com.reforma.domain.common.csv.CsvReader;
import com.reforma.domain.common.csv.CsvWriter;
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
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
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

    /**
     * Exporta las fórmulas activas de la granja a un único CSV "denormalizado":
     * una fila por ingrediente con los datos de la cabecera repetidos.
     *
     * <p>Columnas: {@code codigo_formula, descripcion_formula, codigo_animal,
     * codigo_materia_prima, cantidad_kg}. Las fórmulas sin ingredientes se omiten
     * (no se pueden reimportar por la regla de 1000 kg).
     */
    @Transactional(readOnly = true)
    public String exportarCsv(String idUsuario, String idGranja) {
        granjaAccesoService.validarAcceso(idUsuario, idGranja);
        List<List<String>> filas = new ArrayList<>();
        filas.add(List.of(
                "codigo_formula",
                "descripcion_formula",
                "codigo_animal",
                "codigo_materia_prima",
                "cantidad_kg"));
        for (FormulaCabecera cab :
                formulaCabeceraRepository.findByGranjaIdAndActivaTrueOrderByCodigoFormulaAsc(idGranja)) {
            for (FormulaDetalle d : cab.getDetalles()) {
                filas.add(List.of(
                        textoONulo(cab.getCodigoFormula()),
                        textoONulo(cab.getDescripcionFormula()),
                        textoONulo(cab.getAnimal().getCodigoAnimal()),
                        textoONulo(d.getMateriaPrima().getCodigoMateriaPrima()),
                        Double.toString(FormulaCalculo.redondear(d.getCantidadKg()))));
            }
        }
        return CsvWriter.escribir(filas);
    }

    /**
     * Importa fórmulas desde un único CSV "denormalizado".
     * <ul>
     *   <li>Las filas se agrupan por {@code codigo_formula} (la unidad de import es la fórmula
     *       completa, no la línea).</li>
     *   <li>Dentro de cada grupo, {@code descripcion_formula} y {@code codigo_animal} deben ser
     *       consistentes en todas las filas; un valor distinto invalida toda la fórmula.</li>
     *   <li>La suma de {@code cantidad_kg} por fórmula debe igualar 1000 kg (regla del lote).</li>
     *   <li>Cada fórmula se persiste en su propia transacción independiente: si una falla, las
     *       demás siguen siendo importadas. El error queda en la respuesta con la línea CSV
     *       donde apareció esa fórmula por primera vez.</li>
     * </ul>
     *
     * <p>El contador {@code filasOk}/{@code filasError} representa <b>fórmulas</b>, no líneas
     * individuales (porque la unidad mínima de carga es la fórmula completa).
     */
    @Transactional(propagation = Propagation.NEVER)
    public CsvImportResult importarCsv(String idUsuario, String idGranja, InputStream csv) {
        granjaAccesoService.validarAcceso(idUsuario, idGranja);
        List<Map<String, String>> filas = CsvReader.leer(csv);
        if (filas.isEmpty()) return CsvImportResult.vacio();

        Map<String, GrupoFormulaCsv> grupos = agruparFormulasPorCodigo(filas);

        int formulasOk = 0;
        List<CsvImportError> errores = new ArrayList<>();
        for (GrupoFormulaCsv grupo : grupos.values()) {
            try {
                if (grupo.errorAgrupacion != null) {
                    throw new ResponseStatusException(
                            HttpStatus.UNPROCESSABLE_ENTITY, grupo.errorAgrupacion);
                }
                self().importarFormulaUnitaria(idUsuario, idGranja, grupo);
                formulasOk++;
            } catch (ResponseStatusException e) {
                errores.add(new CsvImportError(grupo.lineaPrimera, grupo.codigoFormula, e.getReason()));
            } catch (RuntimeException e) {
                errores.add(new CsvImportError(
                        grupo.lineaPrimera, grupo.codigoFormula, "Error: " + e.getMessage()));
            }
        }
        return new CsvImportResult(formulasOk, errores.size(), errores);
    }

    /**
     * Persiste una fórmula completa (cabecera + detalles) a partir de un grupo de filas
     * ya agrupadas. Está en transacción {@code REQUIRES_NEW} para que el rollback de una
     * fórmula con error no propague al resto del import.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void importarFormulaUnitaria(
            String idUsuario, String idGranja, GrupoFormulaCsv grupo) {
        validarLimitePlan(idUsuario, idGranja);
        String codigo = grupo.codigoFormula;
        if (formulaCabeceraRepository.existsByGranjaIdAndCodigoFormulaIgnoreCaseAndActivaTrue(
                idGranja, codigo)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "Ya existe una formula activa con codigo " + codigo);
        }

        Animal animal = animalRepository
                .findByGranjaIdAndCodigoAnimalIgnoreCaseAndActivoTrue(idGranja, grupo.codigoAnimal)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.UNPROCESSABLE_ENTITY,
                        "Animal con codigo '" + grupo.codigoAnimal + "' no encontrado o inactivo"));

        Granja granja = granjaRepository.findById(idGranja).orElseThrow();
        FormulaCabecera cabecera = FormulaCabecera.builder()
                .id(IdGenerator.newId())
                .granja(granja)
                .animal(animal)
                .codigoFormula(codigo)
                .descripcionFormula(grupo.descripcionFormula)
                .pesoTotalFormula(FormulaCalculo.PESO_LOTE_KG)
                .costoTotalFormula(0.0)
                .activa(true)
                .build();

        Set<Long> materiasUsadas = new HashSet<>();
        double sumaKg = 0.0;
        for (LineaCsv linea : grupo.lineas) {
            MateriaPrima mp = materiaPrimaRepository
                    .findByGranjaIdAndCodigoMateriaPrimaIgnoreCaseAndActivaTrue(
                            idGranja, linea.codigoMateriaPrima)
                    .orElseThrow(() -> new ResponseStatusException(
                            HttpStatus.UNPROCESSABLE_ENTITY,
                            "Materia prima con codigo '" + linea.codigoMateriaPrima
                                    + "' no encontrada o inactiva"));
            if (!materiasUsadas.add(mp.getId())) {
                throw new ResponseStatusException(
                        HttpStatus.UNPROCESSABLE_ENTITY,
                        "Materia prima '" + linea.codigoMateriaPrima
                                + "' aparece más de una vez en la formula");
            }
            double cantidad = FormulaCalculo.redondear(linea.cantidadKg);
            if (cantidad <= 0) {
                throw new ResponseStatusException(
                        HttpStatus.UNPROCESSABLE_ENTITY,
                        "cantidad_kg debe ser mayor a cero para '" + linea.codigoMateriaPrima + "'");
            }
            double precio = FormulaCalculo.redondear(mp.getPrecioPorKilo());
            FormulaDetalle d = FormulaDetalle.builder()
                    .id(IdGenerator.newId())
                    .formula(cabecera)
                    .materiaPrima(mp)
                    .cantidadKg(cantidad)
                    .porcentajeFormula(
                            FormulaCalculo.calcularPorcentaje(cantidad, FormulaCalculo.PESO_LOTE_KG))
                    .precioUnitarioMomentoCreacion(precio)
                    .costoParcial(FormulaCalculo.calcularCostoParcial(cantidad, precio))
                    .build();
            cabecera.getDetalles().add(d);
            sumaKg += cantidad;
        }
        sumaKg = FormulaCalculo.redondear(sumaKg);
        if (!FormulaCalculo.sumaKgCompleta(sumaKg)) {
            throw new ResponseStatusException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "La formula debe sumar 1000 kg. Suma actual: " + sumaKg);
        }
        aplicarCostoTotal(cabecera);
        formulaCabeceraRepository.save(cabecera);
    }

    /** Agrupa las filas por codigoFormula, validando consistencia interna del grupo. */
    private Map<String, GrupoFormulaCsv> agruparFormulasPorCodigo(List<Map<String, String>> filas) {
        Map<String, GrupoFormulaCsv> grupos = new LinkedHashMap<>();
        int numeroLinea = 1;
        for (Map<String, String> fila : filas) {
            numeroLinea++;
            final int lineaActual = numeroLinea;
            try {
                CsvFields.validarColumnas(
                        fila,
                        "codigo_formula",
                        "descripcion_formula",
                        "codigo_animal",
                        "codigo_materia_prima",
                        "cantidad_kg");
                String codigoFormula = CsvFields.requerido(fila, "codigo_formula");
                String descripcionFormula = CsvFields.requerido(fila, "descripcion_formula");
                String codigoAnimal = CsvFields.requerido(fila, "codigo_animal");
                String codigoMp = CsvFields.requerido(fila, "codigo_materia_prima");
                Double cantidad = CsvFields.decimalOpcional(fila, "cantidad_kg");
                if (cantidad == null) {
                    throw new ResponseStatusException(
                            HttpStatus.UNPROCESSABLE_ENTITY, "cantidad_kg vacía o inválida");
                }
                String clave = codigoFormula.toLowerCase();
                GrupoFormulaCsv grupo = grupos.computeIfAbsent(
                        clave,
                        k -> new GrupoFormulaCsv(lineaActual, codigoFormula, descripcionFormula, codigoAnimal));
                if (grupo.errorAgrupacion == null) {
                    if (!grupo.descripcionFormula.equalsIgnoreCase(descripcionFormula)) {
                        grupo.errorAgrupacion =
                                "descripcion_formula inconsistente entre filas de la formula '"
                                        + codigoFormula + "'";
                    } else if (!grupo.codigoAnimal.equalsIgnoreCase(codigoAnimal)) {
                        grupo.errorAgrupacion =
                                "codigo_animal inconsistente entre filas de la formula '"
                                        + codigoFormula + "'";
                    }
                }
                grupo.lineas.add(new LineaCsv(lineaActual, codigoMp, cantidad));
            } catch (ResponseStatusException e) {
                String codigoFila = fila.get("codigo_formula");
                String clave = codigoFila == null
                        ? ("__error_linea_" + lineaActual + "__")
                        : codigoFila.toLowerCase();
                GrupoFormulaCsv grupo = grupos.computeIfAbsent(
                        clave,
                        k -> new GrupoFormulaCsv(lineaActual, codigoFila, "", ""));
                if (grupo.errorAgrupacion == null) {
                    grupo.errorAgrupacion = e.getReason();
                }
            }
        }
        return grupos;
    }

    private static String textoONulo(String v) {
        return v == null ? "" : v;
    }

    /** Self-injection para que {@code REQUIRES_NEW} actúe (proxy Spring). */
    @org.springframework.beans.factory.annotation.Autowired
    @org.springframework.context.annotation.Lazy
    private FormulaService self;

    private FormulaService self() {
        return self != null ? self : this;
    }

    /** Estructuras intermedias del import — no se exponen fuera del paquete. */
    static final class GrupoFormulaCsv {
        final int lineaPrimera;
        final String codigoFormula;
        final String descripcionFormula;
        final String codigoAnimal;
        final List<LineaCsv> lineas = new ArrayList<>();
        String errorAgrupacion;

        GrupoFormulaCsv(
                int lineaPrimera,
                String codigoFormula,
                String descripcionFormula,
                String codigoAnimal) {
            this.lineaPrimera = lineaPrimera;
            this.codigoFormula = codigoFormula;
            this.descripcionFormula = descripcionFormula;
            this.codigoAnimal = codigoAnimal;
        }
    }

    static final class LineaCsv {
        final int numeroLinea;
        final String codigoMateriaPrima;
        final double cantidadKg;

        LineaCsv(int numeroLinea, String codigoMateriaPrima, double cantidadKg) {
            this.numeroLinea = numeroLinea;
            this.codigoMateriaPrima = codigoMateriaPrima;
            this.cantidadKg = cantidadKg;
        }
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
