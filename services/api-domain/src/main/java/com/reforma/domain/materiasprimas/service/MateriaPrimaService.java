package com.reforma.domain.materiasprimas.service;

import com.reforma.domain.common.csv.CsvFields;
import com.reforma.domain.common.csv.CsvImportError;
import com.reforma.domain.common.csv.CsvImportResult;
import com.reforma.domain.common.csv.CsvReader;
import com.reforma.domain.common.csv.CsvWriter;
import com.reforma.domain.granjas.entity.Granja;
import com.reforma.domain.granjas.repository.GranjaRepository;
import com.reforma.domain.granjas.service.GranjaAccesoService;
import com.reforma.domain.materiasprimas.dto.MateriaPrimaRequest;
import com.reforma.domain.materiasprimas.dto.MateriaPrimaResponse;
import com.reforma.domain.materiasprimas.entity.MateriaPrima;
import com.reforma.domain.materiasprimas.repository.MateriaPrimaRepository;
import com.reforma.domain.suscripciones.service.PlanService;
import java.io.InputStream;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class MateriaPrimaService {

    private final MateriaPrimaRepository materiaPrimaRepository;
    private final GranjaRepository granjaRepository;
    private final GranjaAccesoService granjaAccesoService;
    private final PlanService planService;

    @Transactional(readOnly = true)
    public List<MateriaPrimaResponse> listarPorGranja(String idUsuario, String idGranja) {
        granjaAccesoService.validarAcceso(idUsuario, idGranja);
        return materiaPrimaRepository
                .findByGranjaIdAndActivaTrueOrderByNombreMateriaPrimaAsc(idGranja)
                .stream()
                .map(MateriaPrimaResponse::from)
                .toList();
    }

    /**
     * Alta de materia prima. Política de soft-delete + entidades versionadas (ADR 0005):
     * <ul>
     *   <li>Rechaza con 409 si ya existe otra MP <b>activa</b> con el mismo código en la granja.</li>
     *   <li>Si solo existen MPs <b>inactivas</b> con ese código, las deja intactas (son
     *       histórico congelado) y crea una <b>fila nueva</b> con {@code id} autoincremental distinto.</li>
     *   <li>El {@code id} (Long) lo asigna la BD; el {@code codigoMateriaPrima} lo ingresa el usuario.</li>
     * </ul>
     * Esto preserva la integridad histórica: las compras / fabricaciones / series ML
     * asociadas a la MP vieja siguen apuntando a ella con sus datos originales.
     */
    @Transactional
    public MateriaPrimaResponse crear(
            String idUsuario, String idGranja, MateriaPrimaRequest request) {
        granjaAccesoService.validarAcceso(idUsuario, idGranja);
        validarLimitePlan(idUsuario, idGranja);

        String codigo = request.codigoMateriaPrima().trim();
        if (materiaPrimaRepository
                .existsByGranjaIdAndCodigoMateriaPrimaIgnoreCaseAndActivaTrue(idGranja, codigo)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Ya existe una materia prima activa con código " + codigo + " en esta granja");
        }
        Granja granja = granjaRepository.findById(idGranja).orElseThrow();
        Instant now = Instant.now();
        double precio = request.precioPorKilo() != null ? request.precioPorKilo() : 0.0;
        MateriaPrima mp = MateriaPrima.builder()
                .granja(granja)
                .codigoMateriaPrima(codigo)
                .nombreMateriaPrima(request.nombreMateriaPrima().trim())
                .precioPorKilo(precio)
                .precioBaseManual(precio)
                .activa(true)
                .fechaCreacion(now)
                .fechaUltimaActualizacion(now)
                .build();
        return MateriaPrimaResponse.from(materiaPrimaRepository.save(mp));
    }

    @Transactional
    public MateriaPrimaResponse actualizar(
            String idUsuario, String idGranja, Long idMateriaPrima, MateriaPrimaRequest request) {
        granjaAccesoService.validarAcceso(idUsuario, idGranja);
        MateriaPrima mp = obtenerOFallar(idMateriaPrima, idGranja);
        String nuevoCodigo = request.codigoMateriaPrima().trim();
        // Solo bloqueamos si el código nuevo colisiona con OTRA MP ACTIVA (ver ADR 0005).
        if (!mp.getCodigoMateriaPrima().equalsIgnoreCase(nuevoCodigo)
                && materiaPrimaRepository
                        .existsByGranjaIdAndCodigoMateriaPrimaIgnoreCaseAndActivaTrue(
                                idGranja, nuevoCodigo)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "Código duplicado en la granja: " + nuevoCodigo);
        }
        mp.setCodigoMateriaPrima(nuevoCodigo);
        mp.setNombreMateriaPrima(request.nombreMateriaPrima().trim());
        if (request.precioPorKilo() != null) {
            // Edición manual: actualiza tanto el precio vigente como la base de reversión.
            mp.setPrecioPorKilo(request.precioPorKilo());
            mp.setPrecioBaseManual(request.precioPorKilo());
        }
        mp.setFechaUltimaActualizacion(Instant.now());
        return MateriaPrimaResponse.from(mp);
    }

    @Transactional
    public void desactivar(String idUsuario, String idGranja, Long idMateriaPrima) {
        granjaAccesoService.validarAcceso(idUsuario, idGranja);
        MateriaPrima mp = obtenerOFallar(idMateriaPrima, idGranja);
        mp.setActiva(false);
        mp.setFechaUltimaActualizacion(Instant.now());
    }

    /**
     * Exporta las MPs activas de la granja a CSV (RF-MP-004).
     * <p>Columnas: {@code codigo, nombre, precio_por_kilo}. Orden: igual que el listado.
     */
    @Transactional(readOnly = true)
    public String exportarCsv(String idUsuario, String idGranja) {
        granjaAccesoService.validarAcceso(idUsuario, idGranja);
        List<List<String>> filas = new ArrayList<>();
        filas.add(List.of("codigo", "nombre", "precio_por_kilo"));
        materiaPrimaRepository
                .findByGranjaIdAndActivaTrueOrderByNombreMateriaPrimaAsc(idGranja)
                .forEach(mp -> filas.add(List.of(
                        mp.getCodigoMateriaPrima() == null ? "" : mp.getCodigoMateriaPrima(),
                        mp.getNombreMateriaPrima() == null ? "" : mp.getNombreMateriaPrima(),
                        mp.getPrecioPorKilo() == null ? "0" : mp.getPrecioPorKilo().toString())));
        return CsvWriter.escribir(filas);
    }

    /**
     * Importa MPs desde un CSV con header {@code codigo, nombre, precio_por_kilo} (RF-MP-004).
     * <p>Cada fila se procesa como un alta independiente: si falla la validación o
     * existe un código duplicado activo, se registra en {@code errores} y se continúa con
     * el resto. El plan-gating se evalúa por fila (cuenta MPs activas existentes).
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
                CsvFields.validarColumnas(fila, "codigo", "nombre");
                codigo = CsvFields.requerido(fila, "codigo");
                String nombre = CsvFields.requerido(fila, "nombre");
                Double precio = CsvFields.decimalOpcional(fila, "precio_por_kilo");
                if (precio == null) precio = 0.0;
                if (precio < 0) {
                    throw new ResponseStatusException(
                            HttpStatus.UNPROCESSABLE_ENTITY, "El precio no puede ser negativo");
                }
                validarLimitePlan(idUsuario, idGranja);
                if (materiaPrimaRepository.existsByGranjaIdAndCodigoMateriaPrimaIgnoreCaseAndActivaTrue(
                        idGranja, codigo)) {
                    throw new ResponseStatusException(
                            HttpStatus.CONFLICT,
                            "Ya existe una materia prima activa con código " + codigo);
                }
                MateriaPrima mp = MateriaPrima.builder()
                        .granja(granja)
                        .codigoMateriaPrima(codigo)
                        .nombreMateriaPrima(nombre)
                        .precioPorKilo(precio)
                        .precioBaseManual(precio)
                        .activa(true)
                        .fechaCreacion(ahora)
                        .fechaUltimaActualizacion(ahora)
                        .build();
                materiaPrimaRepository.save(mp);
                filasOk++;
            } catch (ResponseStatusException e) {
                errores.add(new CsvImportError(numeroLinea, codigo, e.getReason()));
            } catch (RuntimeException e) {
                errores.add(new CsvImportError(numeroLinea, codigo, "Error: " + e.getMessage()));
            }
        }
        return new CsvImportResult(filasOk, errores.size(), errores);
    }

    private MateriaPrima obtenerOFallar(Long idMateriaPrima, String idGranja) {
        return materiaPrimaRepository
                .findByIdAndGranjaId(idMateriaPrima, idGranja)
                .orElseThrow(() ->
                        new ResponseStatusException(HttpStatus.NOT_FOUND, "Materia prima no encontrada"));
    }

    private void validarLimitePlan(String idUsuario, String idGranja) {
        var plan = planService.obtenerPlanEfectivo(idUsuario);
        int limite = planService.limiteMateriasPrimas(plan);
        long actuales = materiaPrimaRepository.countByGranjaIdAndActivaTrue(idGranja);
        if (actuales >= limite) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Plan " + plan.name() + " permite hasta " + limite
                            + " materias primas activas por granja");
        }
    }
}
