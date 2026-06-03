package com.reforma.domain.testsupport;

import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiConsumer;
import org.mockito.stubbing.Answer;

/**
 * Simula en tests unitarios el comportamiento de {@code GenerationType.IDENTITY}:
 * tras {@code save()}, la entidad recibe un {@code Long id} asignado.
 */
public final class EntidadConIdMocks {

    private static final AtomicLong SECUENCIA = new AtomicLong(1L);

    private EntidadConIdMocks() {}

    public static <T> Answer<T> asignarIdAlGuardar(Class<T> type, BiConsumer<T, Long> asignarId) {
        return inv -> {
            T entity = type.cast(inv.getArgument(0));
            asignarId.accept(entity, siguienteId());
            return entity;
        };
    }

    public static long siguienteId() {
        return SECUENCIA.getAndIncrement();
    }

    public static void reiniciarSecuencia() {
        SECUENCIA.set(1L);
    }
}
