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
}
