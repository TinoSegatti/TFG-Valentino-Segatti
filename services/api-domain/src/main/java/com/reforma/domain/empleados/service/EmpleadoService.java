package com.reforma.domain.empleados.service;

import com.reforma.domain.auditoria.domain.AccionAuditoria;
import com.reforma.domain.auditoria.dto.AuditoriaEvento;
import com.reforma.domain.auditoria.service.AuditoriaService;
import com.reforma.domain.auth.jwt.TokenVersionCache;
import com.reforma.domain.common.domain.RolEmpleado;
import com.reforma.domain.common.domain.TipoUsuario;
import com.reforma.domain.common.util.IdGenerator;
import com.reforma.domain.empleados.dto.AceptarInvitacionRequest;
import com.reforma.domain.empleados.dto.EmpleadoResponse;
import com.reforma.domain.empleados.dto.InvitarEmpleadoRequest;
import com.reforma.domain.suscripciones.service.SuscripcionService;
import com.reforma.domain.usuarios.email.EmailNotificacionService;
import com.reforma.domain.usuarios.entity.Usuario;
import com.reforma.domain.usuarios.repository.UsuarioRepository;
import com.reforma.domain.usuarios.token.domain.TipoToken;
import com.reforma.domain.usuarios.token.service.TokenSeguridadService;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Invitación, alta y gestión de cuentas de empleado (Etapas 2–4 de {@code docs/MODULO_USUARIOS.md}).
 *
 * <p>Jerarquía de la cuenta de un dueño (tenant):
 * <ul>
 *   <li><b>OWNER</b> (dueño): gestión total del equipo, incluido designar/quitar jefes (ADMIN).</li>
 *   <li><b>ADMIN</b> (jefe): gestiona empleados EDITOR/LECTOR; <b>no</b> puede tocar a otro ADMIN,
 *       designar ADMIN ni modificarse a sí mismo.</li>
 *   <li><b>EDITOR/LECTOR</b>: no gestionan equipo (bloqueados por authority antes de llegar aquí).</li>
 * </ul>
 * Cuentas separadas (decisión §0): un email es dueño O empleado; email existente → 409.
 */
@Service
@RequiredArgsConstructor
public class EmpleadoService {

    private static final String TABLA_USUARIOS = "t_usuarios";
    private static final Duration VIGENCIA_INVITACION = Duration.ofHours(72);

    private final UsuarioRepository usuarioRepository;
    private final SuscripcionService suscripcionService;
    private final TokenSeguridadService tokenSeguridadService;
    private final EmailNotificacionService emailNotificacionService;
    private final AuditoriaService auditoriaService;
    private final PasswordEncoder passwordEncoder;
    private final TokenVersionCache tokenVersionCache;

    // ---------------------------------------------------------------- invitar

    /**
     * Crea un empleado pendiente bajo el dueño efectivo del {@code actor} (el propio dueño, o el
     * dueño del jefe ADMIN que invita), con plan-gating sobre el plan del dueño, token de 72 h y email.
     */
    @Transactional
    public EmpleadoResponse invitar(String idActor, InvitarEmpleadoRequest request) {
        var actor = cargar(idActor);
        var dueno = resolverDueno(actor);
        // Un jefe ADMIN no puede crear otros administradores; solo el dueño designa jefes.
        if (esJefe(actor) && request.rol() == RolEmpleado.ADMIN) {
            throw prohibido("Un jefe no puede crear administradores; solo el dueño puede designarlos");
        }

        var email = request.email().toLowerCase().trim();
        if (usuarioRepository.existsByEmailIgnoreCase(email)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "El email ya está registrado");
        }

        validarCupoEmpleados(dueno.getId());

        var empleado = Usuario.builder()
                .id(IdGenerator.newId())
                .email(email)
                .passwordHash(null) // pendiente: la fija el empleado al aceptar
                .nombreUsuario(request.nombreUsuario().trim())
                .apellidoUsuario(request.apellidoUsuario().trim())
                .tipoUsuario(TipoUsuario.CLIENTE)
                // Snapshot no-nulo (columnas NOT NULL); el plan EFECTIVO se hereda del dueño.
                .planSuscripcion(dueno.getPlanSuscripcion())
                .maxGranjas(dueno.getMaxGranjas())
                .activo(true)
                .emailVerificado(false)
                .fechaRegistro(Instant.now())
                .esUsuarioEmpleado(true)
                .usuarioDueno(dueno)
                .rolEmpleado(request.rol())
                .activoComoEmpleado(false)
                .build();
        usuarioRepository.saveAndFlush(empleado);

        auditar(actor.getId(), empleado.getId(), AccionAuditoria.CREAR_EMPLEADO, "Invitación de empleado",
                Map.of("email", empleado.getEmail(), "rolEmpleado", empleado.getRolEmpleado().name()));

        var token = tokenSeguridadService.emitir(empleado, TipoToken.INVITACION_EMPLEADO, VIGENCIA_INVITACION);
        emailNotificacionService.enviarInvitacionEmpleado(dueno, empleado, token);

        return EmpleadoResponse.from(empleado);
    }

    // ---------------------------------------------------------- aceptar

    /** Consume el token de invitación, fija la contraseña y activa al empleado. */
    @Transactional
    public EmpleadoResponse aceptarInvitacion(AceptarInvitacionRequest request) {
        var empleado = tokenSeguridadService.validarYConsumir(
                request.token(), TipoToken.INVITACION_EMPLEADO);
        empleado.setPasswordHash(passwordEncoder.encode(request.password()));
        empleado.setEmailVerificado(true);
        empleado.setActivoComoEmpleado(true);
        empleado.setFechaVinculacion(Instant.now());

        auditar(empleado.getId(), empleado.getId(), AccionAuditoria.ACEPTAR_INVITACION,
                "Aceptación de invitación de empleado", null);

        return EmpleadoResponse.from(empleado);
    }

    // ---------------------------------------------------------- gestión (Etapa 4)

    /** Lista los empleados del dueño efectivo del actor (incluye pendientes y desactivados). */
    @Transactional(readOnly = true)
    public List<EmpleadoResponse> listar(String idActor) {
        var dueno = resolverDueno(cargar(idActor));
        return usuarioRepository.findByUsuarioDuenoIdOrderByFechaVinculacionDesc(dueno.getId()).stream()
                .filter(u -> Boolean.TRUE.equals(u.getEsUsuarioEmpleado()))
                .map(EmpleadoResponse::from)
                .toList();
    }

    /** Cambia el rol de un empleado del tenant, aplicando la matriz de jerarquía. */
    @Transactional
    public EmpleadoResponse cambiarRol(String idActor, String idEmpleado, RolEmpleado nuevoRol) {
        var actor = cargar(idActor);
        var dueno = resolverDueno(actor);
        var empleado = cargarEmpleadoDelDueno(idEmpleado, dueno.getId());
        autorizarAccion(actor, empleado, nuevoRol);

        var rolAnterior = empleado.getRolEmpleado();
        empleado.setRolEmpleado(nuevoRol);
        // El token en vuelo lleva el rol anterior en sus authorities; revocarlo fuerza re-login.
        empleado.revocarSesiones();
        tokenVersionCache.invalidar(empleado.getId());
        auditar(actor.getId(), empleado.getId(), AccionAuditoria.CAMBIO_ROL_EMPLEADO, "Cambio de rol de empleado",
                Map.of("rolAnterior", String.valueOf(rolAnterior), "rolNuevo", nuevoRol.name()));
        return EmpleadoResponse.from(empleado);
    }

    /**
     * Activa o desactiva (baja lógica, ADR-0005) a un empleado del tenant. Desactivar corta el acceso
     * (el login ya lo rechaza); la revocación de tokens en vuelo es parte del endurecimiento de sesión.
     */
    @Transactional
    public EmpleadoResponse cambiarEstado(String idActor, String idEmpleado, boolean activo) {
        var actor = cargar(idActor);
        var dueno = resolverDueno(actor);
        var empleado = cargarEmpleadoDelDueno(idEmpleado, dueno.getId());
        autorizarAccion(actor, empleado, null);

        // Reactivar consume cupo igual que un alta (RD-P6.b.2): validar contra el límite operativo.
        if (activo && !Boolean.TRUE.equals(empleado.getActivoComoEmpleado())) {
            validarCupoEmpleados(dueno.getId());
        }
        empleado.setActivoComoEmpleado(activo);
        // Al desactivar, cortar de inmediato las sesiones en vuelo (el login ya lo rechaza).
        if (!activo) {
            empleado.revocarSesiones();
            tokenVersionCache.invalidar(empleado.getId());
        }
        auditar(actor.getId(), empleado.getId(), AccionAuditoria.DESACTIVAR_EMPLEADO,
                activo ? "Reactivación de empleado" : "Desactivación de empleado",
                Map.of("activoComoEmpleado", activo));
        return EmpleadoResponse.from(empleado);
    }

    // ---------------------------------------------------------- helpers

    private Usuario cargar(String id) {
        return usuarioRepository.findById(id).orElseThrow();
    }

    /** Dueño efectivo del actor: él mismo si es dueño, o su dueño si es jefe ADMIN activo. */
    private Usuario resolverDueno(Usuario actor) {
        if (!Boolean.TRUE.equals(actor.getEsUsuarioEmpleado())) {
            return actor;
        }
        if (esJefe(actor) && Boolean.TRUE.equals(actor.getActivoComoEmpleado())
                && actor.getUsuarioDueno() != null) {
            return actor.getUsuarioDueno();
        }
        throw prohibido("No tenés permisos para gestionar empleados");
    }

    private Usuario cargarEmpleadoDelDueno(String idEmpleado, String idDueno) {
        var empleado = usuarioRepository.findById(idEmpleado).orElseThrow(
                () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Empleado no encontrado"));
        if (!Boolean.TRUE.equals(empleado.getEsUsuarioEmpleado())
                || empleado.getUsuarioDueno() == null
                || !idDueno.equals(empleado.getUsuarioDueno().getId())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Empleado no encontrado");
        }
        return empleado;
    }

    /**
     * Matriz de jerarquía para modificar un empleado. {@code nuevoRol} es null cuando la acción no
     * cambia el rol (p. ej. activar/desactivar).
     */
    private void autorizarAccion(Usuario actor, Usuario empleado, RolEmpleado nuevoRol) {
        if (actor.getId().equals(empleado.getId())) {
            throw prohibido("No podés modificar tu propia cuenta");
        }
        if (esJefe(actor)) {
            if (empleado.getRolEmpleado() == RolEmpleado.ADMIN) {
                throw prohibido("Un jefe no puede modificar a otro administrador");
            }
            if (nuevoRol == RolEmpleado.ADMIN) {
                throw prohibido("Solo el dueño puede designar administradores");
            }
        }
        // OWNER: sin restricciones adicionales (no figura en la lista de empleados de su propio tenant).
    }

    /**
     * Plan-gating del equipo contra el límite OPERATIVO (RD-P6.b.2): mientras haya un downgrade
     * programado rige el menor entre el plan actual y el pendiente, para que la precondición de
     * empleados no pueda romperse entre programar el cambio y que el job lo aplique.
     */
    private void validarCupoEmpleados(String idDueno) {
        var limite = suscripcionService.limiteEmpleadosOperativo(idDueno);
        var actuales = usuarioRepository.countByUsuarioDuenoIdAndActivoComoEmpleadoTrue(idDueno);
        if (actuales >= limite.limite()) {
            throw prohibido(limite.porCambioProgramado()
                    ? "Límite de empleados alcanzado: tenés un cambio a " + limite.planQueLimita().name()
                            + " programado (límite: " + limite.limite() + ")"
                    : "Límite de empleados alcanzado para el plan " + limite.planQueLimita().name());
        }
    }

    private static boolean esJefe(Usuario u) {
        return Boolean.TRUE.equals(u.getEsUsuarioEmpleado()) && u.getRolEmpleado() == RolEmpleado.ADMIN;
    }

    private static ResponseStatusException prohibido(String mensaje) {
        return new ResponseStatusException(HttpStatus.FORBIDDEN, mensaje);
    }

    private void auditar(String idActor, String idEmpleado, AccionAuditoria accion, String descripcion,
            Object datosNuevos) {
        auditoriaService.registrar(AuditoriaEvento.builder()
                .idUsuario(idActor)
                .tablaOrigen(TABLA_USUARIOS)
                .idRegistro(idEmpleado)
                .accion(accion)
                .descripcion(descripcion)
                .datosNuevos(datosNuevos)
                .build());
    }
}
