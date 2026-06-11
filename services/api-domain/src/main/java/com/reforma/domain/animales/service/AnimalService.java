package com.reforma.domain.animales.service;

import com.reforma.domain.animales.dto.AnimalRequest;
import com.reforma.domain.animales.dto.AnimalResponse;
import com.reforma.domain.animales.entity.Animal;
import com.reforma.domain.animales.repository.AnimalRepository;
import com.reforma.domain.common.csv.CsvFields;
import com.reforma.domain.common.csv.CsvImportError;
import com.reforma.domain.common.csv.CsvImportResult;
import com.reforma.domain.common.csv.CsvReader;
import com.reforma.domain.common.csv.CsvWriter;
import com.reforma.domain.granjas.entity.Granja;
import com.reforma.domain.granjas.repository.GranjaRepository;
import com.reforma.domain.granjas.service.GranjaAccesoService;
import com.reforma.domain.suscripciones.service.PlanService;
import java.io.InputStream;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Lógica de negocio del catálogo de Animales (RF-ANI-001 / RF-ANI-002).
 *
 * <p>Invariantes:
 * <ol>
 *   <li>El usuario debe tener acceso a la granja (multi-tenancy).</li>
 *   <li>El código de animal es único dentro de la granja <b>entre activos</b>
 *       (ADR 0005). Los inactivos son histórico congelado.</li>
 *   <li>El número de animales activos por granja no excede el límite del plan.</li>
 * </ol>
 *
 * <p>La baja es lógica ({@code activo=false}) para preservar fórmulas y fabricaciones
 * históricas que referencian al animal.
 */
@Service
@RequiredArgsConstructor
public class AnimalService {

    private final AnimalRepository animalRepository;
    private final GranjaRepository granjaRepository;
    private final GranjaAccesoService granjaAccesoService;
    private final PlanService planService;

    @Transactional(readOnly = true)
    public List<AnimalResponse> listarPorGranja(String idUsuario, String idGranja, String buscar) {
        granjaAccesoService.validarAcceso(idUsuario, idGranja);
        List<Animal> activos = (buscar == null || buscar.isBlank())
                ? animalRepository.findByGranjaIdAndActivoTrueOrderByDescripcionAnimalAsc(idGranja)
                : animalRepository
                        .findByGranjaIdAndActivoTrueAndDescripcionAnimalContainingIgnoreCaseOrderByDescripcionAnimalAsc(
                                idGranja, buscar.trim());
        return activos.stream().map(AnimalResponse::from).toList();
    }

    /**
     * Alta de animal. Política ADR 0005:
     * <ul>
     *   <li>Rechaza con 409 si ya existe otro animal <b>activo</b> con el mismo código.</li>
     *   <li>Si solo existen animales <b>inactivos</b> con ese código, quedan intactos y se
     *       inserta una fila nueva (id autoincremental distinto).</li>
     * </ul>
     */
    @Transactional
    public AnimalResponse crear(String idUsuario, String idGranja, AnimalRequest request) {
        granjaAccesoService.validarAcceso(idUsuario, idGranja);
        validarLimitePlan(idUsuario, idGranja);

        String codigo = request.codigoAnimal().trim();
        if (animalRepository.existsByGranjaIdAndCodigoAnimalIgnoreCaseAndActivoTrue(idGranja, codigo)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Ya existe un animal activo con código " + codigo + " en esta granja");
        }
        Granja granja = granjaRepository.findById(idGranja).orElseThrow();
        Instant now = Instant.now();
        Animal animal = Animal.builder()
                .granja(granja)
                .codigoAnimal(codigo)
                .descripcionAnimal(request.descripcionAnimal().trim())
                .categoriaAnimal(normalizar(request.categoriaAnimal()))
                .observaciones(normalizar(request.observaciones()))
                .activo(true)
                .fechaCreacion(now)
                .fechaUltimaActualizacion(now)
                .build();
        return AnimalResponse.from(animalRepository.save(animal));
    }

    @Transactional
    public AnimalResponse actualizar(
            String idUsuario, String idGranja, Long idAnimal, AnimalRequest request) {
        granjaAccesoService.validarAcceso(idUsuario, idGranja);
        Animal animal = obtenerOFallar(idAnimal, idGranja);

        String nuevoCodigo = request.codigoAnimal().trim();
        // Solo bloqueamos si el código nuevo colisiona con OTRO animal ACTIVO (ADR 0005).
        if (!animal.getCodigoAnimal().equalsIgnoreCase(nuevoCodigo)
                && animalRepository.existsByGranjaIdAndCodigoAnimalIgnoreCaseAndActivoTrue(
                        idGranja, nuevoCodigo)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "Código duplicado en la granja: " + nuevoCodigo);
        }

        animal.setCodigoAnimal(nuevoCodigo);
        animal.setDescripcionAnimal(request.descripcionAnimal().trim());
        animal.setCategoriaAnimal(normalizar(request.categoriaAnimal()));
        animal.setObservaciones(normalizar(request.observaciones()));
        animal.setFechaUltimaActualizacion(Instant.now());
        return AnimalResponse.from(animal);
    }

    @Transactional
    public void desactivar(String idUsuario, String idGranja, Long idAnimal) {
        granjaAccesoService.validarAcceso(idUsuario, idGranja);
        Animal animal = obtenerOFallar(idAnimal, idGranja);
        animal.setActivo(false);
        animal.setFechaUltimaActualizacion(Instant.now());
        // Sin delete() — RF-ANI-002 (fórmulas/fabricaciones que lo referencian deben seguir leyendo).
    }

    /**
     * Exporta animales activos a CSV (RF-ANI-003).
     * <p>Columnas: {@code codigo, descripcion, categoria, observaciones}.
     */
    @Transactional(readOnly = true)
    public String exportarCsv(String idUsuario, String idGranja) {
        granjaAccesoService.validarAcceso(idUsuario, idGranja);
        List<List<String>> filas = new ArrayList<>();
        filas.add(Arrays.asList("codigo", "descripcion", "categoria", "observaciones"));
        animalRepository
                .findByGranjaIdAndActivoTrueOrderByDescripcionAnimalAsc(idGranja)
                .forEach(a -> filas.add(Arrays.asList(
                        textoONulo(a.getCodigoAnimal()),
                        textoONulo(a.getDescripcionAnimal()),
                        textoONulo(a.getCategoriaAnimal()),
                        textoONulo(a.getObservaciones()))));
        return CsvWriter.escribir(filas);
    }

    /**
     * Importa animales desde un CSV. Header esperado: {@code codigo, descripcion} (obligatorias)
     * y opcionales {@code categoria, observaciones} (RF-ANI-003).
     */
    @Transactional
    public CsvImportResult importarCsv(String idUsuario, String idGranja, InputStream csv) {
        granjaAccesoService.validarAcceso(idUsuario, idGranja);
        List<Map<String, String>> filas = CsvReader.leer(csv);
        if (filas.isEmpty()) return CsvImportResult.vacio();

        Granja granja = granjaRepository.findById(idGranja).orElseThrow();
        int filasOk = 0;
        List<CsvImportError> errores = new ArrayList<>();
        Instant ahora = Instant.now();
        int numeroLinea = 1;
        for (Map<String, String> fila : filas) {
            numeroLinea++;
            String codigo = null;
            try {
                CsvFields.validarColumnas(fila, "codigo", "descripcion");
                codigo = CsvFields.requerido(fila, "codigo");
                String descripcion = CsvFields.requerido(fila, "descripcion");
                validarLimitePlan(idUsuario, idGranja);
                if (animalRepository.existsByGranjaIdAndCodigoAnimalIgnoreCaseAndActivoTrue(
                        idGranja, codigo)) {
                    throw new ResponseStatusException(
                            HttpStatus.CONFLICT, "Ya existe un animal activo con código " + codigo);
                }
                Animal a = Animal.builder()
                        .granja(granja)
                        .codigoAnimal(codigo)
                        .descripcionAnimal(descripcion)
                        .categoriaAnimal(CsvFields.opcional(fila, "categoria"))
                        .observaciones(CsvFields.opcional(fila, "observaciones"))
                        .activo(true)
                        .fechaCreacion(ahora)
                        .fechaUltimaActualizacion(ahora)
                        .build();
                animalRepository.save(a);
                filasOk++;
            } catch (ResponseStatusException e) {
                errores.add(new CsvImportError(numeroLinea, codigo, e.getReason()));
            } catch (RuntimeException e) {
                errores.add(new CsvImportError(numeroLinea, codigo, "Error: " + e.getMessage()));
            }
        }
        return new CsvImportResult(filasOk, errores.size(), errores);
    }

    private static String textoONulo(String v) {
        return v == null ? "" : v;
    }

    private Animal obtenerOFallar(Long idAnimal, String idGranja) {
        return animalRepository
                .findByIdAndGranjaId(idAnimal, idGranja)
                .orElseThrow(() ->
                        new ResponseStatusException(HttpStatus.NOT_FOUND, "Animal no encontrado"));
    }

    /** Plan-gating: 403 si la granja ya alcanzó el tope de animales activos del plan. */
    private void validarLimitePlan(String idUsuario, String idGranja) {
        var plan = planService.obtenerPlanEfectivo(idUsuario);
        int limite = planService.limiteAnimales(plan);
        long actuales = animalRepository.countByGranjaIdAndActivoTrue(idGranja);
        if (actuales >= limite) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Plan " + plan.name() + " permite hasta " + limite
                            + " animales activos por granja");
        }
    }

    private static String normalizar(String value) {
        if (value == null) return null;
        String t = value.trim();
        return t.isEmpty() ? null : t;
    }
}
