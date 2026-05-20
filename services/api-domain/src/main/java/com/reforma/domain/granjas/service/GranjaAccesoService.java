package com.reforma.domain.granjas.service;

import com.reforma.domain.granjas.repository.GranjaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Multi-tenancy: valida que el usuario autenticado pueda acceder a la granja (RF-GRN-005).
 */
@Service
@RequiredArgsConstructor
public class GranjaAccesoService {

    private final GranjaRepository granjaRepository;

    @Transactional(readOnly = true)
    public void validarAcceso(String idUsuario, String idGranja) {
        boolean ok = granjaRepository.findByIdAndUsuarioId(idGranja, idUsuario).isPresent();
        // TODO: empleados vinculados por codigoReferencia (RF-EMP-003)
        if (!ok) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Sin acceso a esta granja");
        }
    }
}
