package com.reforma.domain.fabricaciones.entity;

import com.reforma.domain.fabricaciones.domain.EstadoFabricacion;
import com.reforma.domain.formulas.entity.FormulaCabecera;
import com.reforma.domain.granjas.entity.Granja;
import com.reforma.domain.usuarios.entity.Usuario;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "t_fabricacion")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Fabricacion {

    @Id
    @Column(length = 32)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_granja", nullable = false)
    private Granja granja;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_usuario", nullable = false)
    private Usuario usuario;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_formula")
    private FormulaCabecera formula;

    @Column(name = "codigo_fabricacion", length = 50)
    private String codigoFabricacion;

    @Column(name = "descripcion_fabricacion", length = 200)
    private String descripcionFabricacion;

    @Column(name = "cantidad_fabricacion", nullable = false)
    private Double cantidadFabricacion;

    @Column(name = "costo_total_fabricacion", nullable = false)
    private Double costoTotalFabricacion;

    @Column(name = "costo_por_kilo", nullable = false)
    private Double costoPorKilo;

    @Column(name = "fecha_fabricacion", nullable = false)
    private Instant fechaFabricacion;

    @Column(columnDefinition = "TEXT")
    private String observaciones;

    @Column(name = "sin_existencias", nullable = false)
    private Boolean sinExistencias;

    @Column(nullable = false)
    private Boolean activo;

    @Column(name = "fecha_eliminacion")
    private Instant fechaEliminacion;

    @Column(name = "eliminado_por", length = 32)
    private String eliminadoPor;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EstadoFabricacion estado;

    @Column(name = "codigo_formula_snapshot", length = 50)
    private String codigoFormulaSnapshot;

    @Column(name = "descripcion_formula_snapshot", length = 200)
    private String descripcionFormulaSnapshot;

    @Column(name = "costo_unitario_formula_snapshot")
    private Double costoUnitarioFormulaSnapshot;

    @OneToMany(mappedBy = "fabricacion", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id ASC")
    @Builder.Default
    private List<FabricacionDetalle> detalles = new ArrayList<>();
}
