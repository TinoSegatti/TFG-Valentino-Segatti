package com.reforma.domain.ml;

import static org.assertj.core.api.Assertions.assertThat;

import com.reforma.domain.config.ReformaProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import javax.crypto.SecretKey;
import org.junit.jupiter.api.Test;

class MlJwtServiceTest {

    private static final String SECRET =
            "dev_ml_jwt_secret_minimo_64_caracteres_compartido_domain_ml_xxxxx";

    private final MlJwtService service = new MlJwtService(
            new ReformaProperties(
                    new ReformaProperties.Jwt("irrelevante_pero_largo_para_hs256_000000000000000", 24),
                    new ReformaProperties.Ml("http://api-ml:8081", SECRET),
                    "http://localhost:4200"));

    @Test
    void generarToken_llevaIssuerYAudienceCorrectos() {
        String token = service.generarToken();

        SecretKey key = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
        Claims claims = Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();

        assertThat(claims.getIssuer()).isEqualTo("api-domain");
        assertThat(claims.getAudience()).contains("api-ml");
        assertThat(claims.getExpiration()).isAfter(claims.getIssuedAt());
    }
}
