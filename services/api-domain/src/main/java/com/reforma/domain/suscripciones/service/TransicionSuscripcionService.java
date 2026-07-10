package com.reforma.domain.suscripciones.service;

import com.reforma.domain.auditoria.domain.AccionAuditoria;
import com.reforma.domain.auditoria.dto.AuditoriaEvento;
import com.reforma.domain.auditoria.service.AuditoriaService;
import com.reforma.domain.common.domain.PlanSuscripcion;
import com.reforma.domain.empleados.service.EmpleadoService;
import com.reforma.domain.suscripciones.domain.EstadoPago;
import com.reforma.domain.suscripciones.domain.EstadoSuscripcion;
import com.reforma.domain.suscripciones.entity.Pago;
import com.reforma.domain.suscripciones.entity.Suscripcion;
import com.reforma.domain.suscripciones.pasarela.PasarelaPagosService;
import com.reforma.domain.suscripciones.repository.PagoRepository;
import com.reforma.domain.suscripciones.repository.SuscripcionRepository;
import com.reforma.domain.usuarios.email.EmailNotificacionService;
import com.reforma.domain.usuarios.entity.Usuario;
import com.reforma.domain.usuarios.repository.UsuarioRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Transiciones que aplica el job de vencimientos (RD-P8) sobre UNA suscripción, cada una en su
 * propia transacción ({@code REQUIRES_NEW}) para que el fallo de una cuenta no arrastre al resto
 * (mismo patrón que {@code LimpiezaCuentasDemoService}). Re-evalúa el estado adentro de la
 * transacción: entre que {@code VencimientoSuscripcionService} arma los candidatos y esta clase
 * procesa, el dueño pudo reactivar/cambiar el plan.
 *
 * <p>Transiciones, en orden de prioridad:
 * <ol>
 *   <li><b>CANCELADA vencida (RD-P7):</b> estado EXPIRADA y caída a DEMO (con
 *       {@code fecha_inicio_demo} = ahora y desactivación de empleados excedentes, RD-P6.b.4).</li>
 *   <li><b>Gracia por rechazo agotada (RD-P8):</b> último cobro RECHAZADO hace más de la gracia
 *       → EXPIRADA + caída a DEMO + baja del cobro recurrente.</li>
 *   <li><b>Downgrade programado vencido (RD-P5):</b> re-verifica la precondición de empleados;
 *       si se rompió (carrera), POSPONE el downgrade renovando un ciclo más del plan actual
 *       (RD-P6.b.3); si se cumple, aplica el plan pendiente con precio de lista vigente.</li>
 *   <li><b>Renovación (RD-P4):</b> ciclo nuevo desde el fin del anterior (no desde hoy: cobrar
 *       tarde no regala días). En modo simulado el cobro sale aprobado; en {@code mp} la pasarela
 *       devuelve vacío y la renovación la resuelve el webhook (Etapa 4).</li>
 * </ol>
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class TransicionSuscripcionService {

    private static final String TABLA_SUSCRIPCION = "t_suscripcion";

    private final SuscripcionRepository suscripcionRepository;
    private final UsuarioRepository usuarioRepository;
    private final PagoRepository pagoRepository;
    private final PlanService planService;
    private final PrecioPlanService precioPlanService;
    private final PasarelaPagosService pasarela;
    private final EmpleadoService empleadoService;
    private final EmailNotificacionService emailNotificacionService;
    private final AuditoriaService auditoriaService;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void procesar(Long idSuscripcion, Instant ahora, Instant corteGracia) {
        var s = suscripcionRepository.findById(idSuscripcion).orElse(null);
        if (s == null) {
            return;
        }
        var dueno = usuarioRepository.findById(s.getIdUsuario()).orElse(null);
        if (dueno == null) {
            return;
        }
        boolean cicloVencido = s.getFechaFinPeriodo() != null
                && !s.getFechaFinPeriodo().isAfter(ahora);

        if (s.getEstado() == EstadoSuscripcion.CANCELADA && cicloVencido) {
            expirar(s, dueno, ahora, "Fin de ciclo tras cancelación");
        } else if (s.getEstado() == EstadoSuscripcion.ACTIVA && graciaAgotada(s, corteGracia)) {
            pasarela.cancelarCobroRecurrente(s);
            expirar(s, dueno, ahora, "Gracia por cobro rechazado agotada");
        } else if (s.getEstado() == EstadoSuscripcion.ACTIVA && cicloVencido
                && s.getPlanPendiente() != null) {
            aplicarDowngrade(s, dueno, ahora);
        } else if (s.getEstado() == EstadoSuscripcion.ACTIVA && cicloVencido) {
            renovar(s, dueno, ahora);
        }
    }

    private static boolean graciaAgotada(Suscripcion s, Instant corteGracia) {
        return s.getUltimoCobroEstado() == EstadoPago.RECHAZADO
                && s.getUltimoCobroFecha() != null
                && !s.getUltimoCobroFecha().isAfter(corteGracia);
    }

    // ------------------------------------------------------------- transiciones

    /** RD-P5: aplica (o pospone, RD-P6.b.3) el downgrade programado al vencer el ciclo. */
    private void aplicarDowngrade(Suscripcion s, Usuario dueno, Instant ahora) {
        var destino = s.getPlanPendiente();
        long activos = usuarioRepository.countByUsuarioDuenoIdAndActivoComoEmpleadoTrue(dueno.getId());
        int limite = planService.limiteEmpleados(destino);
        if (activos > limite) {
            // Carrera programar→aplicar: no se desactiva a nadie por un downgrade voluntario;
            // se renueva un ciclo más del plan actual y el cambio queda para el próximo corte.
            renovar(s, dueno, ahora);
            auditar(dueno.getId(), s.getId(), AccionAuditoria.DOWNGRADE_POSPUESTO,
                    "Downgrade pospuesto un ciclo: empleados activos sobre el límite del plan destino",
                    Map.of("planDestino", destino.name(),
                            "empleadosActivos", String.valueOf(activos),
                            "limiteDestino", String.valueOf(limite)));
            emailNotificacionService.enviarAvisoSuscripcion(dueno,
                    "Tu cambio de plan en REFORMA quedó pospuesto",
                    "Tu cambio al plan " + destino.name() + " no pudo aplicarse porque tu equipo tiene "
                            + activos + " integrante(s) activo(s) y el plan destino admite " + limite + ".\n"
                            + "Renovamos tu plan actual por un ciclo más; desactivá integrantes para que"
                            + " el cambio se aplique en el próximo vencimiento.");
            return;
        }

        var planAnterior = s.getPlan();
        var periodo = s.getPeriodoPendiente() != null ? s.getPeriodoPendiente() : s.getPeriodo();
        var precio = precioPlanService.precio(destino, periodo);
        var inicioNuevoCiclo = s.getFechaFinPeriodo();
        s.setPlan(destino);
        s.setPeriodo(periodo);
        s.setPrecioArs(precio);
        s.setPlanPendiente(null);
        s.setPeriodoPendiente(null);
        s.setFechaInicio(inicioNuevoCiclo);
        s.setFechaFinPeriodo(periodo.finDeCiclo(inicioNuevoCiclo));
        s.setFechaActualizacion(ahora);
        pasarela.actualizarMontoRecurrente(s, precio);
        cobrarCiclo(s, dueno, ahora, "Downgrade aplicado");

        dueno.setPlanSuscripcion(destino);
        auditar(dueno.getId(), s.getId(), AccionAuditoria.DOWNGRADE_APLICADO,
                "Downgrade aplicado a fin de ciclo",
                Map.of("planAnterior", planAnterior.name(),
                        "plan", destino.name(),
                        "periodo", periodo.name(),
                        "montoArs", precio.toPlainString()));
        log.info("Downgrade aplicado: {} {} -> {}", dueno.getEmail(), planAnterior, destino);
    }

    /** RD-P4: renueva el ciclo vigente; el nuevo arranca donde terminó el anterior. */
    private void renovar(Suscripcion s, Usuario dueno, Instant ahora) {
        var inicioNuevoCiclo = s.getFechaFinPeriodo();
        s.setFechaInicio(inicioNuevoCiclo);
        s.setFechaFinPeriodo(s.getPeriodo().finDeCiclo(inicioNuevoCiclo));
        s.setFechaActualizacion(ahora);
        cobrarCiclo(s, dueno, ahora, "Renovación");
    }

    /**
     * Cobra el ciclo recién abierto vía pasarela. Vacío = el cobro lo notifica el webhook
     * (modo {@code mp}); acá no se toca {@code ultimo_cobro}. Un RECHAZADO deja la
     * suscripción ACTIVA en gracia (RD-P8) y avisa al dueño.
     */
    private void cobrarCiclo(Suscripcion s, Usuario dueno, Instant ahora, String contexto) {
        var resultado = pasarela.cobrarRenovacion(s).orElse(null);
        if (resultado == null) {
            return;
        }
        s.setUltimoCobroEstado(resultado);
        s.setUltimoCobroFecha(ahora);
        registrarPago(s, resultado, s.getPrecioArs(), ahora);
        auditar(dueno.getId(), s.getId(), AccionAuditoria.PAGO_REGISTRADO,
                contexto + ": cobro " + (resultado == EstadoPago.APROBADO ? "aprobado" : "rechazado"),
                Map.of("plan", s.getPlan().name(), "montoArs", s.getPrecioArs().toPlainString()));
        if (resultado == EstadoPago.RECHAZADO) {
            emailNotificacionService.enviarAvisoSuscripcion(dueno,
                    "No pudimos cobrar tu suscripción de REFORMA",
                    "El cobro de tu plan " + s.getPlan().name() + " fue rechazado. Vamos a reintentar;"
                            + " si el pago no se acredita, tu cuenta pasará a DEMO al agotarse la gracia.");
        }
    }

    /** RD-P7/P8: cierra la suscripción y hace caer el plan efectivo a DEMO. */
    private void expirar(Suscripcion s, Usuario dueno, Instant ahora, String motivo) {
        var planAnterior = s.getPlan();
        s.setEstado(EstadoSuscripcion.EXPIRADA);
        s.setPlanPendiente(null);
        s.setPeriodoPendiente(null);
        s.setFechaActualizacion(ahora);

        dueno.setPlanSuscripcion(PlanSuscripcion.DEMO);
        // Reinicia la ventana de retención DEMO (RD-P7): la purga cuenta desde acá, no
        // desde un fecha_registro posiblemente viejo.
        dueno.setFechaInicioDemo(ahora);
        desactivarEmpleadosExcedentes(dueno);

        auditar(dueno.getId(), s.getId(), AccionAuditoria.SUSCRIPCION_EXPIRADA, motivo,
                Map.of("planAnterior", planAnterior.name(), "plan", PlanSuscripcion.DEMO.name()));
        emailNotificacionService.enviarAvisoSuscripcion(dueno,
                "Tu suscripción de REFORMA finalizó",
                "Tu plan " + planAnterior.name() + " finalizó y tu cuenta pasó a DEMO. Tus datos se"
                        + " conservan por el período de retención de las cuentas de prueba; podés"
                        + " recontratar un plan cuando quieras desde la sección Planes.");
        log.info("Suscripción expirada ({}): {} {} -> DEMO", motivo, dueno.getEmail(), planAnterior);
    }

    /**
     * RD-P6.b.4: al caer a DEMO, desactiva los empleados que exceden el límite del plan,
     * los más recientes primero (criterio de menor arraigo). Pasa por
     * {@code EmpleadoService.cambiarEstado} para heredar auditoría y revocación de sesiones.
     */
    private void desactivarEmpleadosExcedentes(Usuario dueno) {
        int limite = planService.limiteEmpleados(PlanSuscripcion.DEMO);
        var activos = usuarioRepository
                .findByUsuarioDuenoIdOrderByFechaVinculacionDesc(dueno.getId()).stream()
                .filter(u -> Boolean.TRUE.equals(u.getActivoComoEmpleado()))
                .toList();
        long excedente = activos.size() - limite;
        for (int i = 0; i < excedente; i++) {
            var empleado = activos.get(i);
            empleadoService.cambiarEstado(dueno.getId(), empleado.getId(), false);
            log.info("Empleado {} desactivado por caída a DEMO del tenant {}",
                    empleado.getEmail(), dueno.getEmail());
        }
    }

    // ------------------------------------------------------------------ helpers

    private void registrarPago(Suscripcion s, EstadoPago estado, BigDecimal monto, Instant fecha) {
        pagoRepository.save(Pago.builder()
                .idSuscripcion(s.getId())
                .montoArs(monto)
                .estado(estado)
                .descripcion(SuscripcionService.descripcionPago(s.getPlan(), s.getPeriodo()))
                .fechaPago(fecha)
                .build());
    }

    private void auditar(String idUsuario, Long idSuscripcion, AccionAuditoria accion,
            String descripcion, Object datosNuevos) {
        auditoriaService.registrar(AuditoriaEvento.builder()
                .idUsuario(idUsuario)
                .tablaOrigen(TABLA_SUSCRIPCION)
                .idRegistro(String.valueOf(idSuscripcion))
                .accion(accion)
                .descripcion(descripcion)
                .datosNuevos(datosNuevos)
                .build());
    }
}
