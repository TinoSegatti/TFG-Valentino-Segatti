-- REFORMA — V011: versión de token para revocación de sesiones (endurecimiento, Etapa 5)
-- Al desactivar un empleado, cambiarle el rol o restablecer la contraseña se incrementa
-- token_version. El JWT lleva el valor vigente como claim "tv"; el servicio de tokens rechaza
-- (401) cualquier JWT cuyo "tv" no coincida con el de la BD → las sesiones en vuelo quedan
-- revocadas sin esperar a que expire el token. Default 0 = no rompe sesiones existentes al desplegar.

ALTER TABLE t_usuarios
    ADD COLUMN token_version INTEGER NOT NULL DEFAULT 0;
