package com.reforma.domain.auditoria.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.reforma.domain.auditoria.dto.AuditoriaEvento;
import com.reforma.domain.auditoria.entity.Auditoria;
import com.reforma.domain.auditoria.repository.AuditoriaRepository;
import com.reforma.domain.common.util.IdGenerator;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Escribe la pista de auditoría en {@code t_auditoria} (RF-AUD). Captura IP y User-Agent
 * del request en curso (best-effort: nulos fuera de un contexto web). La auditoría nunca
 * debe romper la operación de negocio: los errores de serialización se degradan a {@code null}.
 *
 * <p>Dos semánticas de transacción:
 * <ul>
 *   <li>{@link #registrar} — se une a la transacción del llamador ({@code REQUIRED}). Para
 *       operaciones de escritura donde el registro auditado vive en la misma transacción
 *       (p. ej. el alta de un usuario recién persistido).</li>
 *   <li>{@link #registrarIndependiente} — transacción nueva ({@code REQUIRES_NEW}). Para
 *       eventos que ocurren en un flujo que aborta/relanza (p. ej. login fallido): la fila
 *       de auditoría debe persistir aunque la transacción externa haga rollback. El usuario
 *       referenciado por la FK ya debe existir y estar confirmado.</li>
 * </ul>
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class AuditoriaService {

    private final AuditoriaRepository auditoriaRepository;
    private final ObjectMapper objectMapper;

    @Transactional(propagation = Propagation.REQUIRED)
    public void registrar(AuditoriaEvento evento) {
        persistir(evento);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void registrarIndependiente(AuditoriaEvento evento) {
        persistir(evento);
    }

    private void persistir(AuditoriaEvento evento) {
        var request = requestActual();
        var auditoria = Auditoria.builder()
                .id(IdGenerator.newId())
                .idUsuario(evento.idUsuario())
                .idGranja(evento.idGranja())
                .tablaOrigen(evento.tablaOrigen())
                .idRegistro(evento.idRegistro())
                .accion(evento.accion())
                .descripcion(evento.descripcion())
                .datosAnterioresJson(aJson(evento.datosAnteriores()))
                .datosNuevosJson(aJson(evento.datosNuevos()))
                .fechaOperacion(Instant.now())
                .ipAddress(ipCliente(request))
                .userAgent(request == null ? null : request.getHeader("User-Agent"))
                .build();
        auditoriaRepository.save(auditoria);
    }

    private String aJson(Object datos) {
        if (datos == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(datos);
        } catch (Exception e) {
            log.warn("No se pudo serializar datos de auditoría a JSON: {}", e.getMessage());
            return null;
        }
    }

    private static HttpServletRequest requestActual() {
        if (RequestContextHolder.getRequestAttributes()
                instanceof ServletRequestAttributes attributes) {
            return attributes.getRequest();
        }
        return null;
    }

    private static String ipCliente(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        var forwarded = request.getHeader("X-Forwarded-For");
        var ip = (forwarded == null || forwarded.isBlank())
                ? request.getRemoteAddr()
                : forwarded.split(",")[0].trim();
        if (ip != null && ip.length() > 45) {
            ip = ip.substring(0, 45);
        }
        return ip;
    }
}
