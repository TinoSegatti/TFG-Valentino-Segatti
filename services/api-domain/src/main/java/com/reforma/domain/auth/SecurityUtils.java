package com.reforma.domain.auth;

import com.reforma.domain.auth.jwt.JwtUserPrincipal;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

public final class SecurityUtils {

    private SecurityUtils() {}

    public static JwtUserPrincipal requirePrincipal() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof JwtUserPrincipal principal)) {
            throw new IllegalStateException("Usuario no autenticado");
        }
        return principal;
    }

    public static String requireUserId() {
        return requirePrincipal().id();
    }

    /**
     * Tenant efectivo sobre el que opera el usuario autenticado: el id del dueño para un
     * empleado vinculado, o su propio id para un dueño. Los recursos granja-scoped viven bajo
     * el dueño, por lo que los controllers deben scopear con este id, no con {@link #requireUserId()}.
     */
    public static String requireTenantId() {
        return requirePrincipal().tenantId();
    }
}
