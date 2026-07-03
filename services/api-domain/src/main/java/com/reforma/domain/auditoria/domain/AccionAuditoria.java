package com.reforma.domain.auditoria.domain;

/**
 * Acciones auditables registradas en {@code t_auditoria.accion} (VARCHAR(30)).
 * Los nombres no deben superar 30 caracteres. Se incluyen las acciones del módulo
 * de usuarios (Etapa 0–4 de {@code docs/MODULO_USUARIOS.md}); las que aún no tienen
 * flujo implementado quedan reservadas para no versionar el enum más adelante.
 */
public enum AccionAuditoria {
    // Etapa 0 — autenticación
    REGISTRO,
    LOGIN,
    LOGIN_FALLIDO,
    // Etapa 1 — ciclo de vida del dueño (reservadas)
    VERIFICACION_EMAIL,
    REENVIO_VERIFICACION,
    SOLICITUD_RESET,
    RESET_PASSWORD,
    // Etapa 3–4 — empleados (reservadas)
    CREAR_EMPLEADO,
    ACEPTAR_INVITACION,
    CAMBIO_ROL_EMPLEADO,
    DESACTIVAR_EMPLEADO,
    // Módulo Archivos — snapshot inmutable creado (t_archivo, V013)
    ARCHIVO_CREADO,
    // Módulo Reportes — informe de estado generado (RF-REP)
    INFORME_GENERADO
}
