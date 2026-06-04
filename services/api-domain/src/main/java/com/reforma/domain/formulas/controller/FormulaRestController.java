package com.reforma.domain.formulas.controller;

import com.reforma.domain.auth.SecurityUtils;
import com.reforma.domain.formulas.dto.FormulaCabeceraRequest;
import com.reforma.domain.formulas.dto.FormulaCompletaResponse;
import com.reforma.domain.formulas.dto.FormulaResumenResponse;
import com.reforma.domain.formulas.dto.GuardarFormulaDetalleRequest;
import com.reforma.domain.formulas.service.FormulaService;
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
@RequestMapping("/api/formulas/{idGranja}")
@RequiredArgsConstructor
public class FormulaRestController {

    private final FormulaService formulaService;

    @GetMapping
    public List<FormulaResumenResponse> listar(@PathVariable String idGranja) {
        return formulaService.listar(SecurityUtils.requireUserId(), idGranja);
    }

    @GetMapping("/{idFormula}")
    public FormulaCompletaResponse obtener(
            @PathVariable String idGranja, @PathVariable String idFormula) {
        return formulaService.obtener(SecurityUtils.requireUserId(), idGranja, idFormula);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public FormulaCompletaResponse crearCabecera(
            @PathVariable String idGranja, @Valid @RequestBody FormulaCabeceraRequest request) {
        return formulaService.crearCabecera(SecurityUtils.requireUserId(), idGranja, request);
    }

    @PutMapping("/{idFormula}")
    public FormulaCompletaResponse actualizarCabecera(
            @PathVariable String idGranja,
            @PathVariable String idFormula,
            @Valid @RequestBody FormulaCabeceraRequest request) {
        return formulaService.actualizarCabecera(
                SecurityUtils.requireUserId(), idGranja, idFormula, request);
    }

    @PutMapping("/{idFormula}/detalle")
    public FormulaCompletaResponse guardarDetalle(
            @PathVariable String idGranja,
            @PathVariable String idFormula,
            @Valid @RequestBody GuardarFormulaDetalleRequest request) {
        return formulaService.guardarDetalle(
                SecurityUtils.requireUserId(), idGranja, idFormula, request);
    }

    @DeleteMapping("/{idFormula}")
    public ResponseEntity<Void> desactivar(
            @PathVariable String idGranja, @PathVariable String idFormula) {
        formulaService.desactivar(SecurityUtils.requireUserId(), idGranja, idFormula);
        return ResponseEntity.noContent().build();
    }
}
