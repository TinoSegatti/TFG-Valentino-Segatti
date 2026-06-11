package com.reforma.domain.animales.controller;

import com.reforma.domain.animales.dto.AnimalRequest;
import com.reforma.domain.animales.dto.AnimalResponse;
import com.reforma.domain.animales.service.AnimalService;
import com.reforma.domain.auth.SecurityUtils;
import com.reforma.domain.common.csv.CsvImportResult;
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

/**
 * Endpoints REST del catálogo Animales (RF-ANI-*).
 * Multi-tenant por path: {@code /api/animales/{idGranja}[/{id}]}.
 */
@RestController
@RequestMapping("/api/animales/{idGranja}")
@RequiredArgsConstructor
public class AnimalRestController {

    private final AnimalService animalService;

    @GetMapping
    public List<AnimalResponse> listar(
            @PathVariable String idGranja,
            @RequestParam(name = "buscar", required = false) String buscar) {
        return animalService.listarPorGranja(SecurityUtils.requireUserId(), idGranja, buscar);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AnimalResponse crear(
            @PathVariable String idGranja, @Valid @RequestBody AnimalRequest request) {
        return animalService.crear(SecurityUtils.requireUserId(), idGranja, request);
    }

    @PutMapping("/{idAnimal}")
    public AnimalResponse actualizar(
            @PathVariable String idGranja,
            @PathVariable Long idAnimal,
            @Valid @RequestBody AnimalRequest request) {
        return animalService.actualizar(
                SecurityUtils.requireUserId(), idGranja, idAnimal, request);
    }

    @DeleteMapping("/{idAnimal}")
    public ResponseEntity<Void> desactivar(
            @PathVariable String idGranja, @PathVariable Long idAnimal) {
        animalService.desactivar(SecurityUtils.requireUserId(), idGranja, idAnimal);
        return ResponseEntity.noContent().build();
    }

    /**
     * RF-ANI-003 — exporta animales activos a CSV (UTF-8). Columnas:
     * {@code codigo, descripcion, categoria, observaciones}.
     */
    @GetMapping(value = "/csv", produces = "text/csv; charset=UTF-8")
    public ResponseEntity<byte[]> exportarCsv(@PathVariable String idGranja) {
        String csv = animalService.exportarCsv(SecurityUtils.requireUserId(), idGranja);
        byte[] cuerpo = csv.getBytes(StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"animales-" + idGranja + ".csv\"")
                .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
                .body(cuerpo);
    }

    /** RF-ANI-003 — importa animales desde CSV. Devuelve resumen con filas OK y errores. */
    @PostMapping(value = "/csv", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public CsvImportResult importarCsv(
            @PathVariable String idGranja, @RequestParam("archivo") MultipartFile archivo) {
        if (archivo == null || archivo.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Archivo CSV vacío o ausente");
        }
        try {
            return animalService.importarCsv(
                    SecurityUtils.requireUserId(), idGranja, archivo.getInputStream());
        } catch (IOException e) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "No se pudo leer el archivo: " + e.getMessage());
        }
    }
}
