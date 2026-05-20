package com.reforma.domain.auth.jwt;

import com.reforma.domain.config.ReformaProperties;
import com.reforma.domain.usuarios.entity.Usuario;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Service;

@Service
public class TokenJwtServicio {

    private final SecretKey key;
    private final int expirationHours;

    public TokenJwtServicio(ReformaProperties properties) {
        this.key = Keys.hmacShaKeyFor(
                properties.jwt().secret().getBytes(StandardCharsets.UTF_8));
        this.expirationHours = properties.jwt().expirationHours();
    }

    public String generarToken(Usuario usuario) {
        var now = Instant.now();
        return Jwts.builder()
                .subject(usuario.getId())
                .claim("email", usuario.getEmail())
                .claim("tipoUsuario", usuario.getTipoUsuario().name())
                .claim("planSuscripcion", usuario.getPlanSuscripcion().name())
                .claim("emailVerificado", usuario.getEmailVerificado())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(expirationHours, ChronoUnit.HOURS)))
                .signWith(key)
                .compact();
    }

    public JwtUserPrincipal validarToken(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
        return new JwtUserPrincipal(
                claims.getSubject(),
                claims.get("email", String.class),
                com.reforma.domain.common.domain.TipoUsuario.valueOf(
                        claims.get("tipoUsuario", String.class)),
                com.reforma.domain.common.domain.PlanSuscripcion.valueOf(
                        claims.get("planSuscripcion", String.class)),
                Boolean.TRUE.equals(claims.get("emailVerificado", Boolean.class)));
    }
}
