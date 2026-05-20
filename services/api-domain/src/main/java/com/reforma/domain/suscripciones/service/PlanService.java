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
}
