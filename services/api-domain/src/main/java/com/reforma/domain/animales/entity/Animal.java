package com.reforma.domain.animales.entity;

import com.reforma.domain.granjas.entity.Granja;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
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

@Entity
@Table(name = "t_animal")
// id: PK BIGINT autoincremental. codigo_animal: clave de negocio/UI (única entre activos, V004).
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Animal {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_granja", nullable = false)
    private Granja granja;

    @Column(name = "codigo_animal", nullable = false, length = 50)
    private String codigoAnimal;

    @Column(name = "descripcion_animal", nullable = false, length = 200)
    private String descripcionAnimal;

    /** Categoría libre (ej. "Cerda gestante", "Lechón destete"). Heredado del esquema V001. */
    @Column(name = "categoria_animal", length = 100)
    private String categoriaAnimal;

    /** Notas operativas del usuario (RF-ANI-001). Sin límite duro, hasta TEXT. */
    @Column(name = "observaciones", columnDefinition = "TEXT")
    private String observaciones;

    @Column(nullable = false)
    private Boolean activo;

    @Column(name = "fecha_creacion", nullable = false)
    private Instant fechaCreacion;

    @Column(name = "fecha_ultima_actualizacion", nullable = false)
    private Instant fechaUltimaActualizacion;
}
