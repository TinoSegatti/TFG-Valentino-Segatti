package com.reforma.domain.usuarios.email;

import com.reforma.domain.usuarios.entity.Usuario;

/**
 * Envío de correos transaccionales del módulo de usuarios. La implementación activa depende
 * de la configuración (ver {@code LogEmailNotificacionService} para dev/test). Recibe el token
 * EN CLARO para construir el enlace; nunca debe persistirlo ni registrarlo en auditoría.
 */
public interface EmailNotificacionService {

    void enviarVerificacionEmail(Usuario usuario, String tokenPlano);

    void enviarResetPassword(Usuario usuario, String tokenPlano);
}
