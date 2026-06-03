package com.reforma.domain.animales.controller;

import com.reforma.domain.animales.dto.AnimalRequest;
import com.reforma.domain.animales.dto.AnimalResponse;
import com.reforma.domain.animales.service.AnimalService;
import com.reforma.domain.auth.SecurityUtils;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

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
}
