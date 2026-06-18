package com.reforma.domain.auth.jwt;

import com.reforma.domain.common.domain.PlanSuscripcion;
import com.reforma.domain.common.domain.RolEmpleado;
import com.reforma.domain.common.domain.TipoUsuario;
import java.util.Collection;
import java.util.List;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

/**
 * Identidad autenticada extraída del JWT. Para empleados transporta el rol y el id del dueño
 * ({@code idDueno}), que es el <em>tenant efectivo</em> sobre el que operan (sus recursos viven
 * bajo el dueño). Authorities: {@code ROLE_OWNER} para el dueño; {@code ROLE_ADMIN/EDITOR/LECTOR}
 * según {@code rolEmpleado} para los empleados.
 */
public record JwtUserPrincipal(
        String id,
        String email,
        TipoUsuario tipoUsuario,
        PlanSuscripcion planSuscripcion,
        boolean emailVerificado,
        boolean esEmpleado,
        RolEmpleado rolEmpleado,
        String idDueno) implements UserDetails {

    /** Tenant efectivo: el dueño para un empleado, o el propio usuario si es dueño. */
    public String tenantId() {
        return esEmpleado && idDueno != null ? idDueno : id;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        var rol = esEmpleado && rolEmpleado != null ? rolEmpleado.name() : "OWNER";
        return List.of(new SimpleGrantedAuthority("ROLE_" + rol));
    }

    @Override
    public String getPassword() {
        return "";
    }

    @Override
    public String getUsername() {
        return email;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return emailVerificado;
    }
}
