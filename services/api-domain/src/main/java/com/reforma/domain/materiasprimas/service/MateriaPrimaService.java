package com.reforma.domain.materiasprimas.service;

import com.reforma.domain.common.util.IdGenerator;
import com.reforma.domain.granjas.entity.Granja;
import com.reforma.domain.granjas.repository.GranjaRepository;
import com.reforma.domain.granjas.service.GranjaAccesoService;
import com.reforma.domain.materiasprimas.dto.MateriaPrimaRequest;
import com.reforma.domain.materiasprimas.dto.MateriaPrimaResponse;
import com.reforma.domain.materiasprimas.entity.MateriaPrima;
import com.reforma.domain.materiasprimas.repository.MateriaPrimaRepository;
import com.reforma.domain.suscripciones.service.PlanService;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class MateriaPrimaService {

    private final MateriaPrimaRepository materiaPrimaRepository;
    private final GranjaRepository granjaRepository;
    private final GranjaAccesoService granjaAccesoService;
    private final PlanService planService;

    @Transactional(readOnly = true)
    public List<MateriaPrimaResponse> listarPorGranja(String idUsuario, String idGranja) {
        granjaAccesoService.validarAcceso(idUsuario, idGranja);
        return materiaPrimaRepository
                .findByGranjaIdAndActivaTrueOrderByNombreMateriaPrimaAsc(idGranja)
                .stream()
                .map(MateriaPrimaResponse::from)
                .toList();
    }

    @Transactional
    public MateriaPrimaResponse crear(
            String idUsuario, String idGranja, MateriaPrimaRequest request) {
        granjaAccesoService.validarAcceso(idUsuario, idGranja);
        validarLimitePlan(idUsuario, idGranja);

        String codigo = request.codigoMateriaPrima().trim();
        if (materiaPrimaRepository.existsByGranjaIdAndCodigoMateriaPrimaIgnoreCase(idGranja, codigo)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Ya existe una materia prima con código " + codigo + " en esta granja");
        }
        Granja granja = granjaRepository.findById(idGranja).orElseThrow();
        Instant now = Instant.now();
        MateriaPrima mp = MateriaPrima.builder()
                .id(IdGenerator.newId())
                .granja(granja)
                .codigoMateriaPrima(codigo)
                .nombreMateriaPrima(request.nombreMateriaPrima().trim())
                .precioPorKilo(request.precioPorKilo() != null ? request.precioPorKilo() : 0.0)
                .activa(true)
                .fechaCreacion(now)
                .fechaUltimaActualizacion(now)
                .build();
        return MateriaPrimaResponse.from(materiaPrimaRepository.save(mp));
    }

    @Transactional
    public MateriaPrimaResponse actualizar(
            String idUsuario, String idGranja, String idMateriaPrima, MateriaPrimaRequest request) {
        granjaAccesoService.validarAcceso(idUsuario, idGranja);
        MateriaPrima mp = obtenerOFallar(idMateriaPrima, idGranja);
        String nuevoCodigo = request.codigoMateriaPrima().trim();
        if (!mp.getCodigoMateriaPrima().equalsIgnoreCase(nuevoCodigo)
                && materiaPrimaRepository.existsByGranjaIdAndCodigoMateriaPrimaIgnoreCase(
                        idGranja, nuevoCodigo)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "Código duplicado en la granja: " + nuevoCodigo);
        }
        mp.setCodigoMateriaPrima(nuevoCodigo);
        mp.setNombreMateriaPrima(request.nombreMateriaPrima().trim());
        if (request.precioPorKilo() != null) {
            mp.setPrecioPorKilo(request.precioPorKilo());
        }
        mp.setFechaUltimaActualizacion(Instant.now());
        return MateriaPrimaResponse.from(mp);
    }

    @Transactional
    public void desactivar(String idUsuario, String idGranja, String idMateriaPrima) {
        granjaAccesoService.validarAcceso(idUsuario, idGranja);
        MateriaPrima mp = obtenerOFallar(idMateriaPrima, idGranja);
        mp.setActiva(false);
        mp.setFechaUltimaActualizacion(Instant.now());
    }

    private MateriaPrima obtenerOFallar(String idMateriaPrima, String idGranja) {
        return materiaPrimaRepository
                .findByIdAndGranjaId(idMateriaPrima, idGranja)
                .orElseThrow(() ->
                        new ResponseStatusException(HttpStatus.NOT_FOUND, "Materia prima no encontrada"));
    }

    private void validarLimitePlan(String idUsuario, String idGranja) {
        var plan = planService.obtenerPlanEfectivo(idUsuario);
        int limite = planService.limiteMateriasPrimas(plan);
        long actuales = materiaPrimaRepository.countByGranjaIdAndActivaTrue(idGranja);
        if (actuales >= limite) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Plan " + plan.name() + " permite hasta " + limite
                            + " materias primas activas por granja");
        }
    }
}
