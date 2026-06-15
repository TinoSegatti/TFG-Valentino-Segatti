package com.reforma.domain.usuarios.email;

import com.reforma.domain.config.ReformaProperties;
import com.reforma.domain.usuarios.entity.Usuario;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * Implementación por defecto (dev/test): escribe el enlace en el log en vez de enviar correo.
 * Activa salvo que se configure {@code reforma.email.mode=smtp}. El envío SMTP real
 * (RF-AUTH) queda pendiente — ver {@code docs/MODULO_USUARIOS.md} §7.
 */
@Service
@Slf4j
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "reforma.email", name = "mode", havingValue = "log", matchIfMissing = true)
public class LogEmailNotificacionService implements EmailNotificacionService {

    private final ReformaProperties properties;

    @Override
    public void enviarVerificacionEmail(Usuario usuario, String tokenPlano) {
        var enlace = properties.frontendUrl() + "/auth/verificar-email?token=" + tokenPlano;
        log.info("[EMAIL:VERIFICACION] para {} -> {}", usuario.getEmail(), enlace);
    }

    @Override
    public void enviarResetPassword(Usuario usuario, String tokenPlano) {
        var enlace = properties.frontendUrl() + "/auth/restablecer?token=" + tokenPlano;
        log.info("[EMAIL:RESET] para {} -> {}", usuario.getEmail(), enlace);
    }
}
