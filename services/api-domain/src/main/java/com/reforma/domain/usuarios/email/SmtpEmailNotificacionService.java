package com.reforma.domain.usuarios.email;

import com.reforma.domain.config.ReformaProperties;
import com.reforma.domain.usuarios.entity.Usuario;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

/**
 * Envío de correos transaccionales por SMTP (RF-AUTH). Se activa con
 * {@code reforma.email.mode=smtp}; en ese caso hay que configurar {@code spring.mail.*}
 * (host/usuario/contraseña). Por defecto el sistema usa {@link LogEmailNotificacionService}.
 *
 * <p>Recibe el token EN CLARO solo para armar el enlace; nunca lo persiste ni lo audita.
 */
@Service
@Slf4j
@ConditionalOnProperty(prefix = "reforma.email", name = "mode", havingValue = "smtp")
public class SmtpEmailNotificacionService implements EmailNotificacionService {

    private final JavaMailSender mailSender;
    private final ReformaProperties properties;
    private final String from;

    public SmtpEmailNotificacionService(
            JavaMailSender mailSender,
            ReformaProperties properties,
            @Value("${reforma.email.from:${spring.mail.username:no-reply@reforma.local}}") String from) {
        this.mailSender = mailSender;
        this.properties = properties;
        this.from = from;
    }

    @Override
    public void enviarVerificacionEmail(Usuario usuario, String tokenPlano) {
        var enlace = properties.frontendUrl() + "/auth/verificar-email?token=" + tokenPlano;
        enviar(
                usuario.getEmail(),
                "Verificá tu cuenta en REFORMA",
                "Hola " + usuario.getNombreUsuario() + ",\n\n"
                        + "Confirmá tu email para activar tu cuenta:\n" + enlace + "\n\n"
                        + "El enlace vence en 24 horas. Si no creaste esta cuenta, ignorá este correo.");
    }

    @Override
    public void enviarResetPassword(Usuario usuario, String tokenPlano) {
        var enlace = properties.frontendUrl() + "/auth/restablecer?token=" + tokenPlano;
        enviar(
                usuario.getEmail(),
                "Restablecé tu contraseña de REFORMA",
                "Hola " + usuario.getNombreUsuario() + ",\n\n"
                        + "Para definir una nueva contraseña, entrá a:\n" + enlace + "\n\n"
                        + "El enlace vence en 1 hora y es de un solo uso. Si no lo solicitaste, ignorá este correo.");
    }

    @Override
    public void enviarInvitacionEmpleado(Usuario dueno, Usuario empleado, String tokenPlano) {
        var enlace = properties.frontendUrl() + "/auth/aceptar-invitacion?token=" + tokenPlano;
        enviar(
                empleado.getEmail(),
                "Te invitaron a un equipo en REFORMA",
                "Hola " + empleado.getNombreUsuario() + ",\n\n"
                        + dueno.getNombreUsuario() + " " + dueno.getApellidoUsuario()
                        + " te invitó a su equipo con el rol " + empleado.getRolEmpleado() + ".\n\n"
                        + "Aceptá la invitación y definí tu contraseña:\n" + enlace + "\n\n"
                        + "El enlace vence en 72 horas.");
    }

    private void enviar(String destinatario, String asunto, String cuerpo) {
        var mensaje = new SimpleMailMessage();
        mensaje.setFrom(from);
        mensaje.setTo(destinatario);
        mensaje.setSubject(asunto);
        mensaje.setText(cuerpo);
        mailSender.send(mensaje);
        log.info("[EMAIL:SMTP] enviado '{}' a {}", asunto, destinatario);
    }
}
