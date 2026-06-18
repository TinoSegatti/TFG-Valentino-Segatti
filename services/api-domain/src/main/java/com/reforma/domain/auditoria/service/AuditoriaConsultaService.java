package com.reforma.domain.auditoria.service;

import com.reforma.domain.auditoria.domain.AccionAuditoria;
import com.reforma.domain.auditoria.dto.AuditoriaResponse;
import com.reforma.domain.auditoria.dto.PaginaAuditoria;
import com.reforma.domain.auditoria.entity.Auditoria;
import com.reforma.domain.auditoria.repository.AuditoriaRepository;
import com.reforma.domain.common.domain.RolEmpleado;
import com.reforma.domain.usuarios.entity.Usuario;
import com.reforma.domain.usuarios.repository.UsuarioRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Consola de auditoría (RF-AUD, Etapa 5): consulta de solo lectura sobre {@code t_auditoria}.
 *
 * <p><b>Scoping multi-tenant:</b> un dueño (o su jefe ADMIN) solo ve la actividad de su propio
 * tenant — los actores válidos son el dueño y sus empleados. El filtro opcional {@code idUsuario}
 * solo puede acotar dentro de ese conjunto; cualquier id ajeno simplemente no devuelve filas.
 */
@Service
@RequiredArgsConstructor
public class AuditoriaConsultaService {

    private static final int TAMANO_MAX = 100;

    private final AuditoriaRepository auditoriaRepository;
    private final UsuarioRepository usuarioRepository;

    @Transactional(readOnly = true)
    public PaginaAuditoria consultar(
            String idActor,
            String idGranja,
            String idUsuario,
            AccionAuditoria accion,
            Instant desde,
            Instant hasta,
            int pagina,
            int tamano) {
        var actor = usuarioRepository.findById(idActor).orElseThrow();
        var dueno = resolverDueno(actor);

        // Actores del tenant: el dueño + todos sus empleados (incluye pendientes y desactivados).
        var idsTenant = new ArrayList<String>();
        idsTenant.add(dueno.getId());
        usuarioRepository.findByUsuarioDuenoIdOrderByFechaVinculacionDesc(dueno.getId())
                .forEach(e -> idsTenant.add(e.getId()));

        var tamanoSeguro = Math.min(Math.max(tamano, 1), TAMANO_MAX);
        var pageable = PageRequest.of(
                Math.max(pagina, 0), tamanoSeguro, Sort.by(Sort.Direction.DESC, "fechaOperacion"));
        var page = auditoriaRepository.buscar(
                idsTenant,
                idGranja != null,
                idGranja,
                idUsuario != null,
                idUsuario,
                accion != null,
                accion,
                desde != null,
                desde,
                hasta != null,
                hasta,
                pageable);

        // Enriquecer con email/nombre del actor (una sola consulta para toda la página).
        var actores = page.getContent().stream().map(Auditoria::getIdUsuario).distinct().toList();
        Map<String, Usuario> porId = usuarioRepository.findAllById(actores).stream()
                .collect(Collectors.toMap(Usuario::getId, Function.identity()));

        List<AuditoriaResponse> contenido = page.getContent().stream()
                .map(a -> AuditoriaResponse.from(a, porId.get(a.getIdUsuario())))
                .toList();

        return new PaginaAuditoria(
                contenido,
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages());
    }

    /** Dueño efectivo: él mismo si es dueño; su dueño si es un jefe ADMIN activo. */
    private Usuario resolverDueno(Usuario actor) {
        if (!Boolean.TRUE.equals(actor.getEsUsuarioEmpleado())) {
            return actor;
        }
        if (actor.getRolEmpleado() == RolEmpleado.ADMIN
                && Boolean.TRUE.equals(actor.getActivoComoEmpleado())
                && actor.getUsuarioDueno() != null) {
            return actor.getUsuarioDueno();
        }
        throw new ResponseStatusException(
                HttpStatus.FORBIDDEN, "No tenés permisos para consultar la auditoría");
    }
}
