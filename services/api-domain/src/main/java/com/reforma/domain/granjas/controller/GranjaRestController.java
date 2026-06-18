package com.reforma.domain.granjas.controller;

import com.reforma.domain.auth.SecurityUtils;
import com.reforma.domain.granjas.dto.GranjaRequest;
import com.reforma.domain.granjas.dto.GranjaResponse;
import com.reforma.domain.granjas.service.GranjaAplicacionService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/granjas")
@RequiredArgsConstructor
public class GranjaRestController {

    private final GranjaAplicacionService granjaAplicacionService;

    @GetMapping
    public List<GranjaResponse> listar() {
        return granjaAplicacionService.listarPorUsuario(SecurityUtils.requireTenantId());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public GranjaResponse crear(@Valid @RequestBody GranjaRequest request) {
        return granjaAplicacionService.crear(SecurityUtils.requireTenantId(), request);
    }

    @GetMapping("/{idGranja}")
    public GranjaResponse obtener(@PathVariable String idGranja) {
        return granjaAplicacionService.obtener(SecurityUtils.requireTenantId(), idGranja);
    }
}
