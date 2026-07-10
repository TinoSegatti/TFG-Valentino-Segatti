package com.reforma.domain.suscripciones.service;

import com.reforma.domain.auditoria.domain.AccionAuditoria;
import com.reforma.domain.auditoria.dto.AuditoriaEvento;
import com.reforma.domain.auditoria.service.AuditoriaService;
import com.reforma.domain.common.domain.PlanSuscripcion;
import com.reforma.domain.suscripciones.domain.EmpleadosSobreLimiteException;
import com.reforma.domain.suscripciones.domain.EstadoPago;
import com.reforma.domain.suscripciones.domain.EstadoSuscripcion;
import com.reforma.domain.suscripciones.dto.CheckoutRequest;
import com.reforma.domain.suscripciones.dto.CheckoutResponse;
import com.reforma.domain.suscripciones.dto.ConfirmacionSimuladaRequest;
import com.reforma.domain.suscripciones.dto.PaginaPagos;
import com.reforma.domain.suscripciones.dto.PagoResponse;
import com.reforma.domain.suscripciones.dto.PlanCatalogoResponse;
import com.reforma.domain.suscripciones.dto.SuscripcionResponse;
import com.reforma.domain.suscripciones.domain.PeriodoFacturacion;
import com.reforma.domain.suscripciones.entity.Pago;
import com.reforma.domain.suscripciones.entity.Suscripcion;
import com.reforma.domain.suscripciones.pasarela.PasarelaPagosService;
import com.reforma.domain.suscripciones.repository.PagoRepository;
import com.reforma.domain.suscripciones.repository.SuscripcionRepository;
import com.reforma.domain.usuarios.entity.Usuario;
import com.reforma.domain.usuarios.repository.UsuarioRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Módulo Suscripciones: catálogo/consultas (Etapa 1) y máquina de estados comercial (Etapa 2).
 *
 * <p>Reglas de la máquina de estados (ver {@code docs/PLAN_PAGOS_SUSCRIPCIONES.md}):
 * <ul>
 *   <li><b>Upgrade / primera contratación / cambio de período (RD-P4):</b> inmediato, sin
 *       prorrateo — checkout con cobro; al aprobarse arranca un ciclo nuevo desde hoy y se
 *       escribe {@code t_usuarios.plan_suscripcion} (lo único que lee {@code PlanService}).</li>
 *   <li><b>Downgrade a plan pago menor (RD-P5):</b> diferido — se programa en
 *       {@code plan_pendiente} y lo aplica el job de vencimientos a fin de ciclo. Programarlo
 *       exige la precondición de empleados (RD-P6.b.1, 409 estructurado).</li>
 *   <li><b>Un checkout de upgrade NO toca una fila ACTIVA/CANCELADA:</b> la intención de compra
 *       viaja por parámetros hasta la confirmación, así un checkout abandonado no destruye el
 *       estado vigente. {@code plan_pendiente} conserva una única semántica: downgrade programado.</li>
 *   <li><b>Cancelar (RD-P7) nunca se bloquea:</b> detiene el cobro y programa la caída a DEMO
 *       a fin de ciclo (los empleados excedentes se desactivan al aplicarse, RD-P6.b.4).</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class SuscripcionService {

    private static final int TAMANO_MAXIMO_PAGINA = 100;
    private static final String TABLA_SUSCRIPCION = "t_suscripcion";

    private final UsuarioRepository usuarioRepository;
    private final SuscripcionRepository suscripcionRepository;
    private final PagoRepository pagoRepository;
    private final PlanService planService;
    private final PrecioPlanService precioPlanService;
    private final PasarelaPagosService pasarela;
    private final AuditoriaService auditoriaService;
    private final CheckoutIdempotencia checkoutIdempotencia;

    /**
     * Límite de empleados operativo del tenant (RD-P6.b.2): mientras haya un downgrade
     * programado, el alta/reactivación de empleados valida contra
     * {@code min(límite del plan actual, límite del plan pendiente)} para que la precondición
     * no pueda romperse entre programar y aplicar. {@code EmpleadoService} consulta esto.
     */
    public record LimiteEmpleados(int limite, PlanSuscripcion planQueLimita, boolean porCambioProgramado) {}

    // ------------------------------------------------------------- consultas (Etapa 1)

    /** Catálogo público: una card por plan, armada desde PlanService + PrecioPlanService. */
    public List<PlanCatalogoResponse> catalogo() {
        return Arrays.stream(PlanSuscripcion.values()).map(this::cardDe).toList();
    }

    @Transactional(readOnly = true)
    public SuscripcionResponse obtenerMiSuscripcion(String idUsuario) {
        Usuario usuario = cargarUsuario(idUsuario);
        return suscripcionRepository
                .findByIdUsuario(idUsuario)
                .map(s -> aResponse(usuario.getPlanSuscripcion(), s))
                .orElseGet(() -> SuscripcionResponse.implicita(usuario.getPlanSuscripcion()));
    }

    @Transactional(readOnly = true)
    public PaginaPagos listarPagos(String idUsuario, int pagina, int tamano) {
        int tamanoEfectivo = Math.min(Math.max(tamano, 1), TAMANO_MAXIMO_PAGINA);
        return suscripcionRepository
                .findByIdUsuario(idUsuario)
                .map(s -> aPagina(pagoRepository.findByIdSuscripcionOrderByFechaPagoDesc(
                        s.getId(), PageRequest.of(Math.max(pagina, 0), tamanoEfectivo))))
                .orElseGet(() -> new PaginaPagos(List.of(), 0, tamanoEfectivo, 0, 0));
    }

    @Transactional(readOnly = true)
    public LimiteEmpleados limiteEmpleadosOperativo(String idDueno) {
        var planActual = planService.obtenerPlanEfectivo(idDueno);
        int limiteActual = planService.limiteEmpleados(planActual);
        var pendiente = suscripcionRepository.findByIdUsuario(idDueno)
                .map(Suscripcion::getPlanPendiente)
                .orElse(null);
        if (pendiente != null && planService.limiteEmpleados(pendiente) < limiteActual) {
            return new LimiteEmpleados(planService.limiteEmpleados(pendiente), pendiente, true);
        }
        return new LimiteEmpleados(limiteActual, planActual, false);
    }

    // ------------------------------------------------------------- checkout (Etapa 2)

    /**
     * Inicia la contratación de {@code (plan, periodo)}. Upgrade/primera contratación/cambio de
     * período → devuelve la URL de pago (RD-P4). Downgrade con suscripción activa → lo programa
     * a fin de ciclo sin cobro (RD-P5). En ambos casos aplica la precondición de empleados
     * (RD-P6.b.1) — nunca se contrata un plan con el equipo por encima del límite destino.
     */
    @Transactional
    public CheckoutResponse checkout(String idUsuario, CheckoutRequest request) {
        Usuario dueno = cargarUsuario(idUsuario);
        if (request.plan() == PlanSuscripcion.DEMO) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "DEMO no se contrata; para volver a DEMO cancelá tu suscripción");
        }
        var existente = suscripcionRepository.findByIdUsuario(idUsuario).orElse(null);
        if (existente != null && existente.getEstado() == EstadoSuscripcion.ACTIVA
                && existente.getPlan() == request.plan()
                && existente.getPeriodo() == request.periodo()
                && existente.getPlanPendiente() == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Ya tenés contratado ese plan y período");
        }
        validarPrecondicionEmpleados(dueno.getId(), request.plan());

        boolean esDowngrade = request.plan().ordinal() < dueno.getPlanSuscripcion().ordinal();
        if (esDowngrade && existente != null && existente.getEstado() == EstadoSuscripcion.ACTIVA) {
            return programarDowngrade(dueno, existente, request);
        }
        // Ventana de idempotencia: doble clic / doble pestaña / retry devuelven la misma URL
        // de pago en vez de crear otro preapproval en MP (ver CheckoutIdempotencia).
        return checkoutIdempotencia.obtenerOCrear(idUsuario, request,
                () -> iniciarCheckoutConCobro(dueno, existente, request));
    }

    /** Downgrade diferido (RD-P5): sin cobro; el job lo aplica al vencer el ciclo pagado. */
    private CheckoutResponse programarDowngrade(
            Usuario dueno, Suscripcion suscripcion, CheckoutRequest request) {
        suscripcion.setPlanPendiente(request.plan());
        suscripcion.setPeriodoPendiente(request.periodo());
        suscripcion.setFechaActualizacion(Instant.now());
        auditar(dueno.getId(), suscripcion.getId(), AccionAuditoria.DOWNGRADE_PROGRAMADO,
                "Downgrade programado a fin de ciclo",
                Map.of("planDestino", request.plan().name(),
                        "periodoDestino", request.periodo().name(),
                        "aplicaDesde", String.valueOf(suscripcion.getFechaFinPeriodo())));
        return new CheckoutResponse(pasarela.modo(), false, null,
                aResponse(dueno.getPlanSuscripcion(), suscripcion));
    }

    /**
     * Contratación con cobro (RD-P4). Solo crea/pisa la fila cuando no hay una suscripción
     * viva que proteger (sin fila, PENDIENTE_PAGO o EXPIRADA); sobre ACTIVA/CANCELADA la
     * intención viaja hasta {@code confirmar-simulado}/webhook sin tocar el estado vigente.
     */
    private CheckoutResponse iniciarCheckoutConCobro(
            Usuario dueno, Suscripcion existente, CheckoutRequest request) {
        var ahora = Instant.now();
        var precio = precioPlanService.precio(request.plan(), request.periodo());
        Suscripcion base = existente;
        if (existente == null) {
            base = suscripcionRepository.save(nuevaPendiente(dueno.getId(), request, precio, ahora));
        } else if (existente.getEstado() == EstadoSuscripcion.PENDIENTE_PAGO
                || existente.getEstado() == EstadoSuscripcion.EXPIRADA) {
            existente.setPlan(request.plan());
            existente.setPeriodo(request.periodo());
            existente.setPrecioArs(precio);
            existente.setEstado(EstadoSuscripcion.PENDIENTE_PAGO);
            existente.setFechaInicio(ahora);
            existente.setFechaFinPeriodo(null);
            existente.setPlanPendiente(null);
            existente.setPeriodoPendiente(null);
            existente.setFechaActualizacion(ahora);
        }
        auditar(dueno.getId(), base.getId(), AccionAuditoria.CHECKOUT_INICIADO,
                "Checkout de suscripción iniciado",
                Map.of("plan", request.plan().name(),
                        "periodo", request.periodo().name(),
                        "montoArs", precio.toPlainString()));
        var url = pasarela.iniciarCheckout(dueno, request.plan(), request.periodo(), precio);
        return new CheckoutResponse(pasarela.modo(), true, url,
                aResponse(dueno.getPlanSuscripcion(), base));
    }

    // ------------------------------------------------- confirmación simulada (Etapa 2)

    /**
     * Cierre del checkout simulado (RD-P2): aplica la misma lógica que el webhook real.
     * 404 con {@code mode=mp} (la pantalla simulada no existe en producción real).
     */
    @Transactional
    public SuscripcionResponse confirmarSimulado(String idUsuario, ConfirmacionSimuladaRequest request) {
        if (!PasarelaPagosService.MODO_SIMULADO.equals(pasarela.modo())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "La confirmación simulada no está disponible en este modo");
        }
        if (request.resultado() != EstadoPago.APROBADO && request.resultado() != EstadoPago.RECHAZADO) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "resultado debe ser APROBADO o RECHAZADO");
        }
        if (request.plan() == PlanSuscripcion.DEMO) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "DEMO no se contrata");
        }
        Usuario dueno = cargarUsuario(idUsuario);
        var ahora = Instant.now();
        var precio = precioPlanService.precio(request.plan(), request.periodo());
        var existente = suscripcionRepository.findByIdUsuario(idUsuario).orElse(null);

        if (request.resultado() == EstadoPago.RECHAZADO) {
            return registrarRechazoSimulado(dueno, existente, request, precio, ahora);
        }
        return activar(dueno, existente, request.plan(), request.periodo(), precio, ahora,
                null, true);
    }

    /**
     * Activación disparada por el webhook de Mercado Pago (RD-P9): preapproval {@code authorized}.
     * Misma máquina de estados que la confirmación simulada, con dos diferencias: el monto es el
     * del preapproval (snapshot que viajó en el checkout, RD-P12) y NO se inserta un pago local —
     * los cobros reales llegan como eventos {@code payment} propios, con su id idempotente.
     */
    @Transactional
    public SuscripcionResponse activarDesdePasarela(String idUsuario, PlanSuscripcion plan,
            PeriodoFacturacion periodo, BigDecimal montoArs, String mpPreapprovalId) {
        Usuario dueno = cargarUsuario(idUsuario);
        var existente = suscripcionRepository.findByIdUsuario(idUsuario).orElse(null);
        var precio = montoArs != null ? montoArs : precioPlanService.precio(plan, periodo);
        return activar(dueno, existente, plan, periodo, precio, Instant.now(), mpPreapprovalId, false);
    }

    /**
     * Pago rechazado en la pantalla simulada. Queda en el historial; solo marca
     * {@code ultimo_cobro} cuando la fila está PENDIENTE_PAGO — el intento fallido de un
     * upgrade NO cuenta contra la gracia del plan vigente (RD-P8: esa gracia mide los cobros
     * del ciclo contratado, no compras que nunca se concretaron).
     */
    private SuscripcionResponse registrarRechazoSimulado(
            Usuario dueno, Suscripcion existente, ConfirmacionSimuladaRequest request,
            BigDecimal precio, Instant ahora) {
        Suscripcion base = existente != null
                ? existente
                : suscripcionRepository.save(nuevaPendiente(
                        dueno.getId(), new CheckoutRequest(request.plan(), request.periodo()), precio, ahora));
        registrarPago(base, EstadoPago.RECHAZADO, precio,
                descripcionPago(request.plan(), request.periodo()), ahora);
        if (base.getEstado() == EstadoSuscripcion.PENDIENTE_PAGO) {
            base.setUltimoCobroEstado(EstadoPago.RECHAZADO);
            base.setUltimoCobroFecha(ahora);
            base.setFechaActualizacion(ahora);
        }
        auditar(dueno.getId(), base.getId(), AccionAuditoria.PAGO_REGISTRADO,
                "Pago simulado rechazado",
                Map.of("plan", request.plan().name(), "montoArs", precio.toPlainString()));
        return aResponse(dueno.getPlanSuscripcion(), base);
    }

    /**
     * Activación: ciclo nuevo desde hoy, snapshot de precio y plan efectivo al instante (RD-P4).
     * {@code registrarCobroLocal} distingue las pasarelas: la simulada inserta acá el pago
     * aprobado; en MP el cobro real llega como evento {@code payment} y lo registra el webhook.
     */
    private SuscripcionResponse activar(
            Usuario dueno, Suscripcion existente, PlanSuscripcion plan, PeriodoFacturacion periodo,
            BigDecimal precio, Instant ahora, String mpPreapprovalId, boolean registrarCobroLocal) {
        // Re-chequeo de la precondición de empleados (carrera checkout→confirmación, RD-P6.b).
        validarPrecondicionEmpleados(dueno.getId(), plan);

        boolean primeraActivacion = existente == null
                || existente.getEstado() != EstadoSuscripcion.ACTIVA;
        Suscripcion s = existente != null
                ? existente
                : nuevaPendiente(dueno.getId(), new CheckoutRequest(plan, periodo), precio, ahora);
        s.setPlan(plan);
        s.setPeriodo(periodo);
        s.setPrecioArs(precio);
        s.setEstado(EstadoSuscripcion.ACTIVA);
        s.setFechaInicio(ahora);
        s.setFechaFinPeriodo(periodo.finDeCiclo(ahora));
        s.setPlanPendiente(null);
        s.setPeriodoPendiente(null);
        if (mpPreapprovalId != null) {
            s.setMpPreapprovalId(mpPreapprovalId);
        }
        s.setUltimoCobroEstado(EstadoPago.APROBADO);
        s.setUltimoCobroFecha(ahora);
        s.setFechaActualizacion(ahora);
        s = suscripcionRepository.save(s);

        if (registrarCobroLocal) {
            registrarPago(s, EstadoPago.APROBADO, precio, descripcionPago(plan, periodo), ahora);
        }

        var planAnterior = dueno.getPlanSuscripcion();
        dueno.setPlanSuscripcion(plan);
        // La compra se concretó: la URL de checkout cacheada (si quedara viva) ya no sirve.
        checkoutIdempotencia.invalidar(dueno.getId());

        auditar(dueno.getId(), s.getId(),
                primeraActivacion ? AccionAuditoria.SUSCRIPCION_ACTIVADA : AccionAuditoria.PLAN_CAMBIADO,
                primeraActivacion ? "Suscripción activada" : "Cambio de plan aplicado",
                Map.of("planAnterior", planAnterior.name(),
                        "plan", plan.name(),
                        "periodo", periodo.name(),
                        "montoArs", precio.toPlainString()));
        return aResponse(plan, s);
    }

    // ------------------------------------------------------- cancelar / reactivar (Etapa 2)

    /**
     * Cancela la suscripción (RD-P7): detiene el cobro recurrente YA y programa la caída a
     * DEMO a fin del ciclo pagado. Nunca se bloquea (regla de oro: siempre se puede dejar de
     * pagar); los empleados excedentes se desactivan recién al aplicarse (RD-P6.b.4).
     */
    @Transactional
    public SuscripcionResponse cancelar(String idUsuario) {
        Usuario dueno = cargarUsuario(idUsuario);
        var s = suscripcionRepository.findByIdUsuario(idUsuario)
                .filter(x -> x.getEstado() == EstadoSuscripcion.ACTIVA)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT,
                        "No hay una suscripción activa para cancelar"));
        s.setEstado(EstadoSuscripcion.CANCELADA);
        s.setPlanPendiente(PlanSuscripcion.DEMO);
        s.setPeriodoPendiente(null);
        s.setFechaActualizacion(Instant.now());
        pasarela.cancelarCobroRecurrente(s);
        auditar(dueno.getId(), s.getId(), AccionAuditoria.SUSCRIPCION_CANCELADA,
                "Suscripción cancelada; el plan sigue vigente hasta fin de ciclo",
                Map.of("plan", s.getPlan().name(),
                        "vigenteHasta", String.valueOf(s.getFechaFinPeriodo())));
        return aResponse(dueno.getPlanSuscripcion(), s);
    }

    /** Des-programa un downgrade/cancelación aún no aplicado ("Mantener mi plan"). */
    @Transactional
    public SuscripcionResponse reactivar(String idUsuario) {
        Usuario dueno = cargarUsuario(idUsuario);
        var s = suscripcionRepository.findByIdUsuario(idUsuario)
                .filter(x -> x.getPlanPendiente() != null)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT,
                        "No hay ningún cambio programado para revertir"));
        boolean eraCancelacion = s.getEstado() == EstadoSuscripcion.CANCELADA;
        var planDesprogramado = s.getPlanPendiente();
        s.setPlanPendiente(null);
        s.setPeriodoPendiente(null);
        s.setFechaActualizacion(Instant.now());
        if (eraCancelacion) {
            s.setEstado(EstadoSuscripcion.ACTIVA);
            pasarela.reanudarCobroRecurrente(s);
        }
        auditar(dueno.getId(), s.getId(), AccionAuditoria.SUSCRIPCION_REACTIVADA,
                eraCancelacion ? "Cancelación revertida" : "Downgrade des-programado",
                Map.of("planDesprogramado", planDesprogramado.name(),
                        "plan", s.getPlan().name()));
        return aResponse(dueno.getPlanSuscripcion(), s);
    }

    // ------------------------------------------------------------------ helpers

    /** RD-P6.b.1: nunca contratar un plan con más empleados activos que su límite. */
    private void validarPrecondicionEmpleados(String idDueno, PlanSuscripcion planDestino) {
        long activos = usuarioRepository.countByUsuarioDuenoIdAndActivoComoEmpleadoTrue(idDueno);
        int limite = planService.limiteEmpleados(planDestino);
        if (activos > limite) {
            throw new EmpleadosSobreLimiteException(planDestino, activos, limite);
        }
    }

    private Suscripcion nuevaPendiente(
            String idUsuario, CheckoutRequest request, BigDecimal precio, Instant ahora) {
        return Suscripcion.builder()
                .idUsuario(idUsuario)
                .plan(request.plan())
                .periodo(request.periodo())
                .estado(EstadoSuscripcion.PENDIENTE_PAGO)
                .precioArs(precio)
                .fechaInicio(ahora)
                .fechaCreacion(ahora)
                .fechaActualizacion(ahora)
                .build();
    }

    private void registrarPago(
            Suscripcion s, EstadoPago estado, BigDecimal monto, String descripcion, Instant fecha) {
        pagoRepository.save(Pago.builder()
                .idSuscripcion(s.getId())
                .montoArs(monto)
                .estado(estado)
                .descripcion(descripcion)
                .fechaPago(fecha)
                .build());
    }

    static String descripcionPago(PlanSuscripcion plan, PeriodoFacturacion periodo) {
        return "REFORMA - Plan " + plan.name() + " (" + periodo.etiqueta() + ")";
    }

    private Usuario cargarUsuario(String idUsuario) {
        return usuarioRepository.findById(idUsuario)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Usuario no encontrado"));
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

    private PlanCatalogoResponse cardDe(PlanSuscripcion plan) {
        var limites = new PlanCatalogoResponse.LimitesPlan(
                limite(planService.limiteGranjas(plan)),
                limite(planService.limiteEmpleados(plan)),
                limite(planService.limiteMateriasPrimas(plan)),
                limite(planService.limiteProveedores(plan)),
                limite(planService.limiteAnimales(plan)),
                limite(planService.limiteFormulas(plan)),
                limite(planService.limiteFabricaciones(plan)),
                limite(planService.limiteArchivos(plan)));
        return new PlanCatalogoResponse(
                plan,
                precioPlanService.precioMensual(plan),
                precioPlanService.precioAnual(plan),
                limites,
                planService.permitePrediccionStock(plan));
    }

    /** {@code Integer.MAX_VALUE} (ilimitado) se publica como {@code null} en el catálogo. */
    private static Integer limite(int valor) {
        return valor == Integer.MAX_VALUE ? null : valor;
    }

    static SuscripcionResponse aResponse(PlanSuscripcion planEfectivo, Suscripcion s) {
        return new SuscripcionResponse(
                planEfectivo,
                true,
                s.getPlan(),
                s.getPeriodo(),
                s.getEstado(),
                s.getPrecioArs(),
                s.getFechaInicio(),
                s.getFechaFinPeriodo(),
                s.getPlanPendiente(),
                s.getPeriodoPendiente(),
                s.getUltimoCobroEstado(),
                s.getUltimoCobroFecha());
    }

    private static PaginaPagos aPagina(Page<Pago> page) {
        List<PagoResponse> contenido = page.getContent().stream()
                .map(p -> new PagoResponse(
                        p.getId(), p.getMontoArs(), p.getEstado(), p.getDescripcion(), p.getFechaPago()))
                .toList();
        return new PaginaPagos(
                contenido,
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages());
    }
}
