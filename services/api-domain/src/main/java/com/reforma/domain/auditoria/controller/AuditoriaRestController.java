package com.reforma.domain.auditoria.controller;

import com.reforma.domain.auditoria.domain.AccionAuditoria;
import com.reforma.domain.auditoria.dto.PaginaAuditoria;
import com.reforma.domain.auditoria.service.AuditoriaConsultaService;
import com.reforma.domain.auth.SecurityUtils;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Consola de auditoría (RF-AUD, Etapa 5). Solo lectura para el dueño (OWNER) y el jefe (ADMIN);
 * el scoping al tenant lo aplica el servicio. Todos los filtros son opcionales.
 */
@RestController
@RequestMapping("/api/auditoria")
@RequiredArgsConstructor
public class AuditoriaRestController {

    private final AuditoriaConsultaService auditoriaConsultaService;

    @GetMapping
    @PreAuthorize("hasAnyRole('OWNER','ADMIN')")
    public PaginaAuditoria listar(
            @RequestParam(required = false) String idGranja,
            @RequestParam(required = false) String idUsuario,
            @RequestParam(required = false) AccionAuditoria accion,
            @RequestParam(required = false) Instant desde,
            @RequestParam(required = false) Instant hasta,
            @RequestParam(defaultValue = "0") int pagina,
            @RequestParam(defaultValue = "20") int tamano) {
        return auditoriaConsultaService.consultar(
                SecurityUtils.requireUserId(), idGranja, idUsuario, accion, desde, hasta, pagina, tamano);
    }
}
