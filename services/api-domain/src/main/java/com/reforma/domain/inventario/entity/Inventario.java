package com.reforma.domain.inventario.entity;

import com.reforma.domain.granjas.entity.Granja;
import com.reforma.domain.materiasprimas.entity.MateriaPrima;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "t_inventario")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Inventario {

    @Id
    @Column(length = 32)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_granja", nullable = false)
    private Granja granja;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_materia_prima", nullable = false)
    private MateriaPrima materiaPrima;

    @Column(name = "cantidad_acumulada", nullable = false)
    private Double cantidadAcumulada;

    @Column(name = "cantidad_sistema", nullable = false)
    private Double cantidadSistema;

    @Column(name = "cantidad_real", nullable = false)
    private Double cantidadReal;

    @Column(nullable = false)
    private Double merma;

    @Column(name = "precio_almacen", nullable = false)
    private Double precioAlmacen;

    @Column(name = "valor_stock", nullable = false)
    private Double valorStock;

    @Version
    @Column(nullable = false)
    private Integer version;

    @Column(name = "fecha_ultima_actualizacion", nullable = false)
    private Instant fechaUltimaActualizacion;

    @Column(columnDefinition = "TEXT")
    private String observaciones;
}
