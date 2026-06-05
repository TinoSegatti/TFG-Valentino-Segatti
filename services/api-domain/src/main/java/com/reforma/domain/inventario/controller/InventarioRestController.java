package com.reforma.domain.inventario.controller;

import com.reforma.domain.auth.SecurityUtils;
import com.reforma.domain.inventario.dto.ActualizarCantidadRealRequest;
import com.reforma.domain.inventario.dto.InicializarInventarioRequest;
import com.reforma.domain.inventario.dto.InventarioListadoResponse;
import com.reforma.domain.inventario.dto.InventarioResponse;
import com.reforma.domain.inventario.service.InventarioService;
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
@RequestMapping("/api/inventario/{idGranja}")
@RequiredArgsConstructor
public class InventarioRestController {

    private final InventarioService inventarioService;

    @GetMapping
    public InventarioListadoResponse listar(@PathVariable String idGranja) {
        return inventarioService.listar(SecurityUtils.requireUserId(), idGranja);
    }

    @PostMapping("/inicializar")
    @ResponseStatus(HttpStatus.CREATED)
    public List<InventarioResponse> inicializar(
            @PathVariable String idGranja,
            @Valid @RequestBody InicializarInventarioRequest request) {
        return inventarioService.inicializar(SecurityUtils.requireUserId(), idGranja, request);
    }

    @PutMapping("/materia-prima/{idMateriaPrima}/cantidad-real")
    public InventarioResponse actualizarCantidadReal(
            @PathVariable String idGranja,
            @PathVariable Long idMateriaPrima,
            @Valid @RequestBody ActualizarCantidadRealRequest request) {
        return inventarioService.actualizarCantidadReal(
                SecurityUtils.requireUserId(), idGranja, idMateriaPrima, request);
    }

    @PostMapping("/recalcular")
    public InventarioListadoResponse recalcular(@PathVariable String idGranja) {
        return inventarioService.recalcularTodo(SecurityUtils.requireUserId(), idGranja);
    }

    @DeleteMapping
    public ResponseEntity<Void> vaciar(@PathVariable String idGranja) {
        inventarioService.vaciar(SecurityUtils.requireUserId(), idGranja);
        return ResponseEntity.noContent().build();
    }
}
