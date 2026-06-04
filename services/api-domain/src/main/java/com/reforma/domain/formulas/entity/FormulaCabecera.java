package com.reforma.domain.formulas.entity;

import com.reforma.domain.animales.entity.Animal;
import com.reforma.domain.granjas.entity.Granja;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "t_formula_cabecera")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FormulaCabecera {

    @Id
    @Column(length = 32)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_granja", nullable = false)
    private Granja granja;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_animal", nullable = false)
    private Animal animal;

    @Column(name = "codigo_formula", nullable = false, length = 50)
    private String codigoFormula;

    @Column(name = "descripcion_formula", nullable = false, length = 200)
    private String descripcionFormula;

    @Column(name = "peso_total_formula", nullable = false)
    private Double pesoTotalFormula;

    @Column(name = "costo_total_formula", nullable = false)
    private Double costoTotalFormula;

    @Column(nullable = false)
    private Boolean activa;

    @OneToMany(mappedBy = "formula", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id ASC")
    @Builder.Default
    private List<FormulaDetalle> detalles = new ArrayList<>();
}