package com.reforma.domain.anomalias.entity;

import com.reforma.domain.anomalias.domain.ClasificacionAnomalia;
import com.reforma.domain.compras.entity.CompraCabecera;
import com.reforma.domain.materiasprimas.entity.MateriaPrima;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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

/** Anomalía de precio detectada al registrar una compra (RF-IA-ANOM-004). Mapea {@code t_ia_anomalia_precio}. */
@Entity
@Table(name = "t_ia_anomalia_precio")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AnomaliaPrecio {

    @Id
    @Column(length = 32)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_compra")
    private CompraCabecera compra;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_materia_prima", nullable = false)
    private MateriaPrima materiaPrima;

    @Column(name = "precio_ingresado", nullable = false)
    private double precioIngresado;

    @Column(name = "precio_promedio_historico")
    private Double precioPromedioHistorico;

    @Column(name = "z_score")
    private Double zScore;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ClasificacionAnomalia clasificacion;

    /** true = confirmada por el usuario; false = rechazada; null = ignorada / no requirió confirmación. */
    @Column(name = "usuario_confirmo")
    private Boolean usuarioConfirmo;

    @Column(name = "detectado_en", nullable = false)
    private Instant detectadoEn;
}
