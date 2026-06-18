package com.reforma.domain.materiasprimas.controller;

import com.reforma.domain.auth.SecurityUtils;
import com.reforma.domain.common.csv.CsvImportResult;
import com.reforma.domain.materiasprimas.dto.MateriaPrimaRequest;
import com.reforma.domain.materiasprimas.dto.MateriaPrimaResponse;
import com.reforma.domain.materiasprimas.service.MateriaPrimaService;
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
@RequestMapping("/api/materias-primas/{idGranja}")
@RequiredArgsConstructor
public class MateriaPrimaRestController {

    private final MateriaPrimaService materiaPrimaService;

    @GetMapping
    public List<MateriaPrimaResponse> listar(@PathVariable String idGranja) {
        return materiaPrimaService.listarPorGranja(SecurityUtils.requireTenantId(), idGranja);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public MateriaPrimaResponse crear(
            @PathVariable String idGranja, @Valid @RequestBody MateriaPrimaRequest request) {
        return materiaPrimaService.crear(SecurityUtils.requireTenantId(), idGranja, request);
    }

    @PutMapping("/{idMateriaPrima}")
    public MateriaPrimaResponse actualizar(
            @PathVariable String idGranja,
            @PathVariable Long idMateriaPrima,
            @Valid @RequestBody MateriaPrimaRequest request) {
        return materiaPrimaService.actualizar(
                SecurityUtils.requireTenantId(), idGranja, idMateriaPrima, request);
    }

    @DeleteMapping("/{idMateriaPrima}")
    public ResponseEntity<Void> desactivar(
            @PathVariable String idGranja, @PathVariable Long idMateriaPrima) {
        materiaPrimaService.desactivar(SecurityUtils.requireTenantId(), idGranja, idMateriaPrima);
        return ResponseEntity.noContent().build();
    }

    /** RF-MP-004 — exporta MPs activas a CSV (UTF-8, columnas: codigo,nombre,precio_por_kilo). */
    @GetMapping(value = "/csv", produces = "text/csv; charset=UTF-8")
    public ResponseEntity<byte[]> exportarCsv(@PathVariable String idGranja) {
        String csv = materiaPrimaService.exportarCsv(SecurityUtils.requireTenantId(), idGranja);
        byte[] cuerpo = csv.getBytes(StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"materias-primas-" + idGranja + ".csv\"")
                .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
                .body(cuerpo);
    }

    /** RF-MP-004 — importa MPs desde CSV. Devuelve resumen con filas OK y errores. */
    @PostMapping(value = "/csv", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public CsvImportResult importarCsv(
            @PathVariable String idGranja, @RequestParam("archivo") MultipartFile archivo) {
        if (archivo == null || archivo.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Archivo CSV vacío o ausente");
        }
        try {
            return materiaPrimaService.importarCsv(
                    SecurityUtils.requireTenantId(), idGranja, archivo.getInputStream());
        } catch (IOException e) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "No se pudo leer el archivo: " + e.getMessage());
        }
    }
}
