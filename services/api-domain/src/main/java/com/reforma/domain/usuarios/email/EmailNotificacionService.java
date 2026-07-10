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

    /**
     * Invitación a un empleado recién creado en estado pendiente. {@code dueno} aporta el
     * contexto (quién invita) y {@code empleado} el destinatario; {@code tokenPlano} es el
     * token de un solo uso (tipo {@code INVITACION_EMPLEADO}) que viaja en el enlace.
     */
    void enviarInvitacionEmpleado(Usuario dueno, Usuario empleado, String tokenPlano);

    /**
     * Aviso del ciclo de vida de la suscripción (módulo Pagos): renovación fallida,
     * downgrade pospuesto, caída a DEMO, etc. Texto plano ya armado por el llamador;
     * nunca debe incluir datos de tarjetas ni tokens de la pasarela.
     */
    void enviarAvisoSuscripcion(Usuario dueno, String asunto, String cuerpo);
}
