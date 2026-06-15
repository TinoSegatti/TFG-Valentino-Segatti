-- REFORMA — V010: tabla genérica de tokens de seguridad (Etapa 1 módulo Usuarios)
-- Un solo mecanismo para verificación de email, recuperación de contraseña e invitación
-- de empleados (Etapa 3). Se guarda SOLO el hash SHA-256 del token; el token en claro
-- viaja únicamente por email/URL. Tokens de un solo uso (consumido_en) y con expiración.

CREATE TABLE t_token_seguridad (
    id               VARCHAR(32) PRIMARY KEY,
    id_usuario       VARCHAR(32) NOT NULL REFERENCES t_usuarios(id) ON DELETE CASCADE,
    tipo             VARCHAR(30) NOT NULL,   -- VERIFICACION_EMAIL | RESET_PASSWORD | INVITACION_EMPLEADO
    token_hash       VARCHAR(64) NOT NULL UNIQUE,  -- SHA-256 en hexadecimal
    fecha_creacion   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    fecha_expiracion TIMESTAMPTZ NOT NULL,
    consumido_en     TIMESTAMPTZ
);

CREATE INDEX idx_token_seguridad_usuario_tipo ON t_token_seguridad(id_usuario, tipo);
