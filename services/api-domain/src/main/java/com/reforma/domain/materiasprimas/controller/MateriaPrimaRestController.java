package com.reforma.domain.materiasprimas.controller;

import com.reforma.domain.auth.SecurityUtils;
import com.reforma.domain.materiasprimas.dto.MateriaPrimaRequest;
import com.reforma.domain.materiasprimas.dto.MateriaPrimaResponse;
import com.reforma.domain.materiasprimas.service.MateriaPrimaService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/materias-primas/{idGranja}")
@RequiredArgsConstructor
public class MateriaPrimaRestController {

    private final MateriaPrimaService materiaPrimaService;

    @GetMapping
    public List<MateriaPrimaResponse> listar(@PathVariable String idGranja) {
        return materiaPrimaService.listarPorGranja(SecurityUtils.requireUserId(), idGranja);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public MateriaPrimaResponse crear(
            @PathVariable String idGranja, @Valid @RequestBody MateriaPrimaRequest request) {
        return materiaPrimaService.crear(SecurityUtils.requireUserId(), idGranja, request);
    }

    @PutMapping("/{idMateriaPrima}")
    public MateriaPrimaResponse actualizar(
            @PathVariable String idGranja,
            @PathVariable String idMateriaPrima,
            @Valid @RequestBody MateriaPrimaRequest request) {
        return materiaPrimaService.actualizar(
                SecurityUtils.requireUserId(), idGranja, idMateriaPrima, request);
    }

    @DeleteMapping("/{idMateriaPrima}")
    public ResponseEntity<Void> desactivar(
            @PathVariable String idGranja, @PathVariable String idMateriaPrima) {
        materiaPrimaService.desactivar(SecurityUtils.requireUserId(), idGranja, idMateriaPrima);
        return ResponseEntity.noContent().build();
    }
}
