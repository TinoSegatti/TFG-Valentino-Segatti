package com.reforma.domain.suscripciones.controller;

import com.reforma.domain.auth.SecurityUtils;
import com.reforma.domain.common.domain.PlanSuscripcion;
import com.reforma.domain.suscripciones.dto.CambioPlanImpactoResponse;
import com.reforma.domain.suscripciones.dto.CheckoutRequest;
import com.reforma.domain.suscripciones.dto.CheckoutResponse;
import com.reforma.domain.suscripciones.dto.ConfirmacionSimuladaRequest;
import com.reforma.domain.suscripciones.dto.PaginaPagos;
import com.reforma.domain.suscripciones.dto.PlanCatalogoResponse;
import com.reforma.domain.suscripciones.dto.SuscripcionResponse;
import com.reforma.domain.suscripciones.service.ImpactoCambioPlanService;
import com.reforma.domain.suscripciones.service.SuscripcionService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Módulo Suscripciones (Etapas 1 y 2). El catálogo es público ({@code /api/suscripcion/planes}
 * ya está en {@code SecurityConfig.PUBLIC}: se usa desde la landing sin login); el resto
 * es exclusivo del dueño (RD-P10: los empleados heredan el plan pero no gestionan la
 * suscripción — reciben 403).
 */
@RestController
@RequestMapping("/api/suscripcion")
@RequiredArgsConstructor
public class SuscripcionRestController {

    private final SuscripcionService suscripcionService;
    private final ImpactoCambioPlanService impactoCambioPlanService;

    /** Catálogo público de planes: precios ARS + límites reales, sin datos sensibles. */
    @GetMapping("/planes")
    public List<PlanCatalogoResponse> planes() {
        return suscripcionService.catalogo();
    }

    /** Mi suscripción (dueño autenticado); DEMO implícito si nunca contrató. */
    @GetMapping
    @PreAuthorize("hasRole('OWNER')")
    public SuscripcionResponse miSuscripcion() {
        return suscripcionService.obtenerMiSuscripcion(SecurityUtils.requireUserId());
    }

    /** Historial de cobros, el más reciente primero. */
    @GetMapping("/pagos")
    @PreAuthorize("hasRole('OWNER')")
    public PaginaPagos pagos(
            @RequestParam(defaultValue = "0") int pagina,
            @RequestParam(defaultValue = "20") int tamano) {
        return suscripcionService.listarPagos(SecurityUtils.requireUserId(), pagina, tamano);
    }

    /**
     * Impacto de cambiar al plan {@code plan} (RD-P6.c): bloqueantes y advertencias para el
     * modal de confirmación, sin modificar nada.
     */
    @GetMapping("/cambio-impacto")
    @PreAuthorize("hasRole('OWNER')")
    public CambioPlanImpactoResponse cambioImpacto(@RequestParam PlanSuscripcion plan) {
        return impactoCambioPlanService.impacto(SecurityUtils.requireUserId(), plan);
    }

    /**
     * Inicia la contratación: upgrade/primera contratación → URL de pago (RD-P4);
     * downgrade con suscripción activa → queda programado a fin de ciclo (RD-P5).
     * 409 estructurado si el equipo supera el límite del plan destino (RD-P6.b.1).
     */
    @PostMapping("/checkout")
    @PreAuthorize("hasRole('OWNER')")
    public CheckoutResponse checkout(@Valid @RequestBody CheckoutRequest request) {
        return suscripcionService.checkout(SecurityUtils.requireUserId(), request);
    }

    /** Cierre del checkout simulado (RD-P2). 404 en modo {@code mp}. */
    @PostMapping("/confirmar-simulado")
    @PreAuthorize("hasRole('OWNER')")
    public SuscripcionResponse confirmarSimulado(
            @Valid @RequestBody ConfirmacionSimuladaRequest request) {
        return suscripcionService.confirmarSimulado(SecurityUtils.requireUserId(), request);
    }

    /** Cancela la suscripción (RD-P7): plan vigente hasta fin de ciclo, luego DEMO. */
    @PostMapping("/cancelar")
    @PreAuthorize("hasRole('OWNER')")
    public SuscripcionResponse cancelar() {
        return suscripcionService.cancelar(SecurityUtils.requireUserId());
    }

    /** Revierte un downgrade o cancelación aún no aplicados ("Mantener mi plan"). */
    @PostMapping("/reactivar")
    @PreAuthorize("hasRole('OWNER')")
    public SuscripcionResponse reactivar() {
        return suscripcionService.reactivar(SecurityUtils.requireUserId());
    }
}
