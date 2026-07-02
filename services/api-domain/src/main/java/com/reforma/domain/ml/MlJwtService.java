package com.reforma.domain.ml;

import com.reforma.domain.config.ReformaProperties;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Service;

/**
 * Firma JWT servicio-a-servicio para llamar a api-ml (ADR-0004).
 *
 * <p>Claims fijos {@code iss=api-domain}, {@code aud=api-ml}, firmados con {@code reforma.ml.secret}
 * (HS256). Vigencia corta: el token solo vive durante la llamada saliente.
 */
@Service
public class MlJwtService {

    static final String ISSUER = "api-domain";
    static final String AUDIENCE = "api-ml";
    private static final long VIGENCIA_SEGUNDOS = 60;

    private final SecretKey key;

    public MlJwtService(ReformaProperties properties) {
        this.key = Keys.hmacShaKeyFor(properties.ml().secret().getBytes(StandardCharsets.UTF_8));
    }

    /** Genera un token s2s de un solo uso para autenticarse ante api-ml. */
    public String generarToken() {
        Instant now = Instant.now();
        return Jwts.builder()
                .issuer(ISSUER)
                .audience()
                .add(AUDIENCE)
                .and()
                .subject(ISSUER)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(VIGENCIA_SEGUNDOS, ChronoUnit.SECONDS)))
                // HS256 explícito: sin el algoritmo, jjwt lo autoselecciona según el tamaño de la
                // clave (una clave de 384+ bits deriva en HS384/HS512), y api-ml solo acepta HS256.
                .signWith(key, Jwts.SIG.HS256)
                .compact();
    }
}
