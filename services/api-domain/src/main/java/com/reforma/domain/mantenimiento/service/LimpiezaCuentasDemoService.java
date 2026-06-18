package com.reforma.domain.mantenimiento.service;

import com.reforma.domain.common.domain.PlanSuscripcion;
import com.reforma.domain.mantenimiento.repository.PurgaCuentaDemoRepository;
import com.reforma.domain.usuarios.entity.Usuario;
import com.reforma.domain.usuarios.repository.UsuarioRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Política de retención de cuentas de prueba: una cuenta <b>DEMO</b> se purga por completo
 * (sus granjas y datos, el historial de precios para ML, la auditoría, los tokens/links emitidos
 * y la propia cuenta del dueño y sus empleados) a los {@code reforma.demo.retencion-dias} días
 * de su registro. Así el entorno de demostración se mantiene limpio y los emails vuelven a quedar
 * libres para registrarse de nuevo.
 *
 * <p>Corre como tarea programada ({@code reforma.demo.purga-cron}, por defecto a diario 03:00).
 * Cada tenant se purga en su propia transacción, de modo que un fallo aislado no detiene al resto.
 */
@Service
public class LimpiezaCuentasDemoService {

    private static final Logger log = LoggerFactory.getLogger(LimpiezaCuentasDemoService.class);

    private final UsuarioRepository usuarioRepository;
    private final PurgaCuentaDemoRepository purgaRepository;
    private final boolean habilitada;
    private final long retencionDias;
    private final Set<String> emailsExentos;

    public LimpiezaCuentasDemoService(
            UsuarioRepository usuarioRepository,
            PurgaCuentaDemoRepository purgaRepository,
            @Value("${reforma.demo.purga-habilitada:true}") boolean habilitada,
            @Value("${reforma.demo.retencion-dias:60}") long retencionDias,
            @Value("${reforma.demo.emails-exentos:}") String emailsExentos) {
        this.usuarioRepository = usuarioRepository;
        this.purgaRepository = purgaRepository;
        this.habilitada = habilitada;
        this.retencionDias = retencionDias;
        this.emailsExentos = Arrays.stream(emailsExentos.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(s -> s.toLowerCase())
                .collect(Collectors.toUnmodifiableSet());
    }

    @Scheduled(cron = "${reforma.demo.purga-cron:0 0 3 * * *}")
    public void purgarCuentasDemoVencidas() {
        if (!habilitada) {
            return;
        }
        Instant corte = Instant.now().minus(retencionDias, ChronoUnit.DAYS);
        List<Usuario> candidatos = usuarioRepository
                .findByPlanSuscripcionAndEsUsuarioEmpleadoFalseAndFechaRegistroBefore(
                        PlanSuscripcion.DEMO, corte);
        if (candidatos.isEmpty()) {
            return;
        }
        int purgadas = 0;
        for (Usuario u : candidatos) {
            if (emailsExentos.contains(u.getEmail().toLowerCase())) {
                continue;
            }
            try {
                purgaRepository.purgarTenant(u.getId());
                purgadas++;
                log.info("Cuenta DEMO purgada por retención: {} (registrada {})",
                        u.getEmail(), u.getFechaRegistro());
            } catch (Exception e) {
                log.error("No se pudo purgar la cuenta DEMO {}: {}", u.getEmail(), e.getMessage(), e);
            }
        }
        log.info("Limpieza DEMO: {}/{} cuentas vencidas purgadas (retención {} días).",
                purgadas, candidatos.size(), retencionDias);
    }
}
