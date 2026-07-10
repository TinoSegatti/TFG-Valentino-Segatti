package com.reforma.domain.suscripciones.dto;

import java.util.List;

/**
 * Página del historial de pagos. DTO propio (no se serializa {@code Page} de Spring Data,
 * cuyo contrato JSON es inestable entre versiones) — mismo criterio que {@code PaginaAuditoria}.
 */
public record PaginaPagos(
        List<PagoResponse> contenido,
        int pagina,
        int tamano,
        long totalElementos,
        int totalPaginas) {}
