package com.reforma.domain.common.util;

import java.security.SecureRandom;
import java.time.Instant;

/**
 * Generador de IDs tipo CUID simplificado (32 chars) para entidades que aún usan VARCHAR(32)
 * (Usuario, Granja). Los catálogos (materia prima, proveedor, animal) usan {@code Long}
 * con {@code GenerationType.IDENTITY} — ver ADR 0006.
 */
public final class IdGenerator {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String ALPHABET = "0123456789abcdefghijklmnopqrstuvwxyz";

    private IdGenerator() {}

    public static String newId() {
        var epoch = Long.toString(Instant.now().toEpochMilli(), 36);
        var sb = new StringBuilder(epoch);
        while (sb.length() < 24) {
            sb.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
        }
        return sb.substring(0, Math.min(32, sb.length()));
    }
}
