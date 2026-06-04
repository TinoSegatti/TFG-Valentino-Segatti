package com.reforma.domain.materiasprimas.entity;

import com.reforma.domain.compras.entity.CompraCabecera;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Historial de precios por MP; {@code fechaReferencia} alinea la serie temporal con la fecha de negocio (p. ej. factura). */
@Entity
@Table(name = "t_registro_precio")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RegistroPrecio {

    @Id
    @Column(length = 32)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_materia_prima", nullable = false)
    private MateriaPrima materiaPrima;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_compra")
    private CompraCabecera compra;

    @Column(name = "precio_anterior", nullable = false)
    private Double precioAnterior;

    @Column(name = "precio_nuevo", nullable = false)
    private Double precioNuevo;

    @Column(name = "diferencia_porcentual", nullable = false)
    private Double diferenciaPorcentual;

    /** Fecha de negocio (p. ej. fecha de la factura de compra). */
    @Column(name = "fecha_referencia", nullable = false)
    private Instant fechaReferencia;

    @Column(name = "fecha_cambio", nullable = false)
    private Instant fechaCambio;

    @Column(length = 30, nullable = false)
    private String origen;

    @Column(columnDefinition = "TEXT")
    private String motivo;
}
