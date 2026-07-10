package com.reforma.domain.suscripciones.entity;

import com.reforma.domain.suscripciones.domain.EstadoPago;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
 * Cobro de una suscripción ({@code t_pago}, V015). Historial comercial: solo se inserta.
 * {@code mpPaymentId} es la clave de idempotencia del webhook (RD-P9): un mismo pago
 * notificado dos veces no genera dos filas. Jamás guarda datos de tarjeta.
 */
@Entity
@Table(name = "t_pago")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Pago {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "id_suscripcion", nullable = false)
    private Long idSuscripcion;

    /** Id del pago en Mercado Pago; NULL en modo simulado. */
    @Column(name = "mp_payment_id", unique = true, length = 64)
    private String mpPaymentId;

    @Column(name = "monto_ars", nullable = false, precision = 12, scale = 2)
    private BigDecimal montoArs;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EstadoPago estado;

    /** P. ej. "REFORMA - Plan BUSINESS (Mensual)". */
    @Column(length = 200)
    private String descripcion;

    @Column(name = "fecha_pago", nullable = false)
    private Instant fechaPago;
}
