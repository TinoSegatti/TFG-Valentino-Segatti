package com.reforma.domain.config;

import com.reforma.domain.auth.jwt.JwtAuthenticationFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private static final String[] PUBLIC = {
        "/api/health",
        "/api/usuarios/registro",
        "/api/usuarios/login",
        "/api/usuarios/google",
        "/api/usuarios/google/verify",
        "/api/usuarios/verificar-email",
        "/api/usuarios/reenviar-verificacion",
        "/api/usuarios/solicitar-reset",
        "/api/usuarios/confirmar-reset",
        "/api/empleados/aceptar",
        "/api/suscripcion/planes",
        "/v3/api-docs/**",
        "/swagger-ui/**",
        "/swagger-ui.html",
        "/actuator/health"
    };

    /**
     * Recursos granja-scoped: toda mutación (POST/PUT/PATCH/DELETE) queda vedada al rol LECTOR
     * (solo lectura). Los GET — incluida la exportación CSV — siguen abiertos a cualquier rol
     * autenticado con acceso al tenant. La importación CSV es POST, por lo que también se bloquea.
     */
    private static final String[] GRANJA_SCOPED = {
        "/api/granjas/**",
        "/api/materias-primas/**",
        "/api/proveedores/**",
        "/api/animales/**",
        "/api/compras/**",
        "/api/formulas/**",
        "/api/fabricaciones/**",
        "/api/inventario/**",
        "/api/archivos/**",
        "/api/reportes/**"
    };

    private static final String[] ROLES_ESCRITURA = {"OWNER", "ADMIN", "EDITOR"};

    @Bean
    SecurityFilterChain securityFilterChain(
            HttpSecurity http, JwtAuthenticationFilter jwtAuthenticationFilter) throws Exception {
        return http.csrf(csrf -> csrf.disable())
                .cors(cors -> {})
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(PUBLIC).permitAll()
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        // Crear granjas/plantas es exclusivo del dueño (gateado por límite de plan);
                        // los empleados las ven y operan, pero no pueden crearlas. Regla más específica
                        // que la de GRANJA_SCOPED, por eso va primero.
                        .requestMatchers(HttpMethod.POST, "/api/granjas").hasRole("OWNER")
                        .requestMatchers(HttpMethod.POST, GRANJA_SCOPED).hasAnyRole(ROLES_ESCRITURA)
                        .requestMatchers(HttpMethod.PUT, GRANJA_SCOPED).hasAnyRole(ROLES_ESCRITURA)
                        .requestMatchers(HttpMethod.PATCH, GRANJA_SCOPED).hasAnyRole(ROLES_ESCRITURA)
                        .requestMatchers(HttpMethod.DELETE, GRANJA_SCOPED).hasAnyRole(ROLES_ESCRITURA)
                        .anyRequest().authenticated())
                .exceptionHandling(ex -> ex.authenticationEntryPoint(
                        (request, response, authException) ->
                                response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "No autenticado"))
                        .accessDeniedHandler((request, response, accessDeniedException) ->
                                response.sendError(HttpServletResponse.SC_FORBIDDEN, "Acceso denegado")))
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .build();
    }
}
