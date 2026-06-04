package com.reforma.domain.formulas.entity;

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
@Table(name = "t_formula_detalle")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FormulaDetalle {

    @Id
    @Column(length = 32)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_formula", nullable = false)
    private FormulaCabecera formula;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_materia_prima", nullable = false)
    private MateriaPrima materiaPrima;

    @Column(name = "cantidad_kg", nullable = false)
    private Double cantidadKg;

    @Column(name = "porcentaje_formula", nullable = false)
    private Double porcentajeFormula;

    @Column(name = "precio_unitario_momento_creacion", nullable = false)
    private Double precioUnitarioMomentoCreacion;

    @Column(name = "costo_parcial", nullable = false)
    private Double costoParcial;
}