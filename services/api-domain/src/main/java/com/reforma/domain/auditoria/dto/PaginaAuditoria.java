package com.reforma.domain.auditoria.dto;

import java.util.List;

/**
 * Página de resultados de auditoría. DTO propio (no se serializa {@code org.springframework.data.domain.Page}
 * directamente, cuyo contrato JSON es inestable entre versiones de Spring Data).
 */
public record PaginaAuditoria(
        List<AuditoriaResponse> contenido,
        int pagina,
        int tamano,
        long totalElementos,
        int totalPaginas) {}
