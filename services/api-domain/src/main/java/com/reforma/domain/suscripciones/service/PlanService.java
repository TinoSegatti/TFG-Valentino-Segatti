package com.reforma.domain.suscripciones.service;

import com.reforma.domain.common.domain.PlanSuscripcion;
import com.reforma.domain.usuarios.entity.Usuario;
import com.reforma.domain.usuarios.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PlanService {

    private final UsuarioRepository usuarioRepository;

    @Transactional(readOnly = true)
    public PlanSuscripcion obtenerPlanEfectivo(String idUsuario) {
        Usuario u = usuarioRepository.findById(idUsuario).orElseThrow();
        if (Boolean.TRUE.equals(u.getEsUsuarioEmpleado()) && u.getUsuarioDueno() != null) {
            return u.getUsuarioDueno().getPlanSuscripcion();
        }
        return u.getPlanSuscripcion();
    }

    public int limiteGranjas(PlanSuscripcion plan) {
        return switch (plan) {
            case DEMO, STARTER -> 1;
            case BUSINESS -> 3;
            case ENTERPRISE -> Integer.MAX_VALUE;
        };
    }

    /** RF-MP-003: límite de materias primas activas por plan. */
    public int limiteMateriasPrimas(PlanSuscripcion plan) {
        return switch (plan) {
            case DEMO -> 10;
            case STARTER -> 30;
            case BUSINESS -> 100;
            case ENTERPRISE -> Integer.MAX_VALUE;
        };
    }

    /**
     * Límite de proveedores activos por plan (Sprint 2 — RF-PROV-002).
     * Los valores siguen la lógica de los demás recursos: DEMO restrictivo para forzar upgrade,
     * STARTER suficiente para PYMEs, BUSINESS para operaciones medianas y ENTERPRISE sin tope.
     */
    public int limiteProveedores(PlanSuscripcion plan) {
        return switch (plan) {
            case DEMO -> 5;
            case STARTER -> 15;
            case BUSINESS -> 50;
            case ENTERPRISE -> Integer.MAX_VALUE;
        };
    }

    /**
     * Límite de animales activos por plan (Sprint 2 — RF-ANI-002).
     * Los animales suelen ser pocos por granja (categorías productivas), por lo que el
     * tope DEMO es estricto y STARTER cubre la inmensa mayoría de PYMEs.
     */
    public int limiteAnimales(PlanSuscripcion plan) {
        return switch (plan) {
            case DEMO -> 5;
            case STARTER -> 20;
            case BUSINESS -> 100;
            case ENTERPRISE -> Integer.MAX_VALUE;
        };
    }

    /** RF-FORM-005: limite de formulas activas por plan. */
    public int limiteFormulas(PlanSuscripcion plan) {
        return switch (plan) {
            case DEMO -> 3;
            case STARTER -> 10;
            case BUSINESS -> 50;
            case ENTERPRISE -> Integer.MAX_VALUE;
        };
    }

    /** RF-FAB-005: limite de fabricaciones activas por plan. */
    public int limiteFabricaciones(PlanSuscripcion plan) {
        return switch (plan) {
            case DEMO -> 5;
            case STARTER -> 50;
            case BUSINESS -> 500;
            case ENTERPRISE -> Integer.MAX_VALUE;
        };
    }
}
