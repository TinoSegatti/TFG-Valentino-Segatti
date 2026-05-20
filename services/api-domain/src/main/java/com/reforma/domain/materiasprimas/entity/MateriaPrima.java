package com.reforma.domain.materiasprimas.entity;

import com.reforma.domain.granjas.entity.Granja;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(
        name = "t_materia_prima",
        uniqueConstraints =
                @UniqueConstraint(
                        name = "uq_mp_granja_codigo",
                        columnNames = {"id_granja", "codigo_materia_prima"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MateriaPrima {

    @Id
    @Column(length = 32)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_granja", nullable = false)
    private Granja granja;

    @Column(name = "codigo_materia_prima", nullable = false, length = 50)
    private String codigoMateriaPrima;

    @Column(name = "nombre_materia_prima", nullable = false, length = 200)
    private String nombreMateriaPrima;

    @Column(name = "precio_por_kilo", nullable = false)
    private Double precioPorKilo;

    @Column(nullable = false)
    private Boolean activa;

    @Column(name = "fecha_creacion", nullable = false)
    private Instant fechaCreacion;

    @Column(name = "fecha_ultima_actualizacion", nullable = false)
    private Instant fechaUltimaActualizacion;
}
