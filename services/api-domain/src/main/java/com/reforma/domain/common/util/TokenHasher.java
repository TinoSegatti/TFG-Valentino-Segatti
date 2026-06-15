package com.reforma.domain.common.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/**
 * Generación y hash de tokens de seguridad. El token en claro (aleatorio, 256 bits) viaja
 * solo por email/URL; en base de datos se guarda únicamente su hash SHA-256 (hex). Así, una
 * filtración de la tabla no expone tokens utilizables.
 */
public final class TokenHasher {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Base64.Encoder URL_ENCODER = Base64.getUrlEncoder().withoutPadding();

    private TokenHasher() {}

    /** Token aleatorio URL-safe de 256 bits (~43 caracteres). */
    public static String generarTokenPlano() {
        var bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return URL_ENCODER.encodeToString(bytes);
    }

    /** SHA-256 del token en hexadecimal (64 caracteres). */
    public static String hash(String tokenPlano) {
        try {
            var digest = MessageDigest.getInstance("SHA-256");
            var hashed = digest.digest(tokenPlano.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashed);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 no disponible", e);
        }
    }
}
