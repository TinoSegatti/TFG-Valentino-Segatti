package com.reforma.domain.formulas.controller;

import com.reforma.domain.auth.SecurityUtils;
import com.reforma.domain.common.csv.CsvImportResult;
import com.reforma.domain.formulas.dto.FormulaCabeceraRequest;
import com.reforma.domain.formulas.dto.FormulaCompletaResponse;
import com.reforma.domain.formulas.dto.FormulaResumenResponse;
import com.reforma.domain.formulas.dto.GuardarFormulaDetalleRequest;
import com.reforma.domain.formulas.service.FormulaService;
import jakarta.validation.Valid;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/formulas/{idGranja}")
@RequiredArgsConstructor
public class FormulaRestController {

    private final FormulaService formulaService;

    @GetMapping
    public List<FormulaResumenResponse> listar(@PathVariable String idGranja) {
        return formulaService.listar(SecurityUtils.requireTenantId(), idGranja);
    }

    @GetMapping("/{idFormula}")
    public FormulaCompletaResponse obtener(
            @PathVariable String idGranja, @PathVariable String idFormula) {
        return formulaService.obtener(SecurityUtils.requireTenantId(), idGranja, idFormula);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public FormulaCompletaResponse crearCabecera(
            @PathVariable String idGranja, @Valid @RequestBody FormulaCabeceraRequest request) {
        return formulaService.crearCabecera(SecurityUtils.requireTenantId(), idGranja, request);
    }

    @PutMapping("/{idFormula}")
    public FormulaCompletaResponse actualizarCabecera(
            @PathVariable String idGranja,
            @PathVariable String idFormula,
            @Valid @RequestBody FormulaCabeceraRequest request) {
        return formulaService.actualizarCabecera(
                SecurityUtils.requireTenantId(), idGranja, idFormula, request);
    }

    @PutMapping("/{idFormula}/detalle")
    public FormulaCompletaResponse guardarDetalle(
            @PathVariable String idGranja,
            @PathVariable String idFormula,
            @Valid @RequestBody GuardarFormulaDetalleRequest request) {
        return formulaService.guardarDetalle(
                SecurityUtils.requireTenantId(), idGranja, idFormula, request);
    }

    @DeleteMapping("/{idFormula}")
    public ResponseEntity<Void> desactivar(
            @PathVariable String idGranja, @PathVariable String idFormula) {
        formulaService.desactivar(SecurityUtils.requireTenantId(), idGranja, idFormula);
        return ResponseEntity.noContent().build();
    }

    /**
     * Exporta todas las fórmulas activas (cabecera + detalles) a un único CSV "denormalizado":
     * una fila por ingrediente, con datos de cabecera repetidos para permitir reimport vía
     * agrupación por {@code codigo_formula}.
     */
    @GetMapping(value = "/csv", produces = "text/csv; charset=UTF-8")
    public ResponseEntity<byte[]> exportarCsv(@PathVariable String idGranja) {
        String csv = formulaService.exportarCsv(SecurityUtils.requireTenantId(), idGranja);
        byte[] cuerpo = csv.getBytes(StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"formulas-" + idGranja + ".csv\"")
                .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
                .body(cuerpo);
    }

    /**
     * Importa fórmulas desde un CSV "denormalizado" (cabecera + detalle en el mismo archivo).
     * Cada fórmula se agrupa por {@code codigo_formula}, se valida (1000 kg, animal y MPs
     * existentes, consistencia de cabecera) y se persiste en su propia transacción.
     * El resultado reporta cuántas fórmulas se importaron y el detalle por fórmula con error.
     */
    @PostMapping(value = "/csv", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public CsvImportResult importarCsv(
            @PathVariable String idGranja, @RequestParam("archivo") MultipartFile archivo) {
        if (archivo == null || archivo.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Archivo CSV vacío o ausente");
        }
        try {
            return formulaService.importarCsv(
                    SecurityUtils.requireTenantId(), idGranja, archivo.getInputStream());
        } catch (IOException e) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "No se pudo leer el archivo: " + e.getMessage());
        }
    }
}
