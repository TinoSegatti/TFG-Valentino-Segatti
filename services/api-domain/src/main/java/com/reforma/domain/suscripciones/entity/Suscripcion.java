package com.reforma.domain.suscripciones.entity;

import com.reforma.domain.common.domain.PlanSuscripcion;
import com.reforma.domain.suscripciones.domain.EstadoPago;
import com.reforma.domain.suscripciones.domain.EstadoSuscripcion;
import com.reforma.domain.suscripciones.domain.PeriodoFacturacion;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Suscripción comercial 1:1 con el usuario DUEÑO ({@code t_suscripcion}, V015). Es la máquina
 * de estados del módulo de pagos (RD-P3): al activarse/expirar escribe
 * {@code t_usuarios.plan_suscripcion}, que sigue siendo lo que lee {@code PlanService}.
 * Una cuenta sin fila acá es DEMO implícito (o plan asignado a mano en BD).
 */
@Entity
@Table(name = "t_suscripcion")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Suscripcion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "id_usuario", nullable = false, unique = true, length = 32)
    private String idUsuario;

    /** Plan contratado (nunca DEMO: DEMO no se contrata, es la ausencia o caída de suscripción). */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PlanSuscripcion plan;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private PeriodoFacturacion periodo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EstadoSuscripcion estado;

    /** Snapshot del precio al contratar (RD-P12): cambios de lista no tocan suscripciones vivas. */
    @Column(name = "precio_ars", nullable = false, precision = 12, scale = 2)
    private BigDecimal precioArs;

    @Column(name = "fecha_inicio", nullable = false)
    private Instant fechaInicio;

    /** "Pagado hasta"; NULL mientras PENDIENTE_PAGO. */
    @Column(name = "fecha_fin_periodo")
    private Instant fechaFinPeriodo;

    /** Downgrade programado a fin de ciclo (RD-P5/P7, incluye DEMO); NULL = ninguno. */
    @Enumerated(EnumType.STRING)
    @Column(name = "plan_pendiente", length = 20)
    private PlanSuscripcion planPendiente;

    @Enumerated(EnumType.STRING)
    @Column(name = "periodo_pendiente", length = 10)
    private PeriodoFacturacion periodoPendiente;

    /** Id del preapproval en Mercado Pago; NULL en modo simulado. */
    @Column(name = "mp_preapproval_id", unique = true, length = 64)
    private String mpPreapprovalId;

    /** Resultado del último cobro notificado (para la gracia por rechazo, RD-P8). */
    @Enumerated(EnumType.STRING)
    @Column(name = "ultimo_cobro_estado", length = 20)
    private EstadoPago ultimoCobroEstado;

    @Column(name = "ultimo_cobro_fecha")
    private Instant ultimoCobroFecha;

    @Column(name = "fecha_creacion", nullable = false)
    private Instant fechaCreacion;

    @Column(name = "fecha_actualizacion", nullable = false)
    private Instant fechaActualizacion;
}
