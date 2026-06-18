package com.reforma.domain.granjas.service;

import com.reforma.domain.granjas.repository.GranjaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Multi-tenancy: valida que el tenant pueda acceder a la granja (RF-GRN-005).
 *
 * <p>El parámetro {@code idTenant} es el <em>dueño efectivo</em>, ya resuelto en el borde del
 * controller vía {@link com.reforma.domain.auth.SecurityUtils#requireTenantId()} (para un
 * empleado, el id de su dueño; para un dueño, su propio id). Como las granjas viven bajo el
 * dueño, basta con verificar la pertenencia contra ese id — el caso empleado queda cubierto.
 */
@Service
@RequiredArgsConstructor
public class GranjaAccesoService {

    private final GranjaRepository granjaRepository;

    @Transactional(readOnly = true)
    public void validarAcceso(String idTenant, String idGranja) {
        boolean ok = granjaRepository.findByIdAndUsuarioId(idGranja, idTenant).isPresent();
        if (!ok) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Sin acceso a esta granja");
        }
    }
}
