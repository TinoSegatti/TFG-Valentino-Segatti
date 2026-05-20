package com.reforma.domain.granjas.service;

import com.reforma.domain.common.util.IdGenerator;
import com.reforma.domain.granjas.dto.GranjaRequest;
import com.reforma.domain.granjas.dto.GranjaResponse;
import com.reforma.domain.granjas.entity.Granja;
import com.reforma.domain.granjas.repository.GranjaRepository;
import com.reforma.domain.suscripciones.service.PlanService;
import com.reforma.domain.usuarios.repository.UsuarioRepository;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class GranjaAplicacionService {

    private final GranjaRepository granjaRepository;
    private final UsuarioRepository usuarioRepository;
    private final PlanService planService;
    private final GranjaAccesoService granjaAccesoService;

    @Transactional(readOnly = true)
    public List<GranjaResponse> listarPorUsuario(String idUsuario) {
        return granjaRepository.findByUsuarioIdAndActivaTrueOrderByNombreGranjaAsc(idUsuario).stream()
                .map(GranjaResponse::from)
                .toList();
    }

    @Transactional
    public GranjaResponse crear(String idUsuario, GranjaRequest request) {
        var usuario = usuarioRepository.findById(idUsuario).orElseThrow();
        var plan = planService.obtenerPlanEfectivo(idUsuario);
        var limite = planService.limiteGranjas(plan);
        var actuales = granjaRepository.countByUsuarioIdAndActivaTrue(idUsuario);
        if (actuales >= limite) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Límite de granjas alcanzado para el plan " + plan.name());
        }
        var granja = Granja.builder()
                .id(IdGenerator.newId())
                .usuario(usuario)
                .nombreGranja(request.nombreGranja().trim())
                .descripcion(request.descripcion())
                .fechaCreacion(Instant.now())
                .activa(true)
                .build();
        return GranjaResponse.from(granjaRepository.save(granja));
    }

    @Transactional(readOnly = true)
    public GranjaResponse obtener(String idUsuario, String idGranja) {
        granjaAccesoService.validarAcceso(idUsuario, idGranja);
        var granja = granjaRepository
                .findByIdAndUsuarioId(idGranja, idUsuario)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Granja no encontrada"));
        return GranjaResponse.from(granja);
    }
}
