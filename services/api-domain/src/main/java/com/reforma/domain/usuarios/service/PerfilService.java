package com.reforma.domain.usuarios.service;

import com.reforma.domain.usuarios.dto.PerfilResponse;
import com.reforma.domain.usuarios.entity.Usuario;
import com.reforma.domain.usuarios.repository.UsuarioRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** Arma el perfil del usuario autenticado (identidad + rol efectivo + permisos legibles). */
@Service
@RequiredArgsConstructor
public class PerfilService {

    private final UsuarioRepository usuarioRepository;

    @Transactional(readOnly = true)
    public PerfilResponse obtenerPerfil(String idUsuario) {
        Usuario u = usuarioRepository
                .findById(idUsuario)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Usuario no encontrado"));
        boolean empleado = Boolean.TRUE.equals(u.getEsUsuarioEmpleado());
        String rol = empleado && u.getRolEmpleado() != null ? u.getRolEmpleado().name() : "OWNER";
        String idDueno = u.getUsuarioDueno() != null ? u.getUsuarioDueno().getId() : null;
        return new PerfilResponse(
                u.getId(),
                u.getEmail(),
                u.getNombreUsuario(),
                u.getApellidoUsuario(),
                rol,
                empleado,
                u.getPlanSuscripcion(),
                idDueno,
                permisosDe(rol));
    }

    /** Capacidades legibles por rol, alineadas con la matriz de SecurityConfig/EmpleadoService. */
    private List<String> permisosDe(String rol) {
        return switch (rol) {
            case "OWNER" -> List.of(
                    "Ver todos los datos",
                    "Crear y editar datos",
                    "Crear granjas",
                    "Gestionar equipo (invitar, cambiar rol, dar de baja)",
                    "Designar administradores",
                    "Ver auditoría",
                    "Gestionar plan y facturación");
            case "ADMIN" -> List.of(
                    "Ver todos los datos",
                    "Crear y editar datos",
                    "Gestionar equipo (sin designar administradores)",
                    "Ver auditoría");
            case "EDITOR" -> List.of(
                    "Ver todos los datos",
                    "Crear y editar datos");
            case "LECTOR" -> List.of(
                    "Ver todos los datos (solo lectura)");
            default -> List.of();
        };
    }
}
