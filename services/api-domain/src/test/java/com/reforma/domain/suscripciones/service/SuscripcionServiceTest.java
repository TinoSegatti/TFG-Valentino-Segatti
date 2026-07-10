package com.reforma.domain.suscripciones.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.reforma.domain.auditoria.domain.AccionAuditoria;
import com.reforma.domain.auditoria.dto.AuditoriaEvento;
import com.reforma.domain.auditoria.service.AuditoriaService;
import com.reforma.domain.common.domain.PlanSuscripcion;
import com.reforma.domain.suscripciones.domain.EmpleadosSobreLimiteException;
import com.reforma.domain.suscripciones.domain.EstadoPago;
import com.reforma.domain.suscripciones.domain.EstadoSuscripcion;
import com.reforma.domain.suscripciones.domain.PeriodoFacturacion;
import com.reforma.domain.suscripciones.dto.CheckoutRequest;
import com.reforma.domain.suscripciones.dto.ConfirmacionSimuladaRequest;
import com.reforma.domain.suscripciones.entity.Pago;
import com.reforma.domain.suscripciones.entity.Suscripcion;
import com.reforma.domain.suscripciones.pasarela.PasarelaPagosService;
import com.reforma.domain.suscripciones.repository.PagoRepository;
import com.reforma.domain.suscripciones.repository.SuscripcionRepository;
import com.reforma.domain.usuarios.entity.Usuario;
import com.reforma.domain.usuarios.repository.UsuarioRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class SuscripcionServiceTest {

    private static final String ID_USUARIO = "u_1";

    @Mock private UsuarioRepository usuarioRepository;
    @Mock private SuscripcionRepository suscripcionRepository;
    @Mock private PagoRepository pagoRepository;
    @Mock private PasarelaPagosService pasarela;
    @Mock private AuditoriaService auditoriaService;

    @Captor private ArgumentCaptor<AuditoriaEvento> eventoCaptor;
    @Captor private ArgumentCaptor<Suscripcion> suscripcionCaptor;
    @Captor private ArgumentCaptor<Pago> pagoCaptor;

    private SuscripcionService servicio;

    @BeforeEach
    void configurar() {
        var precios = new PrecioPlanService(
                new BigDecimal("50750"), new BigDecimal("143550"), new BigDecimal("332050"), 10);
        servicio = new SuscripcionService(
                usuarioRepository,
                suscripcionRepository,
                pagoRepository,
                new PlanService(usuarioRepository),
                precios,
                pasarela,
                auditoriaService,
                new CheckoutIdempotencia(120));
        lenient().when(pasarela.modo()).thenReturn(PasarelaPagosService.MODO_SIMULADO);
    }

    private Usuario dueno(PlanSuscripcion plan) {
        return Usuario.builder()
                .id(ID_USUARIO)
                .email("ana@reforma.com")
                .planSuscripcion(plan)
                .esUsuarioEmpleado(false)
                .build();
    }

    private Suscripcion suscripcionActiva() {
        return Suscripcion.builder()
                .id(10L)
                .idUsuario(ID_USUARIO)
                .plan(PlanSuscripcion.BUSINESS)
                .periodo(PeriodoFacturacion.MENSUAL)
                .estado(EstadoSuscripcion.ACTIVA)
                .precioArs(new BigDecimal("143550.00"))
                .fechaInicio(Instant.parse("2026-07-01T00:00:00Z"))
                .fechaFinPeriodo(Instant.parse("2026-08-01T00:00:00Z"))
                .planPendiente(PlanSuscripcion.STARTER)
                .periodoPendiente(PeriodoFacturacion.MENSUAL)
                .ultimoCobroEstado(EstadoPago.APROBADO)
                .ultimoCobroFecha(Instant.parse("2026-07-01T00:05:00Z"))
                .fechaCreacion(Instant.parse("2026-07-01T00:00:00Z"))
                .fechaActualizacion(Instant.parse("2026-07-01T00:05:00Z"))
                .build();
    }

    // ---- catálogo ----

    @Test
    void catalogo_unaCardPorPlan_conPreciosYLimites() {
        var cards = servicio.catalogo();

        assertThat(cards).hasSize(PlanSuscripcion.values().length);
        var demo = cards.get(0);
        assertThat(demo.plan()).isEqualTo(PlanSuscripcion.DEMO);
        assertThat(demo.precioMensualArs()).isEqualByComparingTo("0");
        assertThat(demo.limites().granjas()).isEqualTo(2);
        assertThat(demo.prediccionStock()).isFalse();

        var business = cards.stream()
                .filter(c -> c.plan() == PlanSuscripcion.BUSINESS).findFirst().orElseThrow();
        assertThat(business.precioMensualArs()).isEqualByComparingTo("143550");
        assertThat(business.precioAnualArs()).isEqualByComparingTo("1435500");
        assertThat(business.limites().empleados()).isEqualTo(10);
        assertThat(business.prediccionStock()).isTrue();
    }

    @Test
    void catalogo_enterpriseIlimitadoSePublicaComoNull() {
        var enterprise = servicio.catalogo().stream()
                .filter(c -> c.plan() == PlanSuscripcion.ENTERPRISE).findFirst().orElseThrow();

        assertThat(enterprise.limites().granjas()).isNull();
        assertThat(enterprise.limites().empleados()).isNull();
        assertThat(enterprise.limites().archivos()).isNull();
        assertThat(enterprise.prediccionStock()).isTrue();
    }

    // ---- mi suscripción ----

    @Test
    void obtenerMiSuscripcion_sinFila_devuelveImplicitaConPlanDelUsuario() {
        when(usuarioRepository.findById(ID_USUARIO))
                .thenReturn(Optional.of(dueno(PlanSuscripcion.DEMO)));
        when(suscripcionRepository.findByIdUsuario(ID_USUARIO)).thenReturn(Optional.empty());

        var r = servicio.obtenerMiSuscripcion(ID_USUARIO);

        assertThat(r.planEfectivo()).isEqualTo(PlanSuscripcion.DEMO);
        assertThat(r.gestionada()).isFalse();
        assertThat(r.plan()).isNull();
        assertThat(r.estado()).isNull();
    }

    @Test
    void obtenerMiSuscripcion_conFila_mapeaEstadoCompleto() {
        when(usuarioRepository.findById(ID_USUARIO))
                .thenReturn(Optional.of(dueno(PlanSuscripcion.BUSINESS)));
        when(suscripcionRepository.findByIdUsuario(ID_USUARIO))
                .thenReturn(Optional.of(suscripcionActiva()));

        var r = servicio.obtenerMiSuscripcion(ID_USUARIO);

        assertThat(r.planEfectivo()).isEqualTo(PlanSuscripcion.BUSINESS);
        assertThat(r.gestionada()).isTrue();
        assertThat(r.plan()).isEqualTo(PlanSuscripcion.BUSINESS);
        assertThat(r.periodo()).isEqualTo(PeriodoFacturacion.MENSUAL);
        assertThat(r.estado()).isEqualTo(EstadoSuscripcion.ACTIVA);
        assertThat(r.precioArs()).isEqualByComparingTo("143550.00");
        assertThat(r.planPendiente()).isEqualTo(PlanSuscripcion.STARTER);
        assertThat(r.ultimoCobroEstado()).isEqualTo(EstadoPago.APROBADO);
    }

    @Test
    void obtenerMiSuscripcion_usuarioInexistente_404() {
        when(usuarioRepository.findById("nadie")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.obtenerMiSuscripcion("nadie"))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    // ---- historial de pagos ----

    @Test
    void listarPagos_sinSuscripcion_paginaVacia() {
        when(suscripcionRepository.findByIdUsuario(ID_USUARIO)).thenReturn(Optional.empty());

        var pagina = servicio.listarPagos(ID_USUARIO, 0, 20);

        assertThat(pagina.contenido()).isEmpty();
        assertThat(pagina.totalElementos()).isZero();
        assertThat(pagina.totalPaginas()).isZero();
    }

    @Test
    void listarPagos_conSuscripcion_mapeaLaPagina() {
        var pago = Pago.builder()
                .id(1L)
                .idSuscripcion(10L)
                .montoArs(new BigDecimal("143550.00"))
                .estado(EstadoPago.APROBADO)
                .descripcion("REFORMA - Plan BUSINESS (Mensual)")
                .fechaPago(Instant.parse("2026-07-01T00:05:00Z"))
                .build();
        when(suscripcionRepository.findByIdUsuario(ID_USUARIO))
                .thenReturn(Optional.of(suscripcionActiva()));
        when(pagoRepository.findByIdSuscripcionOrderByFechaPagoDesc(
                        eq(10L), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(pago), PageRequest.of(0, 20), 1));

        var pagina = servicio.listarPagos(ID_USUARIO, 0, 20);

        assertThat(pagina.contenido()).hasSize(1);
        assertThat(pagina.contenido().get(0).montoArs()).isEqualByComparingTo("143550.00");
        assertThat(pagina.contenido().get(0).estado()).isEqualTo(EstadoPago.APROBADO);
        assertThat(pagina.totalElementos()).isEqualTo(1);
    }

    @Test
    void listarPagos_normalizaPaginacionInvalida() {
        when(suscripcionRepository.findByIdUsuario(ID_USUARIO))
                .thenReturn(Optional.of(suscripcionActiva()));
        when(pagoRepository.findByIdSuscripcionOrderByFechaPagoDesc(
                        eq(10L), any(Pageable.class)))
                .thenAnswer(inv -> {
                    Pageable p = inv.getArgument(1);
                    // tamaño pedido 5000 → clamp a 100; página -3 → 0
                    assertThat(p.getPageSize()).isEqualTo(100);
                    assertThat(p.getPageNumber()).isZero();
                    return new PageImpl<>(List.<Pago>of(), p, 0);
                });

        servicio.listarPagos(ID_USUARIO, -3, 5000);
    }

    // ---- límite de empleados operativo (RD-P6.b.2) ----

    @Test
    @DisplayName("limiteEmpleadosOperativo: sin cambio programado rige el plan actual")
    void limiteOperativo_sinPendiente() {
        when(usuarioRepository.findById(ID_USUARIO))
                .thenReturn(Optional.of(dueno(PlanSuscripcion.BUSINESS)));
        when(suscripcionRepository.findByIdUsuario(ID_USUARIO)).thenReturn(Optional.empty());

        var limite = servicio.limiteEmpleadosOperativo(ID_USUARIO);

        assertThat(limite.limite()).isEqualTo(10);
        assertThat(limite.planQueLimita()).isEqualTo(PlanSuscripcion.BUSINESS);
        assertThat(limite.porCambioProgramado()).isFalse();
    }

    @Test
    @DisplayName("limiteEmpleadosOperativo: con downgrade programado rige el menor (plan pendiente)")
    void limiteOperativo_conDowngradeProgramado() {
        when(usuarioRepository.findById(ID_USUARIO))
                .thenReturn(Optional.of(dueno(PlanSuscripcion.BUSINESS)));
        when(suscripcionRepository.findByIdUsuario(ID_USUARIO))
                .thenReturn(Optional.of(suscripcionActiva())); // planPendiente = STARTER

        var limite = servicio.limiteEmpleadosOperativo(ID_USUARIO);

        assertThat(limite.limite()).isEqualTo(2);
        assertThat(limite.planQueLimita()).isEqualTo(PlanSuscripcion.STARTER);
        assertThat(limite.porCambioProgramado()).isTrue();
    }

    // ---- checkout (Etapa 2) ----

    @Test
    @DisplayName("checkout: DEMO no se contrata → 400")
    void checkout_demoNoSeContrata() {
        when(usuarioRepository.findById(ID_USUARIO))
                .thenReturn(Optional.of(dueno(PlanSuscripcion.STARTER)));

        assertThatThrownBy(() -> servicio.checkout(ID_USUARIO,
                new CheckoutRequest(PlanSuscripcion.DEMO, PeriodoFacturacion.MENSUAL)))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("checkout: mismo plan y período ya activos sin cambio pendiente → 409")
    void checkout_mismoPlanYPeriodo() {
        var s = suscripcionActiva();
        s.setPlanPendiente(null);
        s.setPeriodoPendiente(null);
        when(usuarioRepository.findById(ID_USUARIO))
                .thenReturn(Optional.of(dueno(PlanSuscripcion.BUSINESS)));
        when(suscripcionRepository.findByIdUsuario(ID_USUARIO)).thenReturn(Optional.of(s));

        assertThatThrownBy(() -> servicio.checkout(ID_USUARIO,
                new CheckoutRequest(PlanSuscripcion.BUSINESS, PeriodoFacturacion.MENSUAL)))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    @DisplayName("checkout: empleados sobre el límite destino → EmpleadosSobreLimiteException con payload")
    void checkout_empleadosSobreLimite() {
        when(usuarioRepository.findById(ID_USUARIO))
                .thenReturn(Optional.of(dueno(PlanSuscripcion.BUSINESS)));
        when(suscripcionRepository.findByIdUsuario(ID_USUARIO))
                .thenReturn(Optional.of(suscripcionActivaSinPendiente()));
        when(usuarioRepository.countByUsuarioDuenoIdAndActivoComoEmpleadoTrue(ID_USUARIO))
                .thenReturn(5L);

        assertThatThrownBy(() -> servicio.checkout(ID_USUARIO,
                new CheckoutRequest(PlanSuscripcion.STARTER, PeriodoFacturacion.MENSUAL)))
                .isInstanceOf(EmpleadosSobreLimiteException.class)
                .satisfies(ex -> {
                    var e = (EmpleadosSobreLimiteException) ex;
                    assertThat(e.getPlanDestino()).isEqualTo(PlanSuscripcion.STARTER);
                    assertThat(e.getEmpleadosActivos()).isEqualTo(5);
                    assertThat(e.getLimiteDestino()).isEqualTo(2);
                    assertThat(e.getExcedente()).isEqualTo(3);
                });
        verify(pasarela, never()).iniciarCheckout(any(), any(), any(), any());
    }

    @Test
    @DisplayName("checkout: primera contratación crea fila PENDIENTE_PAGO y devuelve URL de pago")
    void checkout_primeraContratacion() {
        var dueno = dueno(PlanSuscripcion.DEMO);
        when(usuarioRepository.findById(ID_USUARIO)).thenReturn(Optional.of(dueno));
        when(suscripcionRepository.findByIdUsuario(ID_USUARIO)).thenReturn(Optional.empty());
        when(usuarioRepository.countByUsuarioDuenoIdAndActivoComoEmpleadoTrue(ID_USUARIO))
                .thenReturn(0L);
        when(suscripcionRepository.save(any(Suscripcion.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(pasarela.iniciarCheckout(eq(dueno), eq(PlanSuscripcion.STARTER),
                eq(PeriodoFacturacion.MENSUAL), any())).thenReturn("/pago-simulado");

        var r = servicio.checkout(ID_USUARIO,
                new CheckoutRequest(PlanSuscripcion.STARTER, PeriodoFacturacion.MENSUAL));

        assertThat(r.requierePago()).isTrue();
        assertThat(r.urlPago()).isEqualTo("/pago-simulado");
        verify(suscripcionRepository).save(suscripcionCaptor.capture());
        var creada = suscripcionCaptor.getValue();
        assertThat(creada.getEstado()).isEqualTo(EstadoSuscripcion.PENDIENTE_PAGO);
        assertThat(creada.getPlan()).isEqualTo(PlanSuscripcion.STARTER);
        assertThat(creada.getPrecioArs()).isEqualByComparingTo("50750");
        // El plan efectivo NO cambia hasta que el pago se confirme.
        assertThat(dueno.getPlanSuscripcion()).isEqualTo(PlanSuscripcion.DEMO);
        verify(auditoriaService).registrar(eventoCaptor.capture());
        assertThat(eventoCaptor.getValue().accion()).isEqualTo(AccionAuditoria.CHECKOUT_INICIADO);
    }

    @Test
    @DisplayName("checkout: upgrade sobre una ACTIVA no toca la fila vigente (la intención viaja aparte)")
    void checkout_upgradeNoTocaLaFilaActiva() {
        var dueno = dueno(PlanSuscripcion.STARTER);
        var s = suscripcionActivaSinPendiente();
        s.setPlan(PlanSuscripcion.STARTER);
        s.setPrecioArs(new BigDecimal("50750.00"));
        when(usuarioRepository.findById(ID_USUARIO)).thenReturn(Optional.of(dueno));
        when(suscripcionRepository.findByIdUsuario(ID_USUARIO)).thenReturn(Optional.of(s));
        when(usuarioRepository.countByUsuarioDuenoIdAndActivoComoEmpleadoTrue(ID_USUARIO))
                .thenReturn(0L);
        when(pasarela.iniciarCheckout(any(), any(), any(), any())).thenReturn("/pago-simulado");

        var r = servicio.checkout(ID_USUARIO,
                new CheckoutRequest(PlanSuscripcion.BUSINESS, PeriodoFacturacion.MENSUAL));

        assertThat(r.requierePago()).isTrue();
        // La fila vigente queda intacta: un checkout abandonado no destruye el estado.
        assertThat(s.getPlan()).isEqualTo(PlanSuscripcion.STARTER);
        assertThat(s.getEstado()).isEqualTo(EstadoSuscripcion.ACTIVA);
        assertThat(s.getPlanPendiente()).isNull();
        verify(suscripcionRepository, never()).save(any());
    }

    @Test
    @DisplayName("checkout: downgrade con ACTIVA queda programado a fin de ciclo sin cobro (RD-P5)")
    void checkout_downgradeProgramado() {
        var dueno = dueno(PlanSuscripcion.BUSINESS);
        var s = suscripcionActivaSinPendiente();
        when(usuarioRepository.findById(ID_USUARIO)).thenReturn(Optional.of(dueno));
        when(suscripcionRepository.findByIdUsuario(ID_USUARIO)).thenReturn(Optional.of(s));
        when(usuarioRepository.countByUsuarioDuenoIdAndActivoComoEmpleadoTrue(ID_USUARIO))
                .thenReturn(2L);

        var r = servicio.checkout(ID_USUARIO,
                new CheckoutRequest(PlanSuscripcion.STARTER, PeriodoFacturacion.MENSUAL));

        assertThat(r.requierePago()).isFalse();
        assertThat(r.urlPago()).isNull();
        assertThat(s.getPlanPendiente()).isEqualTo(PlanSuscripcion.STARTER);
        assertThat(s.getPeriodoPendiente()).isEqualTo(PeriodoFacturacion.MENSUAL);
        // El plan vigente sigue igual hasta que el job lo aplique.
        assertThat(s.getPlan()).isEqualTo(PlanSuscripcion.BUSINESS);
        assertThat(dueno.getPlanSuscripcion()).isEqualTo(PlanSuscripcion.BUSINESS);
        verify(pasarela, never()).iniciarCheckout(any(), any(), any(), any());
        verify(auditoriaService).registrar(eventoCaptor.capture());
        assertThat(eventoCaptor.getValue().accion()).isEqualTo(AccionAuditoria.DOWNGRADE_PROGRAMADO);
    }

    @Test
    @DisplayName("checkout: doble clic (mismo plan y período) reusa la URL sin crear otro checkout")
    void checkout_dobleClicReusaLaMismaUrl() {
        var dueno = dueno(PlanSuscripcion.DEMO);
        when(usuarioRepository.findById(ID_USUARIO)).thenReturn(Optional.of(dueno));
        when(suscripcionRepository.findByIdUsuario(ID_USUARIO)).thenReturn(Optional.empty());
        when(usuarioRepository.countByUsuarioDuenoIdAndActivoComoEmpleadoTrue(ID_USUARIO))
                .thenReturn(0L);
        when(suscripcionRepository.save(any(Suscripcion.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(pasarela.iniciarCheckout(any(), any(), any(), any())).thenReturn("/pago-simulado");
        var request = new CheckoutRequest(PlanSuscripcion.STARTER, PeriodoFacturacion.MENSUAL);

        var primero = servicio.checkout(ID_USUARIO, request);
        var segundo = servicio.checkout(ID_USUARIO, request);

        assertThat(segundo.urlPago()).isEqualTo(primero.urlPago());
        // Un solo preapproval/checkout contra la pasarela y una sola auditoría de inicio.
        verify(pasarela, times(1)).iniciarCheckout(any(), any(), any(), any());
        verify(auditoriaService, times(1)).registrar(any(AuditoriaEvento.class));
    }

    @Test
    @DisplayName("checkout: dentro de la ventana, pedir OTRO plan sí crea un checkout nuevo")
    void checkout_cambioDeIntencionCreaCheckoutNuevo() {
        var dueno = dueno(PlanSuscripcion.DEMO);
        when(usuarioRepository.findById(ID_USUARIO)).thenReturn(Optional.of(dueno));
        when(suscripcionRepository.findByIdUsuario(ID_USUARIO)).thenReturn(Optional.empty());
        when(usuarioRepository.countByUsuarioDuenoIdAndActivoComoEmpleadoTrue(ID_USUARIO))
                .thenReturn(0L);
        when(suscripcionRepository.save(any(Suscripcion.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(pasarela.iniciarCheckout(any(), eq(PlanSuscripcion.STARTER), any(), any()))
                .thenReturn("/pago-starter");
        when(pasarela.iniciarCheckout(any(), eq(PlanSuscripcion.BUSINESS), any(), any()))
                .thenReturn("/pago-business");

        servicio.checkout(ID_USUARIO,
                new CheckoutRequest(PlanSuscripcion.STARTER, PeriodoFacturacion.MENSUAL));
        var r = servicio.checkout(ID_USUARIO,
                new CheckoutRequest(PlanSuscripcion.BUSINESS, PeriodoFacturacion.MENSUAL));

        assertThat(r.urlPago()).isEqualTo("/pago-business");
        verify(pasarela, times(2)).iniciarCheckout(any(), any(), any(), any());
    }

    // ---- confirmación simulada (Etapa 2) ----

    @Test
    @DisplayName("confirmarSimulado: en modo mp no existe → 404")
    void confirmarSimulado_modoMp404() {
        when(pasarela.modo()).thenReturn("mp");

        assertThatThrownBy(() -> servicio.confirmarSimulado(ID_USUARIO,
                new ConfirmacionSimuladaRequest(
                        PlanSuscripcion.STARTER, PeriodoFacturacion.MENSUAL, EstadoPago.APROBADO)))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("confirmarSimulado: resultado distinto de APROBADO/RECHAZADO → 400")
    void confirmarSimulado_resultadoInvalido() {
        assertThatThrownBy(() -> servicio.confirmarSimulado(ID_USUARIO,
                new ConfirmacionSimuladaRequest(
                        PlanSuscripcion.STARTER, PeriodoFacturacion.MENSUAL, EstadoPago.PENDIENTE)))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("confirmarSimulado APROBADO: primera activación — ciclo desde hoy, pago y plan efectivo")
    void confirmarSimulado_primeraActivacion() {
        var dueno = dueno(PlanSuscripcion.DEMO);
        when(usuarioRepository.findById(ID_USUARIO)).thenReturn(Optional.of(dueno));
        when(suscripcionRepository.findByIdUsuario(ID_USUARIO)).thenReturn(Optional.empty());
        when(usuarioRepository.countByUsuarioDuenoIdAndActivoComoEmpleadoTrue(ID_USUARIO))
                .thenReturn(0L);
        when(suscripcionRepository.save(any(Suscripcion.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        var r = servicio.confirmarSimulado(ID_USUARIO, new ConfirmacionSimuladaRequest(
                PlanSuscripcion.BUSINESS, PeriodoFacturacion.ANUAL, EstadoPago.APROBADO));

        assertThat(r.estado()).isEqualTo(EstadoSuscripcion.ACTIVA);
        assertThat(r.planEfectivo()).isEqualTo(PlanSuscripcion.BUSINESS);
        assertThat(r.precioArs()).isEqualByComparingTo("1435500"); // anual = mensual x 10
        assertThat(dueno.getPlanSuscripcion()).isEqualTo(PlanSuscripcion.BUSINESS);
        verify(pagoRepository).save(pagoCaptor.capture());
        assertThat(pagoCaptor.getValue().getEstado()).isEqualTo(EstadoPago.APROBADO);
        assertThat(pagoCaptor.getValue().getDescripcion())
                .isEqualTo("REFORMA - Plan BUSINESS (Anual)");
        verify(auditoriaService).registrar(eventoCaptor.capture());
        assertThat(eventoCaptor.getValue().accion()).isEqualTo(AccionAuditoria.SUSCRIPCION_ACTIVADA);
    }

    @Test
    @DisplayName("confirmarSimulado APROBADO: upgrade entre pagos pisa la fila y audita PLAN_CAMBIADO")
    void confirmarSimulado_upgradeSobreActiva() {
        var dueno = dueno(PlanSuscripcion.STARTER);
        var s = suscripcionActivaSinPendiente();
        s.setPlan(PlanSuscripcion.STARTER);
        s.setPrecioArs(new BigDecimal("50750.00"));
        when(usuarioRepository.findById(ID_USUARIO)).thenReturn(Optional.of(dueno));
        when(suscripcionRepository.findByIdUsuario(ID_USUARIO)).thenReturn(Optional.of(s));
        when(usuarioRepository.countByUsuarioDuenoIdAndActivoComoEmpleadoTrue(ID_USUARIO))
                .thenReturn(2L);
        when(suscripcionRepository.save(any(Suscripcion.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        var r = servicio.confirmarSimulado(ID_USUARIO, new ConfirmacionSimuladaRequest(
                PlanSuscripcion.BUSINESS, PeriodoFacturacion.MENSUAL, EstadoPago.APROBADO));

        assertThat(r.plan()).isEqualTo(PlanSuscripcion.BUSINESS);
        assertThat(s.getPrecioArs()).isEqualByComparingTo("143550");
        // RD-P4: ciclo nuevo desde hoy, sin prorrateo.
        assertThat(s.getFechaInicio()).isAfter(Instant.parse("2026-07-01T00:00:00Z"));
        assertThat(s.getFechaFinPeriodo())
                .isEqualTo(PeriodoFacturacion.MENSUAL.finDeCiclo(s.getFechaInicio()));
        assertThat(dueno.getPlanSuscripcion()).isEqualTo(PlanSuscripcion.BUSINESS);
        verify(auditoriaService).registrar(eventoCaptor.capture());
        assertThat(eventoCaptor.getValue().accion()).isEqualTo(AccionAuditoria.PLAN_CAMBIADO);
    }

    @Test
    @DisplayName("confirmarSimulado APROBADO: re-valida empleados (carrera checkout→confirmación)")
    void confirmarSimulado_reValidaEmpleados() {
        var dueno = dueno(PlanSuscripcion.BUSINESS);
        when(usuarioRepository.findById(ID_USUARIO)).thenReturn(Optional.of(dueno));
        when(suscripcionRepository.findByIdUsuario(ID_USUARIO))
                .thenReturn(Optional.of(suscripcionActivaSinPendiente()));
        when(usuarioRepository.countByUsuarioDuenoIdAndActivoComoEmpleadoTrue(ID_USUARIO))
                .thenReturn(4L);

        assertThatThrownBy(() -> servicio.confirmarSimulado(ID_USUARIO,
                new ConfirmacionSimuladaRequest(
                        PlanSuscripcion.STARTER, PeriodoFacturacion.MENSUAL, EstadoPago.APROBADO)))
                .isInstanceOf(EmpleadosSobreLimiteException.class);
        assertThat(dueno.getPlanSuscripcion()).isEqualTo(PlanSuscripcion.BUSINESS);
    }

    @Test
    @DisplayName("confirmarSimulado RECHAZADO sobre ACTIVA: registra el pago pero NO toca ultimo_cobro")
    void confirmarSimulado_rechazoNoTocaUltimoCobroDeActiva() {
        var dueno = dueno(PlanSuscripcion.STARTER);
        var s = suscripcionActivaSinPendiente();
        s.setPlan(PlanSuscripcion.STARTER);
        var cobroOriginal = s.getUltimoCobroFecha();
        when(usuarioRepository.findById(ID_USUARIO)).thenReturn(Optional.of(dueno));
        when(suscripcionRepository.findByIdUsuario(ID_USUARIO)).thenReturn(Optional.of(s));

        var r = servicio.confirmarSimulado(ID_USUARIO, new ConfirmacionSimuladaRequest(
                PlanSuscripcion.BUSINESS, PeriodoFacturacion.MENSUAL, EstadoPago.RECHAZADO));

        assertThat(r.estado()).isEqualTo(EstadoSuscripcion.ACTIVA);
        // El intento fallido de upgrade no cuenta contra la gracia del plan vigente (RD-P8).
        assertThat(s.getUltimoCobroEstado()).isEqualTo(EstadoPago.APROBADO);
        assertThat(s.getUltimoCobroFecha()).isEqualTo(cobroOriginal);
        assertThat(dueno.getPlanSuscripcion()).isEqualTo(PlanSuscripcion.STARTER);
        verify(pagoRepository).save(pagoCaptor.capture());
        assertThat(pagoCaptor.getValue().getEstado()).isEqualTo(EstadoPago.RECHAZADO);
    }

    @Test
    @DisplayName("confirmarSimulado RECHAZADO sin fila previa: crea PENDIENTE_PAGO con el rechazo marcado")
    void confirmarSimulado_rechazoPrimeraContratacion() {
        var dueno = dueno(PlanSuscripcion.DEMO);
        when(usuarioRepository.findById(ID_USUARIO)).thenReturn(Optional.of(dueno));
        when(suscripcionRepository.findByIdUsuario(ID_USUARIO)).thenReturn(Optional.empty());
        when(suscripcionRepository.save(any(Suscripcion.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        var r = servicio.confirmarSimulado(ID_USUARIO, new ConfirmacionSimuladaRequest(
                PlanSuscripcion.STARTER, PeriodoFacturacion.MENSUAL, EstadoPago.RECHAZADO));

        assertThat(r.estado()).isEqualTo(EstadoSuscripcion.PENDIENTE_PAGO);
        assertThat(r.ultimoCobroEstado()).isEqualTo(EstadoPago.RECHAZADO);
        assertThat(dueno.getPlanSuscripcion()).isEqualTo(PlanSuscripcion.DEMO);
    }

    // ---- cancelar / reactivar (Etapa 2) ----

    @Test
    @DisplayName("cancelar: detiene el cobro YA y programa la caída a DEMO a fin de ciclo (RD-P7)")
    void cancelar_ok() {
        var dueno = dueno(PlanSuscripcion.BUSINESS);
        var s = suscripcionActivaSinPendiente();
        when(usuarioRepository.findById(ID_USUARIO)).thenReturn(Optional.of(dueno));
        when(suscripcionRepository.findByIdUsuario(ID_USUARIO)).thenReturn(Optional.of(s));

        var r = servicio.cancelar(ID_USUARIO);

        assertThat(r.estado()).isEqualTo(EstadoSuscripcion.CANCELADA);
        assertThat(s.getPlanPendiente()).isEqualTo(PlanSuscripcion.DEMO);
        // El plan efectivo sigue vigente hasta el fin del ciclo pagado.
        assertThat(dueno.getPlanSuscripcion()).isEqualTo(PlanSuscripcion.BUSINESS);
        verify(pasarela).cancelarCobroRecurrente(s);
        verify(auditoriaService).registrar(eventoCaptor.capture());
        assertThat(eventoCaptor.getValue().accion()).isEqualTo(AccionAuditoria.SUSCRIPCION_CANCELADA);
    }

    @Test
    @DisplayName("cancelar: sin suscripción activa → 409")
    void cancelar_sinActiva() {
        when(usuarioRepository.findById(ID_USUARIO))
                .thenReturn(Optional.of(dueno(PlanSuscripcion.DEMO)));
        when(suscripcionRepository.findByIdUsuario(ID_USUARIO)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.cancelar(ID_USUARIO))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    @DisplayName("reactivar: revierte una cancelación — vuelve a ACTIVA y reanuda el cobro")
    void reactivar_cancelacion() {
        var dueno = dueno(PlanSuscripcion.BUSINESS);
        var s = suscripcionActivaSinPendiente();
        s.setEstado(EstadoSuscripcion.CANCELADA);
        s.setPlanPendiente(PlanSuscripcion.DEMO);
        when(usuarioRepository.findById(ID_USUARIO)).thenReturn(Optional.of(dueno));
        when(suscripcionRepository.findByIdUsuario(ID_USUARIO)).thenReturn(Optional.of(s));

        var r = servicio.reactivar(ID_USUARIO);

        assertThat(r.estado()).isEqualTo(EstadoSuscripcion.ACTIVA);
        assertThat(s.getPlanPendiente()).isNull();
        verify(pasarela).reanudarCobroRecurrente(s);
        verify(auditoriaService).registrar(eventoCaptor.capture());
        assertThat(eventoCaptor.getValue().accion()).isEqualTo(AccionAuditoria.SUSCRIPCION_REACTIVADA);
    }

    @Test
    @DisplayName("reactivar: des-programa un downgrade sin tocar el cobro recurrente")
    void reactivar_downgradeProgramado() {
        var dueno = dueno(PlanSuscripcion.BUSINESS);
        var s = suscripcionActiva(); // ACTIVA con planPendiente = STARTER
        when(usuarioRepository.findById(ID_USUARIO)).thenReturn(Optional.of(dueno));
        when(suscripcionRepository.findByIdUsuario(ID_USUARIO)).thenReturn(Optional.of(s));

        var r = servicio.reactivar(ID_USUARIO);

        assertThat(r.estado()).isEqualTo(EstadoSuscripcion.ACTIVA);
        assertThat(s.getPlanPendiente()).isNull();
        assertThat(s.getPeriodoPendiente()).isNull();
        verify(pasarela, never()).reanudarCobroRecurrente(any());
    }

    @Test
    @DisplayName("reactivar: sin cambio programado → 409")
    void reactivar_sinPendiente() {
        when(usuarioRepository.findById(ID_USUARIO))
                .thenReturn(Optional.of(dueno(PlanSuscripcion.BUSINESS)));
        when(suscripcionRepository.findByIdUsuario(ID_USUARIO))
                .thenReturn(Optional.of(suscripcionActivaSinPendiente()));

        assertThatThrownBy(() -> servicio.reactivar(ID_USUARIO))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT);
    }

    private Suscripcion suscripcionActivaSinPendiente() {
        var s = suscripcionActiva();
        s.setPlanPendiente(null);
        s.setPeriodoPendiente(null);
        return s;
    }

    // ---- activación desde el webhook de MP (Etapa 4, RD-P9) ----

    @Test
    @DisplayName("activarDesdePasarela: guarda el preapproval id y NO inserta pago local")
    void activarDesdePasarela_primeraActivacion() {
        var dueno = dueno(PlanSuscripcion.DEMO);
        when(usuarioRepository.findById(ID_USUARIO)).thenReturn(Optional.of(dueno));
        when(suscripcionRepository.findByIdUsuario(ID_USUARIO)).thenReturn(Optional.empty());
        when(usuarioRepository.countByUsuarioDuenoIdAndActivoComoEmpleadoTrue(ID_USUARIO))
                .thenReturn(0L);
        when(suscripcionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var r = servicio.activarDesdePasarela(ID_USUARIO, PlanSuscripcion.STARTER,
                PeriodoFacturacion.MENSUAL, new BigDecimal("50750"), "pre_123");

        assertThat(r.estado()).isEqualTo(EstadoSuscripcion.ACTIVA);
        verify(suscripcionRepository).save(suscripcionCaptor.capture());
        var s = suscripcionCaptor.getValue();
        assertThat(s.getMpPreapprovalId()).isEqualTo("pre_123");
        assertThat(s.getPrecioArs()).isEqualByComparingTo("50750");
        assertThat(s.getUltimoCobroEstado()).isEqualTo(EstadoPago.APROBADO);
        // El cobro real llega como evento payment con su propio id idempotente (RD-P9).
        verify(pagoRepository, never()).save(any());
        assertThat(dueno.getPlanSuscripcion()).isEqualTo(PlanSuscripcion.STARTER);
        verify(auditoriaService).registrar(eventoCaptor.capture());
        assertThat(eventoCaptor.getValue().accion()).isEqualTo(AccionAuditoria.SUSCRIPCION_ACTIVADA);
    }

    @Test
    @DisplayName("activarDesdePasarela: upgrade pisa el preapproval id y usa el monto del preapproval")
    void activarDesdePasarela_upgradeReemplazaPreapproval() {
        var dueno = dueno(PlanSuscripcion.BUSINESS);
        var existente = suscripcionActivaSinPendiente();
        existente.setMpPreapprovalId("pre_viejo");
        when(usuarioRepository.findById(ID_USUARIO)).thenReturn(Optional.of(dueno));
        when(suscripcionRepository.findByIdUsuario(ID_USUARIO)).thenReturn(Optional.of(existente));
        when(usuarioRepository.countByUsuarioDuenoIdAndActivoComoEmpleadoTrue(ID_USUARIO))
                .thenReturn(0L);
        when(suscripcionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        servicio.activarDesdePasarela(ID_USUARIO, PlanSuscripcion.ENTERPRISE,
                PeriodoFacturacion.ANUAL, new BigDecimal("3320500"), "pre_nuevo");

        assertThat(existente.getMpPreapprovalId()).isEqualTo("pre_nuevo");
        assertThat(existente.getPlan()).isEqualTo(PlanSuscripcion.ENTERPRISE);
        assertThat(existente.getPeriodo()).isEqualTo(PeriodoFacturacion.ANUAL);
        assertThat(existente.getPrecioArs()).isEqualByComparingTo("3320500");
        assertThat(dueno.getPlanSuscripcion()).isEqualTo(PlanSuscripcion.ENTERPRISE);
        verify(auditoriaService).registrar(eventoCaptor.capture());
        assertThat(eventoCaptor.getValue().accion()).isEqualTo(AccionAuditoria.PLAN_CAMBIADO);
    }

    @Test
    @DisplayName("activarDesdePasarela: sin monto del preapproval cae al precio de lista")
    void activarDesdePasarela_sinMontoUsaPrecioDeLista() {
        var dueno = dueno(PlanSuscripcion.DEMO);
        when(usuarioRepository.findById(ID_USUARIO)).thenReturn(Optional.of(dueno));
        when(suscripcionRepository.findByIdUsuario(ID_USUARIO)).thenReturn(Optional.empty());
        when(usuarioRepository.countByUsuarioDuenoIdAndActivoComoEmpleadoTrue(ID_USUARIO))
                .thenReturn(0L);
        when(suscripcionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        servicio.activarDesdePasarela(ID_USUARIO, PlanSuscripcion.BUSINESS,
                PeriodoFacturacion.MENSUAL, null, "pre_123");

        verify(suscripcionRepository).save(suscripcionCaptor.capture());
        assertThat(suscripcionCaptor.getValue().getPrecioArs()).isEqualByComparingTo("143550");
    }
}
