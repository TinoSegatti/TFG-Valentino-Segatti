package com.reforma.domain.auth.jwt;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.reforma.domain.usuarios.repository.UsuarioRepository;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Cache in-memory del {@code t_usuarios.token_version} que consulta {@link TokenJwtServicio}
 * en CADA request autenticada (revocación de sesiones). Sin cache, esa verificación cuesta
 * una query a la BD por request y es el principal consumidor del pool bajo tráfico sostenido.
 *
 * <p>Consistencia: los puntos que revocan sesiones ({@code EmpleadoService},
 * {@code RecuperacionCuentaService}) invalidan la entrada, con lo que la revocación sigue
 * siendo inmediata en este nodo. El TTL corto acota el peor caso (p. ej. una carrera entre
 * la invalidación y una lectura concurrente que re-cachea el valor viejo, o un segundo nodo
 * en un despliegue multi-instancia futuro — escenario en el que este cache debería migrar a
 * Redis, ya presente en el stack).
 */
@Component
public class TokenVersionCache {

    /** Centinela para "usuario inexistente": jamás coincide con un token_version real (>= 0). */
    private static final int USUARIO_INEXISTENTE = -1;

    private final UsuarioRepository usuarioRepository;
    private final Cache<String, Integer> cache;

    public TokenVersionCache(
            UsuarioRepository usuarioRepository,
            @Value("${reforma.auth.token-version-cache-segundos:60}") long ttlSegundos) {
        this.usuarioRepository = usuarioRepository;
        this.cache = Caffeine.newBuilder()
                .maximumSize(10_000)
                .expireAfterWrite(Duration.ofSeconds(ttlSegundos))
                .build();
    }

    /** Versión vigente del usuario, o {@code -1} si la cuenta no existe (se cachea igual). */
    public int obtener(String idUsuario) {
        return cache.get(idUsuario,
                id -> usuarioRepository.findTokenVersionById(id).orElse(USUARIO_INEXISTENTE));
    }

    /** Llamar tras {@code usuario.revocarSesiones()} para que el corte sea inmediato. */
    public void invalidar(String idUsuario) {
        cache.invalidate(idUsuario);
    }
}
