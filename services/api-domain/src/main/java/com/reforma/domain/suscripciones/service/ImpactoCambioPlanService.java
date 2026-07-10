package com.reforma.domain.suscripciones.service;

import com.reforma.domain.animales.repository.AnimalRepository;
import com.reforma.domain.archivos.repository.ArchivoRepository;
import com.reforma.domain.common.domain.PlanSuscripcion;
import com.reforma.domain.fabricaciones.repository.FabricacionRepository;
import com.reforma.domain.formulas.repository.FormulaCabeceraRepository;
import com.reforma.domain.granjas.entity.Granja;
import com.reforma.domain.granjas.repository.GranjaRepository;
import com.reforma.domain.materiasprimas.repository.MateriaPrimaRepository;
import com.reforma.domain.proveedores.repository.ProveedorRepository;
import com.reforma.domain.suscripciones.domain.EstadoSuscripcion;
import com.reforma.domain.suscripciones.dto.CambioPlanImpactoResponse;
import com.reforma.domain.suscripciones.dto.CambioPlanImpactoResponse.ImpactoRecurso;
import com.reforma.domain.suscripciones.dto.CambioPlanImpactoResponse.TipoCambio;
import com.reforma.domain.suscripciones.repository.SuscripcionRepository;
import com.reforma.domain.usuarios.repository.UsuarioRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Preview del impacto de pasar al plan destino (RD-P6.c): qué recursos bloquean la
 * contratación (hoy solo empleados, RD-P6.b) y cuáles quedarían en sobre-límite como
 * advertencia (RD-P6.a: nada se borra ni se bloquea; solo no se puede crear más hasta
 * bajar del límite). Con destino DEMO los empleados excedentes son advertencia, no
 * bloqueante: la cancelación nunca se bloquea y el sistema los desactiva al aplicarse
 * (RD-P6.b.4).
 */
@Service
@RequiredArgsConstructor
public class ImpactoCambioPlanService {

    private final UsuarioRepository usuarioRepository;
    private final SuscripcionRepository suscripcionRepository;
    private final PlanService planService;
    private final GranjaRepository granjaRepository;
    private final MateriaPrimaRepository materiaPrimaRepository;
    private final ProveedorRepository proveedorRepository;
    private final AnimalRepository animalRepository;
    private final FormulaCabeceraRepository formulaCabeceraRepository;
    private final FabricacionRepository fabricacionRepository;
    private final ArchivoRepository archivoRepository;

    @Transactional(readOnly = true)
    public CambioPlanImpactoResponse impacto(String idDueno, PlanSuscripcion planDestino) {
        var planActual = planService.obtenerPlanEfectivo(idDueno);
        var tipoCambio = tipoCambio(planActual, planDestino);

        List<ImpactoRecurso> bloqueantes = new ArrayList<>();
        List<ImpactoRecurso> advertencias = new ArrayList<>();

        long empleados = usuarioRepository.countByUsuarioDuenoIdAndActivoComoEmpleadoTrue(idDueno);
        int limiteEmpleados = planService.limiteEmpleados(planDestino);
        if (empleados > limiteEmpleados) {
            var impacto = new ImpactoRecurso(
                    "empleados", null, empleados, limiteEmpleados, empleados - limiteEmpleados);
            // Con destino DEMO (cancelación) nunca se bloquea: se desactivan solos (RD-P6.b.4).
            (planDestino == PlanSuscripcion.DEMO ? advertencias : bloqueantes).add(impacto);
        }

        var granjas = granjaRepository.findByUsuarioIdAndActivaTrueOrderByNombreGranjaAsc(idDueno);
        agregarSiExcede(advertencias, "granjas", null,
                granjas.size(), planService.limiteGranjas(planDestino));
        for (Granja granja : granjas) {
            String id = granja.getId();
            String nombre = granja.getNombreGranja();
            agregarSiExcede(advertencias, "materiasPrimas", nombre,
                    materiaPrimaRepository.countByGranjaIdAndActivaTrue(id),
                    planService.limiteMateriasPrimas(planDestino));
            agregarSiExcede(advertencias, "proveedores", nombre,
                    proveedorRepository.countByGranjaIdAndActivoTrue(id),
                    planService.limiteProveedores(planDestino));
            agregarSiExcede(advertencias, "animales", nombre,
                    animalRepository.countByGranjaIdAndActivoTrue(id),
                    planService.limiteAnimales(planDestino));
            agregarSiExcede(advertencias, "formulas", nombre,
                    formulaCabeceraRepository.countByGranjaIdAndActivaTrue(id),
                    planService.limiteFormulas(planDestino));
            agregarSiExcede(advertencias, "fabricaciones", nombre,
                    fabricacionRepository.countByGranjaIdAndActivoTrue(id),
                    planService.limiteFabricaciones(planDestino));
            agregarSiExcede(advertencias, "archivos", nombre,
                    archivoRepository.countByIdGranja(id),
                    planService.limiteArchivos(planDestino));
        }

        return new CambioPlanImpactoResponse(
                planActual, planDestino, tipoCambio,
                aplicaDesde(idDueno, tipoCambio), bloqueantes, advertencias);
    }

    private static TipoCambio tipoCambio(PlanSuscripcion actual, PlanSuscripcion destino) {
        if (destino == actual) {
            return TipoCambio.SIN_CAMBIO;
        }
        return destino.ordinal() > actual.ordinal() ? TipoCambio.UPGRADE : TipoCambio.DOWNGRADE;
    }

    /** Upgrades aplican ya (RD-P4); downgrades al vencer el ciclo pagado vigente (RD-P5/P7). */
    private Instant aplicaDesde(String idDueno, TipoCambio tipoCambio) {
        if (tipoCambio != TipoCambio.DOWNGRADE) {
            return Instant.now();
        }
        return suscripcionRepository.findByIdUsuario(idDueno)
                .filter(s -> s.getEstado() == EstadoSuscripcion.ACTIVA
                        || s.getEstado() == EstadoSuscripcion.CANCELADA)
                .map(s -> s.getFechaFinPeriodo())
                .orElseGet(Instant::now);
    }

    private static void agregarSiExcede(
            List<ImpactoRecurso> destino, String recurso, String granja, long actual, int limite) {
        if (actual > limite) {
            destino.add(new ImpactoRecurso(recurso, granja, actual, limite, actual - limite));
        }
    }
}
