package com.reforma.domain.fabricaciones.entity;

import com.reforma.domain.materiasprimas.entity.MateriaPrima;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "t_detalle_fabricacion")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FabricacionDetalle {

    @Id
    @Column(length = 32)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_fabricacion", nullable = false)
    private Fabricacion fabricacion;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_materia_prima", nullable = false)
    private MateriaPrima materiaPrima;

    @Column(name = "cantidad_usada", nullable = false)
    private Double cantidadUsada;

    @Column(name = "precio_unitario", nullable = false)
    private Double precioUnitario;

    @Column(name = "costo_parcial", nullable = false)
    private Double costoParcial;

    @Column(name = "codigo_mp_snapshot", length = 50)
    private String codigoMpSnapshot;

    @Column(name = "nombre_mp_snapshot", length = 200)
    private String nombreMpSnapshot;
}
