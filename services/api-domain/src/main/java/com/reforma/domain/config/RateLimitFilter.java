package com.reforma.domain.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.reforma.domain.common.api.ApiError;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Rate limiting por IP sobre los POST sensibles (ventana fija de 1 minuto, en memoria).
 *
 * <p>Protege los endpoints anónimos o costosos contra ráfagas: fuerza bruta de login,
 * registro masivo, spam de emails (reset/verificación, que disparan SMTP) y creación
 * repetida de checkouts contra Mercado Pago. El resto del tráfico no pasa por el contador.
 *
 * <p>Ventana fija con Caffeine ({@code expireAfterWrite} 1 min): simple y suficiente para
 * una instancia única. Si la app escala a varias instancias, migrar el contador a Redis.
 * Se puede desactivar con {@code RATELIMIT_HABILITADO=false} (p. ej. para pruebas de carga).
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class RateLimitFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RateLimitFilter.class);

    /** Límite de requests POST por IP por minuto, por endpoint. */
    private static final Map<String, Integer> LIMITES_POR_PATH = Map.of(
            "/api/usuarios/login", 10,
            "/api/usuarios/registro", 5,
            "/api/usuarios/solicitar-reset", 3,
            "/api/usuarios/reenviar-verificacion", 3,
            "/api/suscripcion/checkout", 10);

    private final boolean habilitado;
    private final ObjectMapper objectMapper;
    private final Cache<String, AtomicInteger> contadores = Caffeine.newBuilder()
            .maximumSize(50_000)
            .expireAfterWrite(Duration.ofMinutes(1))
            .build();

    public RateLimitFilter(
            @Value("${reforma.ratelimit.habilitado:true}") boolean habilitado,
            ObjectMapper objectMapper) {
        this.habilitado = habilitado;
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !habilitado
                || !"POST".equalsIgnoreCase(request.getMethod())
                || !LIMITES_POR_PATH.containsKey(request.getRequestURI());
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        var path = request.getRequestURI();
        var clave = ipCliente(request) + "|" + path;
        var contador = contadores.get(clave, k -> new AtomicInteger());
        if (contador.incrementAndGet() > LIMITES_POR_PATH.get(path)) {
            log.warn("Rate limit excedido en {} (ip {})", path, ipCliente(request));
            responder429(response, path);
            return;
        }
        filterChain.doFilter(request, response);
    }

    private void responder429(HttpServletResponse response, String path) throws IOException {
        response.setStatus(429);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        var body = ApiError.of(
                429,
                "Too Many Requests",
                "Demasiados intentos. Espere un minuto y vuelva a intentarlo.",
                path);
        objectMapper.writeValue(response.getWriter(), body);
    }

    /**
     * Detrás de un proxy (nginx/ngrok) la IP real viene en X-Forwarded-For; el primer valor
     * de la lista es el cliente original. Sin proxy, remoteAddr.
     */
    private String ipCliente(HttpServletRequest request) {
        var forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
