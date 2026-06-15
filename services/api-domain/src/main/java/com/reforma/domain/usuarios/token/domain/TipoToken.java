package com.reforma.domain.usuarios.token.domain;

/**
 * Tipo de token de seguridad ({@code t_token_seguridad.tipo}, VARCHAR(30)).
 * {@code INVITACION_EMPLEADO} queda reservado para la Etapa 3 (ver {@code docs/MODULO_USUARIOS.md}).
 */
public enum TipoToken {
    VERIFICACION_EMAIL,
    RESET_PASSWORD,
    INVITACION_EMPLEADO
}
