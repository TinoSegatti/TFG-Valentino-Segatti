package com.reforma.domain.suscripciones.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.reforma.domain.auth.jwt.JwtUserPrincipal;
import com.reforma.domain.auth.jwt.TokenJwtServicio;
import com.reforma.domain.common.domain.PlanSuscripcion;
import com.reforma.domain.common.domain.TipoUsuario;
import com.reforma.domain.suscripciones.domain.EstadoPago;
import com.reforma.domain.suscripciones.domain.EstadoSuscripcion;
import com.reforma.domain.suscripciones.domain.PeriodoFacturacion;
import com.reforma.domain.suscripciones.dto.CambioPlanImpactoResponse;
import com.reforma.domain.suscripciones.dto.CheckoutRequest;
import com.reforma.domain.suscripciones.dto.CheckoutResponse;
import com.reforma.domain.suscripciones.dto.ConfirmacionSimuladaRequest;
import com.reforma.domain.suscripciones.dto.PaginaPagos;
import com.reforma.domain.suscripciones.dto.PagoResponse;
import com.reforma.domain.suscripciones.dto.PlanCatalogoResponse;
import com.reforma.domain.suscripciones.dto.SuscripcionResponse;
import com.reforma.domain.suscripciones.service.ImpactoCambioPlanService;
import com.reforma.domain.suscripciones.service.SuscripcionService;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = SuscripcionRestController.class)
@AutoConfigureMockMvc(addFilters = false)
class SuscripcionRestControllerTest {

    private static final String ID_USUARIO = "u_demo";
    private static final String EMAIL = "demo@reforma.local";

    @Autowired private MockMvc mockMvc;

    @MockBean private SuscripcionService suscripcionService;
    @MockBean private ImpactoCambioPlanService impactoCambioPlanService;
    @MockBean private TokenJwtServicio tokenJwtServicio;

    @BeforeEach
    void autenticarUsuario() {
        var principal = new JwtUserPrincipal(
                ID_USUARIO, EMAIL, TipoUsuario.CLIENTE, PlanSuscripcion.BUSINESS, true,
                false, null, null);
        var auth = new UsernamePasswordAuthenticationToken(
                principal, null, principal.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    @AfterEach
    void limpiarContexto() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void planes_200_devuelveCatalogoConLimitesNullComoIlimitado() throws Exception {
        var starter = new PlanCatalogoResponse(
                PlanSuscripcion.STARTER,
                new BigDecimal("50750"),
                new BigDecimal("507500"),
                new PlanCatalogoResponse.LimitesPlan(2, 2, 30, 15, 20, 10, 50, 10),
                false);
        var enterprise = new PlanCatalogoResponse(
                PlanSuscripcion.ENTERPRISE,
                new BigDecimal("332050"),
                new BigDecimal("3320500"),
                new PlanCatalogoResponse.LimitesPlan(null, null, null, null, null, null, null, null),
                true);
        when(suscripcionService.catalogo()).thenReturn(List.of(starter, enterprise));

        mockMvc.perform(get("/api/suscripcion/planes"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].plan").value("STARTER"))
                .andExpect(jsonPath("$[0].precioMensualArs").value(50750))
                .andExpect(jsonPath("$[0].limites.granjas").value(2))
                .andExpect(jsonPath("$[1].limites.granjas").doesNotExist())
                .andExpect(jsonPath("$[1].prediccionStock").value(true));
    }

    @Test
    void miSuscripcion_200_implicitaParaCuentaSinContratar() throws Exception {
        when(suscripcionService.obtenerMiSuscripcion(ID_USUARIO))
                .thenReturn(SuscripcionResponse.implicita(PlanSuscripcion.DEMO));

        mockMvc.perform(get("/api/suscripcion"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.planEfectivo").value("DEMO"))
                .andExpect(jsonPath("$.gestionada").value(false))
                .andExpect(jsonPath("$.estado").doesNotExist());
    }

    @Test
    void miSuscripcion_200_gestionadaConDowngradeProgramado() throws Exception {
        when(suscripcionService.obtenerMiSuscripcion(ID_USUARIO))
                .thenReturn(new SuscripcionResponse(
                        PlanSuscripcion.BUSINESS,
                        true,
                        PlanSuscripcion.BUSINESS,
                        PeriodoFacturacion.MENSUAL,
                        EstadoSuscripcion.ACTIVA,
                        new BigDecimal("143550.00"),
                        Instant.parse("2026-07-01T00:00:00Z"),
                        Instant.parse("2026-08-01T00:00:00Z"),
                        PlanSuscripcion.STARTER,
                        PeriodoFacturacion.MENSUAL,
                        EstadoPago.APROBADO,
                        Instant.parse("2026-07-01T00:05:00Z")));

        mockMvc.perform(get("/api/suscripcion"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.gestionada").value(true))
                .andExpect(jsonPath("$.estado").value("ACTIVA"))
                .andExpect(jsonPath("$.planPendiente").value("STARTER"))
                .andExpect(jsonPath("$.ultimoCobroEstado").value("APROBADO"));
    }

    @Test
    void pagos_200_pasaPaginacionAlServicio() throws Exception {
        var pagina = new PaginaPagos(
                List.of(new PagoResponse(
                        1L,
                        new BigDecimal("143550.00"),
                        EstadoPago.APROBADO,
                        "REFORMA - Plan BUSINESS (Mensual)",
                        Instant.parse("2026-07-01T00:05:00Z"))),
                1, 5, 6, 2);
        when(suscripcionService.listarPagos(ID_USUARIO, 1, 5)).thenReturn(pagina);

        mockMvc.perform(get("/api/suscripcion/pagos")
                        .param("pagina", "1")
                        .param("tamano", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contenido[0].estado").value("APROBADO"))
                .andExpect(jsonPath("$.pagina").value(1))
                .andExpect(jsonPath("$.totalElementos").value(6));
    }

    // ---- Etapa 2: checkout / confirmación / cancelar / reactivar / impacto ----

    @Test
    void cambioImpacto_200_devuelveBloqueantesYAdvertencias() throws Exception {
        when(impactoCambioPlanService.impacto(ID_USUARIO, PlanSuscripcion.STARTER))
                .thenReturn(new CambioPlanImpactoResponse(
                        PlanSuscripcion.BUSINESS,
                        PlanSuscripcion.STARTER,
                        CambioPlanImpactoResponse.TipoCambio.DOWNGRADE,
                        Instant.parse("2026-08-01T00:00:00Z"),
                        List.of(new CambioPlanImpactoResponse.ImpactoRecurso(
                                "empleados", null, 5, 2, 3)),
                        List.of(new CambioPlanImpactoResponse.ImpactoRecurso(
                                "materiasPrimas", "Granja Sur", 40, 30, 10))));

        mockMvc.perform(get("/api/suscripcion/cambio-impacto").param("plan", "STARTER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tipoCambio").value("DOWNGRADE"))
                .andExpect(jsonPath("$.bloqueantes[0].recurso").value("empleados"))
                .andExpect(jsonPath("$.bloqueantes[0].excedente").value(3))
                .andExpect(jsonPath("$.advertencias[0].granja").value("Granja Sur"));
    }

    @Test
    void checkout_200_downgradeProgramadoSinPago() throws Exception {
        when(suscripcionService.checkout(eq(ID_USUARIO), any(CheckoutRequest.class)))
                .thenReturn(new CheckoutResponse("simulado", false, null,
                        SuscripcionResponse.implicita(PlanSuscripcion.BUSINESS)));

        mockMvc.perform(post("/api/suscripcion/checkout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"plan\":\"STARTER\",\"periodo\":\"MENSUAL\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.modo").value("simulado"))
                .andExpect(jsonPath("$.requierePago").value(false))
                .andExpect(jsonPath("$.urlPago").doesNotExist());
    }

    @Test
    void checkout_400_sinPlanEnElBody() throws Exception {
        mockMvc.perform(post("/api/suscripcion/checkout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"periodo\":\"MENSUAL\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void confirmarSimulado_200_devuelveLaSuscripcionResultante() throws Exception {
        when(suscripcionService.confirmarSimulado(
                        eq(ID_USUARIO), any(ConfirmacionSimuladaRequest.class)))
                .thenReturn(new SuscripcionResponse(
                        PlanSuscripcion.STARTER,
                        true,
                        PlanSuscripcion.STARTER,
                        PeriodoFacturacion.MENSUAL,
                        EstadoSuscripcion.ACTIVA,
                        new BigDecimal("50750"),
                        Instant.parse("2026-07-06T12:00:00Z"),
                        Instant.parse("2026-08-06T12:00:00Z"),
                        null,
                        null,
                        EstadoPago.APROBADO,
                        Instant.parse("2026-07-06T12:00:00Z")));

        mockMvc.perform(post("/api/suscripcion/confirmar-simulado")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"plan\":\"STARTER\",\"periodo\":\"MENSUAL\",\"resultado\":\"APROBADO\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("ACTIVA"))
                .andExpect(jsonPath("$.planEfectivo").value("STARTER"))
                .andExpect(jsonPath("$.ultimoCobroEstado").value("APROBADO"));
    }

    @Test
    void cancelar_200_quedaCanceladaConCaidaADemoProgramada() throws Exception {
        when(suscripcionService.cancelar(ID_USUARIO))
                .thenReturn(new SuscripcionResponse(
                        PlanSuscripcion.BUSINESS,
                        true,
                        PlanSuscripcion.BUSINESS,
                        PeriodoFacturacion.MENSUAL,
                        EstadoSuscripcion.CANCELADA,
                        new BigDecimal("143550"),
                        Instant.parse("2026-07-01T00:00:00Z"),
                        Instant.parse("2026-08-01T00:00:00Z"),
                        PlanSuscripcion.DEMO,
                        null,
                        EstadoPago.APROBADO,
                        Instant.parse("2026-07-01T00:05:00Z")));

        mockMvc.perform(post("/api/suscripcion/cancelar"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("CANCELADA"))
                .andExpect(jsonPath("$.planPendiente").value("DEMO"));
    }

    @Test
    void reactivar_200_vuelveActivaSinPendiente() throws Exception {
        when(suscripcionService.reactivar(ID_USUARIO))
                .thenReturn(new SuscripcionResponse(
                        PlanSuscripcion.BUSINESS,
                        true,
                        PlanSuscripcion.BUSINESS,
                        PeriodoFacturacion.MENSUAL,
                        EstadoSuscripcion.ACTIVA,
                        new BigDecimal("143550"),
                        Instant.parse("2026-07-01T00:00:00Z"),
                        Instant.parse("2026-08-01T00:00:00Z"),
                        null,
                        null,
                        EstadoPago.APROBADO,
                        Instant.parse("2026-07-01T00:05:00Z")));

        mockMvc.perform(post("/api/suscripcion/reactivar"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("ACTIVA"))
                .andExpect(jsonPath("$.planPendiente").doesNotExist());
    }
}
