package com.reforma.domain.auth.jwt;

import com.reforma.domain.config.ReformaProperties;
import com.reforma.domain.usuarios.entity.Usuario;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
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
    private final TokenVersionCache tokenVersionCache;

    public TokenJwtServicio(ReformaProperties properties, TokenVersionCache tokenVersionCache) {
        this.key = Keys.hmacShaKeyFor(
                properties.jwt().secret().getBytes(StandardCharsets.UTF_8));
        this.expirationHours = properties.jwt().expirationHours();
        this.tokenVersionCache = tokenVersionCache;
    }

    public String generarToken(Usuario usuario) {
        var now = Instant.now();
        var esEmpleado = Boolean.TRUE.equals(usuario.getEsUsuarioEmpleado());
        var builder = Jwts.builder()
                .subject(usuario.getId())
                .claim("email", usuario.getEmail())
                .claim("tipoUsuario", usuario.getTipoUsuario().name())
                .claim("planSuscripcion", usuario.getPlanSuscripcion().name())
                .claim("emailVerificado", usuario.getEmailVerificado())
                .claim("esEmpleado", esEmpleado)
                .claim("tv", usuario.getTokenVersion());
        if (esEmpleado) {
            if (usuario.getRolEmpleado() != null) {
                builder.claim("rolEmpleado", usuario.getRolEmpleado().name());
            }
            if (usuario.getUsuarioDueno() != null) {
                builder.claim("idDueno", usuario.getUsuarioDueno().getId());
            }
        }
        return builder
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
        // Revocación de sesión: el "tv" del token debe coincidir con la versión vigente
        // (leída vía TokenVersionCache para no pagar una query a la BD por request).
        // Tokens viejos sin claim "tv" se asumen versión 0 (igual al default de la columna).
        var tvClaim = claims.get("tv", Integer.class);
        var tvToken = tvClaim == null ? 0 : tvClaim;
        var tvActual = tokenVersionCache.obtener(claims.getSubject());
        if (tvActual != tvToken) {
            throw new JwtException("Sesión revocada");
        }
        var rolEmpleadoClaim = claims.get("rolEmpleado", String.class);
        return new JwtUserPrincipal(
                claims.getSubject(),
                claims.get("email", String.class),
                com.reforma.domain.common.domain.TipoUsuario.valueOf(
                        claims.get("tipoUsuario", String.class)),
                com.reforma.domain.common.domain.PlanSuscripcion.valueOf(
                        claims.get("planSuscripcion", String.class)),
                Boolean.TRUE.equals(claims.get("emailVerificado", Boolean.class)),
                Boolean.TRUE.equals(claims.get("esEmpleado", Boolean.class)),
                rolEmpleadoClaim == null
                        ? null
                        : com.reforma.domain.common.domain.RolEmpleado.valueOf(rolEmpleadoClaim),
                claims.get("idDueno", String.class));
    }
}
