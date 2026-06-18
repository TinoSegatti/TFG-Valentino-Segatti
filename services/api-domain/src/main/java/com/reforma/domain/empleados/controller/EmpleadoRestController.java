package com.reforma.domain.empleados.controller;

import com.reforma.domain.auth.SecurityUtils;
import com.reforma.domain.empleados.dto.AceptarInvitacionRequest;
import com.reforma.domain.empleados.dto.CambiarEstadoEmpleadoRequest;
import com.reforma.domain.empleados.dto.CambiarRolEmpleadoRequest;
import com.reforma.domain.empleados.dto.EmpleadoResponse;
import com.reforma.domain.empleados.dto.InvitarEmpleadoRequest;
import com.reforma.domain.empleados.service.EmpleadoService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Gestión de empleados (Etapas 2–4). El dueño (OWNER) y el jefe (ADMIN) gestionan el equipo; las
 * reglas finas de jerarquía (un jefe no toca a otro jefe ni designa jefes) se aplican en el servicio.
 */
@RestController
@RequestMapping("/api/empleados")
@RequiredArgsConstructor
public class EmpleadoRestController {

    private final EmpleadoService empleadoService;

    /** Invitar: dueño o jefe ADMIN (el ADMIN no puede invitar otro ADMIN — validado en el servicio). */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('OWNER','ADMIN')")
    public EmpleadoResponse invitar(@Valid @RequestBody InvitarEmpleadoRequest request) {
        return empleadoService.invitar(SecurityUtils.requireUserId(), request);
    }

    /** Listar el equipo del tenant (dueño o jefe ADMIN). */
    @GetMapping
    @PreAuthorize("hasAnyRole('OWNER','ADMIN')")
    public List<EmpleadoResponse> listar() {
        return empleadoService.listar(SecurityUtils.requireUserId());
    }

    /** Cambiar el rol de un empleado. */
    @PutMapping("/{idEmpleado}/rol")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN')")
    public EmpleadoResponse cambiarRol(
            @PathVariable String idEmpleado, @Valid @RequestBody CambiarRolEmpleadoRequest request) {
        return empleadoService.cambiarRol(SecurityUtils.requireUserId(), idEmpleado, request.rol());
    }

    /** Activar/desactivar (baja lógica) un empleado. */
    @PutMapping("/{idEmpleado}/activo")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN')")
    public EmpleadoResponse cambiarEstado(
            @PathVariable String idEmpleado, @Valid @RequestBody CambiarEstadoEmpleadoRequest request) {
        return empleadoService.cambiarEstado(
                SecurityUtils.requireUserId(), idEmpleado, request.activo());
    }

    /** Público: el empleado aún no tiene credenciales; se autentica con el token del enlace. */
    @PostMapping("/aceptar")
    public EmpleadoResponse aceptar(@Valid @RequestBody AceptarInvitacionRequest request) {
        return empleadoService.aceptarInvitacion(request);
    }
}
