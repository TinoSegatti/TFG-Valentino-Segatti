package com.reforma.domain.suscripciones.service;

import com.reforma.domain.common.domain.PlanSuscripcion;
import com.reforma.domain.suscripciones.domain.PeriodoFacturacion;
import java.math.BigDecimal;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Precios de lista de los planes en ARS (RD-P12). Viven en configuración
 * ({@code reforma.pagos.precios.*}, override por env) para poder ajustarlos ante
 * devaluación sin release; el precio vigente al contratar se snapshotea en
 * {@code t_suscripcion.precio_ars}, así los cambios de lista no tocan clientes vivos.
 * Anual = mensual × {@code factor-anual} (default 10: dos meses gratis).
 */
@Service
public class PrecioPlanService {

    private final BigDecimal starterMensual;
    private final BigDecimal businessMensual;
    private final BigDecimal enterpriseMensual;
    private final int factorAnual;

    public PrecioPlanService(
            @Value("${reforma.pagos.precios.starter-mensual:50750}") BigDecimal starterMensual,
            @Value("${reforma.pagos.precios.business-mensual:143550}") BigDecimal businessMensual,
            @Value("${reforma.pagos.precios.enterprise-mensual:332050}") BigDecimal enterpriseMensual,
            @Value("${reforma.pagos.precios.factor-anual:10}") int factorAnual) {
        this.starterMensual = starterMensual;
        this.businessMensual = businessMensual;
        this.enterpriseMensual = enterpriseMensual;
        this.factorAnual = factorAnual;
    }

    public BigDecimal precioMensual(PlanSuscripcion plan) {
        return switch (plan) {
            case DEMO -> BigDecimal.ZERO;
            case STARTER -> starterMensual;
            case BUSINESS -> businessMensual;
            case ENTERPRISE -> enterpriseMensual;
        };
    }

    public BigDecimal precioAnual(PlanSuscripcion plan) {
        return precioMensual(plan).multiply(BigDecimal.valueOf(factorAnual));
    }

    /** Precio de lista para el período dado (lo que se snapshotea al contratar, RD-P12). */
    public BigDecimal precio(PlanSuscripcion plan, PeriodoFacturacion periodo) {
        return periodo == PeriodoFacturacion.MENSUAL ? precioMensual(plan) : precioAnual(plan);
    }
}
